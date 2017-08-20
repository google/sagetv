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

import sage.epg.sd.SDImages;
import sage.media.format.MediaFormat;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author Narflex
 */
public class SeriesInfo extends DBObject
{
  // Bit-shifts and bit-masks for the legacy packed images
  // .3.........2.........1........0
  // 1098765432109876543210986543210
  // ttoTTTTRRRRIIIIIIIIIISS

  // SOURCES not supported in legacy image ID
  public static final int IMAGE_SOURCE_MASK = 0x3;
  public static final int IMAGE_SOURCE_SHIFT = 9; // SS
  public static final int IMAGE_SOURCE_SHOWCARDS = 0;
  public static final int IMAGE_SOURCE_TVBANNERS = 1;

  public static final int IMAGE_ID_MASK = 0x3FF;
  public static final int IMAGE_ID_SHIFT = 11; // IIII

  public static final int IMAGE_RESCODE_MASK = 0xF;
  public static final int IMAGE_RESCODE_SHIFT = 21; // RRRR
  public static final int IMAGE_THUMB_RESCODE_SHIFT = 25; // TTTT

  public static final int IMAGE_ORIENTATION_SHIFT = 29;
  public static final int IMAGE_ORIENTATION_MASK = 0x1; // o

  public static final int IMAGE_TYPE_SHIFT = 30;
  public static final int IMAGE_TYPE_MASK = 0x3; // tt

  public static final int IMAGE_TYPE_CAST_IN_CHARACTER = 3;
  public static final int IMAGE_TYPE_CAST_ENSEMBLE = 2;
  public static final int IMAGE_TYPE_BANNER = 1;
  public static final int IMAGE_TYPE_LOGO = 0;

  private static final String[] IMAGE_TYPE_STRINGS = new String[] {"l", "b", "ce", "cc"};

  // Bit-shifts and bit-masks for the packed cast-as-character images
  public static final int IMAGE_CC_THUMB_RESCODE_SHIFT = 60;
  public static final int IMAGE_CC_RESCODE_SHIFT = 56;
  public static final int IMAGE_CC_ID_SHIFT = 46;
  public static final int IMAGE_CC_CHARACTER_MASK = 0xFFFFFF;
  public static final int IMAGE_CC_CHARACTER_ID_SHIFT = 22;

  SeriesInfo(int inID)
  {
    super(inID);
    people = Pooler.EMPTY_PERSON_ARRAY;
    characters = Pooler.EMPTY_STRING_ARRAY;
    setMediaMask(MEDIA_MASK_TV);
  }

  SeriesInfo(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    legacySeriesID = in.readInt();
    title = Wizard.getInstance().getTitleForID(readID(in, idMap));
    network = Wizard.getInstance().getNetworkForID(readID(in, idMap));
    description = in.readUTF();
    history = in.readUTF();
    premiereDate = in.readUTF();
    finaleDate = in.readUTF();
    airDow = in.readUTF();
    airHrMin = in.readUTF();
    int numPeeps = in.readInt();
    if (numPeeps > Wizard.STUPID_SIZE)
      throw new IOException("Stupid array size:" + numPeeps);
    people = numPeeps == 0 ? Pooler.EMPTY_PERSON_ARRAY : new Person[numPeeps];
    characters = numPeeps == 0 ? Pooler.EMPTY_STRING_ARRAY : new String[numPeeps];
    for (int i = 0; i < numPeeps; i++)
    {
      people[i] = Wizard.getInstance().getPersonForID(readID(in, idMap));
      characters[i] = in.readUTF();
    }
    if (ver < 0x4F || ver >= 0x54)
      imageUrl = in.readUTF();
    setMediaMask(MEDIA_MASK_TV);
    if (ver > 0x46)
    {
      buildProps(in.readUTF());
    }
    if (ver > 0x4E)
    {
      showcardID = in.readInt();
      int numSerImages = in.readShort();
      seriesImages = (numSerImages == 0) ? Pooler.EMPTY_INT_ARRAY : new int[numSerImages];
      for (int i = 0; i < numSerImages; i++)
        seriesImages[i] = in.readInt();
      int numCastImages = in.readShort();
      castImages = (numCastImages == 0) ? Pooler.EMPTY_LONG_ARRAY : new long[numCastImages];
      for (int i = 0; i < numCastImages; i++)
        castImages[i] = in.readLong();
    }
    else
    {
      seriesImages = Pooler.EMPTY_INT_ARRAY;
      castImages = Pooler.EMPTY_LONG_ARRAY;
    }

    if (ver >= 0x56)
    {
      int numURLs = in.readShort();
      if (numURLs > Wizard.STUPID_SIZE)
        throw new IOException("Stupid array size:" + numURLs);
      imageURLs = (numURLs == 0) ? Pooler.EMPTY_2D_BYTE_ARRAY : new byte[numURLs][];
      for (int i = 0; i < numURLs; i++)
      {
        int numURLLength = in.readShort();
        if (numURLLength > Wizard.STUPID_SIZE)
          throw new IOException("Stupid array size:" + numURLLength);
        imageURLs[i] = new byte[numURLLength];
        in.readFully(imageURLs[i], 0, numURLLength);
      }
    }
    else if (imageURLs == null)
      imageURLs = Pooler.EMPTY_2D_BYTE_ARRAY;
  }

  private void buildProps(String str)
  {
    if (str != null && str.length() > 0)
    {
      if (props == null)
        props = new Properties();
      else
        props.clear();
      int currNameStart = 0;
      int currValueStart = -1;
      for (int i = 0; i < str.length(); i++)
      {
        char c = str.charAt(i);
        if (c == '\\')
        {
          // Escaped character, so skip the next one
          i++;
          continue;
        }
        else if (c == '=')
        {
          // We found the name=value delimeter, set the value start position
          currValueStart = i + 1;
        }
        else if (c == ';' && currValueStart != -1)
        {
          // We're at the end of the name value pair, get their values!
          String name = sage.media.format.ContainerFormat.unescapeString(str.substring(currNameStart, currValueStart - 1));
          String value = sage.media.format.ContainerFormat.unescapeString(str.substring(currValueStart, i));
          currNameStart = i + 1;
          currValueStart = -1;
          props.setProperty(name, value);
        }
      }
    }
    else if (props != null)
      props.clear();
  }

  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    boolean useLookupIdx = (flags & Wizard.WRITE_OPT_USE_ARRAY_INDICES) != 0;
    out.writeInt(legacySeriesID);
    out.writeInt((title == null) ? 0 : (useLookupIdx ? title.lookupIdx : title.id));
    out.writeInt((network == null) ? 0 : (useLookupIdx ? network.lookupIdx : network.id));
    out.writeUTF(description);
    out.writeUTF(history);
    out.writeUTF(premiereDate);
    out.writeUTF(finaleDate);
    out.writeUTF(airDow);
    out.writeUTF(airHrMin);
    out.writeInt(people.length);
    for (int i = 0; i < people.length; i++)
    {
      out.writeInt(useLookupIdx ? people[i].lookupIdx : people[i].id);
      out.writeUTF(characters[i]);
    }
    out.writeUTF(imageUrl);

    if (props == null)
      out.writeUTF("");
    else
    {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<Object, Object> ent : props.entrySet())
      {
        sb.append(MediaFormat.escapeString(ent.getKey().toString()));
        sb.append('=');
        sb.append(MediaFormat.escapeString(ent.getValue().toString()));
        sb.append(';');
      }
      out.writeUTF(sb.toString());
    }

    out.writeInt(showcardID);
    out.writeShort(seriesImages.length);
    for (int i = 0; i < seriesImages.length; i++)
      out.writeInt(seriesImages[i]);
    out.writeShort(castImages.length);
    for (int i = 0; i < castImages.length; i++)
      out.writeLong(castImages[i]);
    out.writeShort(imageURLs.length);
    for (int i = 0; i < imageURLs.length; i++)
    {
      out.writeShort(imageURLs[i].length);
      out.write(imageURLs[i]);
    }
  }

  @Override
  boolean validate()
  {
    for (int i = 0; i < people.length; i++)
      if (people[i] == null) return false;
    if (title == null) return false;
    return true;
  }

  @Override
  void update(DBObject fromMe)
  {
    SeriesInfo a = (SeriesInfo) fromMe;
    legacySeriesID = a.legacySeriesID;
    showcardID = a.showcardID;
    title = a.title;
    network = a.network;
    description = a.description;
    history = a.history;
    premiereDate = a.premiereDate;
    finaleDate = a.finaleDate;
    airDow = a.airDow;
    airHrMin = a.airHrMin;
    people = a.people;
    characters = a.characters;
    if (a.props != null)
      props = (Properties) a.props.clone();
    else
      props = null;
    imageUrl = a.imageUrl;
    seriesImages = a.seriesImages;
    castImages = a.castImages;
    imageURLs = a.imageURLs;
    super.update(fromMe);
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder("SeriesInfo[");
    sb.append(id);
    sb.append(',');
    sb.append(getTitle());
    sb.append(']');
    return sb.toString();
  }

  public String getTitle()
  {
    return (title == null) ? "" : title.name;
  }

  public String getDescription()
  {
    return description;
  }

  public boolean hasImage() { return seriesImages.length > 0 || imageUrl.length() > 0 || imageURLs.length > 1; }

  public String getImageURL(boolean thumb)
  {
    if (imageURLs.length > 1) return SDImages.getSeriesImageUrl(showcardID, imageURLs, 0, thumb);
    return (seriesImages.length > 0) ? getImageAsUrl(showcardID, seriesImages[0], thumb) : imageUrl;
  }

  public int getImageCount()
  {
    if (imageURLs.length > 1) return imageURLs.length - 1;
    return (seriesImages.length > 0) ? seriesImages.length : ((imageUrl.length() > 0) ? 1 : 0);
  }

  public String getImageURL(int index, boolean thumb)
  {
    if (imageURLs.length > 1) {
      return SDImages.getSeriesImageUrl(showcardID, imageURLs, index, thumb);
    } else if (index < 0 || index >= seriesImages.length) {
      return imageUrl;
    } else {
      return getImageAsUrl(showcardID, seriesImages[index], thumb);
    }
  }

  public boolean hasActorInCharacterImage(Person p)
  {
    if (castImages == null || castImages.length == 0 || p == null) return false;
    for (int i = 0; i < people.length; i++)
    {
      if (Math.abs(people[i].extID) == Math.abs(p.extID))
      {
        return castImages[i] != 0;
      }
    }
    return false;
  }

  public String getActorInCharacterImageURL(Person p, boolean thumb)
  {
    if (castImages == null || castImages.length == 0 || p == null) return "";
    for (int i = 0; i < people.length; i++)
    {
      if (Math.abs(people[i].extID) == Math.abs(p.extID))
      {
        return getCastCharacterImageAsUrl(showcardID, castImages[i], thumb);
      }
    }
    return "";
  }

  public String getNetwork()
  {
    return (network == null) ? "" : network.name;
  }

  public String getHistory()
  {
    return history;
  }

  public String getPremiereDate() { return premiereDate; }
  public String getFinaleDate() { return finaleDate; }
  public String getAirDow() { return airDow; }
  public String getAirHrMin() { return airHrMin; }

  public int getNumberOfCharacters() { return people.length; }
  public String getPerson(int idx)
  {
    return (idx < 0 || idx >= people.length) ? "" : people[idx].name;
  }

  public Person getPersonObj(int idx)
  {
    return (idx < 0 || idx >= people.length) ? null : people[idx];
  }

  public String getCharacter(int idx)
  {
    return (idx < 0 || idx >= characters.length) ? "" : characters[idx];
  }

  public String guessCharacterForActor(String actor)
  {
    for (int i = 0; i < people.length; i++)
    {
      if (people[i].name.equalsIgnoreCase(actor))
        return characters[i];
    }
    return "";
  }

  public String getCharacterForActor(Person actor)
  {
    if (actor == null) return "";
    if (actor.extID == 0) return guessCharacterForActor(actor.name);
    for (int i = 0; i < people.length; i++)
    {
      // Resolve aliases as well so they match
      if (Math.abs(people[i].extID) == Math.abs(actor.extID))
        return characters[i];
    }
    return "";
  }

  public String[] getPersonList()
  {
    String[] rv = new String[people.length];
    for (int i = 0; i < rv.length; i++)
    {
      rv[i] = people[i].name;
    }
    return rv;
  }

  public Person[] getPersonObjList()
  {
    return (people.length == 0) ? people : (Person[]) people.clone();
  }

  public String[] getCharacterList()
  {
    return characters.clone();
  }

  private void determineCategories()
  {
    String seriesIDStr = Integer.toString(legacySeriesID);
    while (seriesIDStr.length() < 6)
      seriesIDStr = "0" + seriesIDStr;
    if (seriesIDStr.length() == 7)
      seriesIDStr = "0" + seriesIDStr;
    for (int x = 0; x < 4; x++)
    {
      Show[] theShows = Wizard.getInstance().getShowsForExternalIDPrefix((x == 0 ? "EP" : "SH") + seriesIDStr);
      for (int i = 0; i < theShows.length; i++)
      {
        if (theShows[i].getCategory().length() > 0)
        {
          category = theShows[i].getCategory();
          subCategory = theShows[i].getSubCategory();
          return;
        }
      }
      if (x == 1)
      {
        if (seriesIDStr.length() == 6)
          seriesIDStr = "00" + seriesIDStr;
        else
          break;
      }
    }
    category = subCategory = "";
  }

  public String getCategory()
  {
    if (category == null)
      determineCategories();
    return category;
  }

  public String getSubCategory()
  {
    if (category == null)
      determineCategories();
    return subCategory;
  }

  public int getSeriesID() { return legacySeriesID; }
  public int getShowcardID() { return showcardID; }

  public String getProperty(String name)
  {
    if (props == null)
      return "";
    String rv = props.getProperty(name);
    return (rv == null) ? "" : rv;
  }

  public synchronized void setProperty(String name, String value)
  {
    if (value == null && (props == null || !props.containsKey(name)))
      return;
    if (value != null && props != null && value.equals(props.getProperty(name)))
      return;
    if (value == null)
    {
      props.remove(name);
    }
    else
    {
      if (props == null)
        props = new Properties();
      props.setProperty(name, value);
    }
    Wizard.getInstance().logUpdate(this, Wizard.SERIESINFO_CODE);
  }

  public boolean isSeriesInfoIdentical(SeriesInfo testMe)
  {
    if (showcardID == testMe.showcardID &&
        legacySeriesID == testMe.legacySeriesID &&
        title == testMe.title &&
        network == testMe.network &&
        description.equals(testMe.description) &&
        history.equals(testMe.history) &&
        premiereDate.equals(testMe.premiereDate) &&
        finaleDate.equals(testMe.finaleDate) &&
        airDow.equals(testMe.airDow) &&
        airHrMin.equals(testMe.airHrMin) &&
        people.length == testMe.people.length &&
        characters.length == testMe.characters.length &&
        seriesImages.length == testMe.seriesImages.length &&
        castImages.length == testMe.castImages.length &&
        imageUrl.equals(testMe.imageUrl) &&
        imageURLs.length == testMe.imageURLs.length)
    {
      for (int i = 0; i < people.length; i++)
        if (people[i] != testMe.people[i])
          return false;
      for (int i = 0; i < characters.length; i++)
        if (!characters[i].equals(testMe.characters[i]))
          return false;
      for (int i = 0; i < seriesImages.length; i++)
        if (seriesImages[i] != testMe.seriesImages[i])
          return false;
      for (int i = 0; i < castImages.length; i++)
        if (castImages[i] != testMe.castImages[i])
          return false;
      for (int i = 0; i < imageURLs.length; i++)
      {
        if (imageURLs[i].length != testMe.imageURLs[i].length)
          return false;
        for (int j = 0; j < imageURLs[i].length; j++)
          if (imageURLs[i][j] != testMe.imageURLs[i][j])
            return false;
      }

      return true;
    }
    else
      return false;
  }

  /**
   * Given a packed image as an int, returns the URL to either the full sized image, or the
   * thumbnail depending on the thumb parameter
   *
   */
  private static String getImageAsUrl(int showCardId, int packedImage, boolean thumb) {

    if (packedImage == 0) {
      return null;
    }

    int source = ((packedImage >> IMAGE_SOURCE_SHIFT) & IMAGE_SOURCE_MASK);

    int type = ((packedImage >> IMAGE_TYPE_SHIFT) & IMAGE_TYPE_MASK);
    boolean isVertical = ((packedImage >> IMAGE_ORIENTATION_SHIFT) & 0x1) == IMAGE_ORIENTATION_MASK;
    int thumbResolutionCode = ((packedImage >> IMAGE_THUMB_RESCODE_SHIFT) & IMAGE_RESCODE_MASK);
    int resolutionCode = ((packedImage >> IMAGE_RESCODE_SHIFT) & IMAGE_RESCODE_MASK);
    int imageId = ((packedImage >> IMAGE_ID_SHIFT) & IMAGE_ID_MASK);

    if (source == IMAGE_SOURCE_SHOWCARDS) {
      return "http://www.gstatic.com/tv/thumb/showcards/" + showCardId + "/p" + showCardId + "_"
          + IMAGE_TYPE_STRINGS[type] + "_" + (isVertical ? "v" : "h")
          + (thumb ? thumbResolutionCode : resolutionCode) + "_" + ((char) ((imageId / 26) + 'a'))
          + "" + ((char) ((imageId % 26) + 'a')) + ".jpg";
    } else if (source == IMAGE_SOURCE_TVBANNERS) {
      return "http://www.gstatic.com/tv/thumb/tvbanners/" + showCardId + "/p" + showCardId + "_"
          + IMAGE_TYPE_STRINGS[type] + "_" + (isVertical ? "v" : "h")
          + (thumb ? thumbResolutionCode : resolutionCode) + "_" + ((char) ((imageId / 26) + 'a'))
          + "" + ((char) ((imageId % 26) + 'a')) + ".jpg";
    } else {
      return null;
    }
  }

  /**
   *  Given a packed cast-as-character image as an long, returns the URL to either the
   *  full sized image, or the thumbnail depending on the thumb parameter
   *
   */
  private static String getCastCharacterImageAsUrl(int showCardId, long packedImage, boolean thumb){

    if (packedImage == 0 ) return null;

    boolean isVertical=true;
    int thumbResolutionCode=(int)((packedImage>>IMAGE_CC_THUMB_RESCODE_SHIFT)&IMAGE_RESCODE_MASK );
    int resolutionCode=(int)((packedImage>>IMAGE_CC_RESCODE_SHIFT)&IMAGE_RESCODE_MASK );
    int imageId=(int)((packedImage>>IMAGE_CC_ID_SHIFT)&IMAGE_ID_MASK );
    int characterId=(int)((packedImage>>IMAGE_CC_CHARACTER_ID_SHIFT)&IMAGE_CC_CHARACTER_MASK );

    return "http://www.gstatic.com/tv/thumb/showcards/"
        + showCardId
        + "/p"+showCardId
        +"_n"+characterId
        + "_cc_"
        + (isVertical?"v":"h")
        + (thumb?thumbResolutionCode:resolutionCode)
        + "_"+(char)((imageId/26)+'a')+""+(char)((imageId%26)+'a')
        + ".jpg";
  }

  int showcardID;
  int legacySeriesID;
  Stringer title;
  Stringer network;
  String description = "";
  String history = "";
  String premiereDate = "";
  String finaleDate = "";
  String airDow = "";
  String airHrMin = "";
  Person[] people;
  String[] characters;
  int[] seriesImages;
  long[] castImages;
  String imageUrl = "";
  byte[][] imageURLs;

  // Calculated values
  String category;
  String subCategory;

  Properties props;

  public static final Comparator<SeriesInfo> LEGACY_SERIES_ID_COMPARATOR =
      new Comparator<SeriesInfo>()
  {
    public int compare(SeriesInfo a1, SeriesInfo a2)
    {
      if (a1 == a2)
        return 0;
      else if (a1 == null)
        return 1;
      else if (a2 == null)
        return -1;

      return (a1.legacySeriesID == a2.legacySeriesID)
          ? a1.showcardID - a2.legacySeriesID
          : a1.legacySeriesID - a2.legacySeriesID;
    }
  };
  public static final Comparator<SeriesInfo> SHOWCARD_ID_COMPARATOR =
      new Comparator<SeriesInfo>()
  {
    public int compare(SeriesInfo a1, SeriesInfo a2)
    {
      if (a1 == a2)
        return 0;
      else if (a1 == null)
        return 1;
      else if (a2 == null)
        return -1;

      return (a1.showcardID == a2.showcardID)
          ? a1.legacySeriesID - a2.legacySeriesID
          : a1.showcardID - a2.showcardID;
    }
  };
}
