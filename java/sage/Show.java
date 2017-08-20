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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class Show extends DBObject
{
  public static final byte ACTOR_ROLE = 1;
  public static final byte LEAD_ACTOR_ROLE = 2; // unused in On2 data
  public static final byte SUPPORTING_ACTOR_ROLE = 3; // unused in On2 data
  public static final byte ACTRESS_ROLE = 4; // unused in On2 data
  public static final byte LEAD_ACTRESS_ROLE = 5; // unused in On2 data
  public static final byte SUPPORTING_ACTRESS_ROLE = 6; // unused in On2 data
  public static final byte GUEST_ROLE = 7;
  public static final byte GUEST_STAR_ROLE = 8;
  public static final byte DIRECTOR_ROLE = 9;
  public static final byte PRODUCER_ROLE = 10;
  public static final byte WRITER_ROLE = 11;
  public static final byte CHOREOGRAPHER_ROLE = 12;
  public static final byte SPORTS_FIGURE_ROLE = 13; // unused in On2 data
  public static final byte COACH_ROLE = 14; // unused in On2 data
  public static final byte HOST_ROLE = 15;
  public static final byte EXECUTIVE_PRODUCER_ROLE = 16;
  public static final byte ARTIST_ROLE = 17; // for music
  public static final byte ALBUM_ARTIST_ROLE = 18; // for music
  public static final byte COMPOSER_ROLE = 19; // for music
  public static final byte JUDGE_ROLE = 20;
  public static final byte NARRATOR_ROLE = 21;
  public static final byte CONTESTANT_ROLE = 22;
  public static final byte CORRESPONDENT_ROLE = 23;
  public static final byte TEAM_ROLE = 24;
  public static final byte GUEST_VOICE_ROLE = 25;
  public static final byte ANCHOR_ROLE = 26;
  public static final byte VOICE_ROLE = 27;
  public static final byte MUSICAL_GUEST_ROLE = 28;
  public static final byte FILM_EDITOR_ROLE = 29;
  public static final byte MUSIC_ROLE = 30;
  public static final byte CASTING_ROLE = 31;
  public static final byte CINEMATOGRAPHER_ROLE = 32;
  public static final byte COSTUME_DESIGNER_ROLE = 33;
  public static final byte PRODUCTION_DESIGN_ROLE = 34;
  public static final byte CREATOR_ROLE = 35;
  public static final byte CO_PRODUCER_ROLE = 36;
  public static final byte ASSOCIATE_PRODUCER_ROLE = 37;
  public static final byte FIRST_ASSISTANT_DIRECTOR_ROLE = 38;
  public static final byte SUPERVISING_ART_DIRECTION_ROLE = 39;
  public static final byte CO_EXECUTIVE_PRODUCER_ROLE = 40;
  public static final byte DIRECTOR_OF_PHOTOGRAPHY_ROLE = 41;
  public static final byte UNIT_PRODUCTION_MANAGER_ROLE = 42;
  public static final byte MAKEUP_ARTIST_ROLE = 43;
  public static final byte ASSISTANT_DIRECTOR_ROLE = 44;
  public static final byte MUSIC_SUPERVISOR_ROLE = 45;
  public static final byte MAX_ROLE_NUM = 45;
  public static final byte ALL_ROLES = 127;

  public static String[] getRoleNames()
  {
    return new String[] {"", Sage.rez("Actor"), Sage.rez("Lead_Actor"),
        Sage.rez("Supporting_Actor"), Sage.rez("Actress"),
        Sage.rez("Lead_Actress"), Sage.rez("Supporting_Actress"),
        Sage.rez("Guest"), Sage.rez("Guest_Star"), Sage.rez("Director"), Sage.rez("Producer"),
        Sage.rez("Writer"), Sage.rez("Choreographer"), Sage.rez("Sports_Figure"),
        Sage.rez("Coach"), Sage.rez("Host"), Sage.rez("Executive_Producer"),
        Sage.rez("Artist"), Sage.rez("Album_Artist"), Sage.rez("Composer"),
        Sage.rez("Judge"), Sage.rez("Narrator"), Sage.rez("Contestant"),
        Sage.rez("Correspondent"), Sage.rez("Team"), Sage.rez("Guest_Voice"),
        Sage.rez("Anchor"), Sage.rez("Voice"), Sage.rez("Musical_Guest"),
        Sage.rez("Film_Editor"), Sage.rez("Music"), Sage.rez("Casting"),
        Sage.rez("Cinematographer"), Sage.rez("Costume_Designer"), Sage.rez("Production_Design"),
        Sage.rez("Creator"), Sage.rez("CoProducer"), Sage.rez("Associate_Producer"),
        Sage.rez("First_Assistant_Director"), Sage.rez("Supervising_Art_Direction"),
        Sage.rez("CoExecutive_Producer"), Sage.rez("Director_of_Photography"),
        Sage.rez("Unit_Production_Manager"), Sage.rez("Makeup_Artist"),
        Sage.rez("Assistant_Director"), Sage.rez("Music_Supervisor")
    };
  }

  public static final byte FORCED_UNIQUE = 2;
  public static final byte PRESUMED_UNIQUE = 1;
  public static final byte NON_UNIQUE = 0;
  public static final byte UNKNOWN_UNIQUE = -1;
  public static final String[] ROLE_NAMES = getRoleNames();
  public static final int IMAGE_PHOTO_TALL = 1;
  public static final int IMAGE_PHOTO_WIDE = 2;
  public static final int IMAGE_PHOTO_THUMB_TALL = 3;
  public static final int IMAGE_PHOTO_THUMB_WIDE = 4;
  public static final int IMAGE_POSTER_TALL = 5;
  public static final int IMAGE_POSTER_WIDE = 6;
  public static final int IMAGE_POSTER_THUMB_TALL = 7;
  public static final int IMAGE_POSTER_THUMB_WIDE = 8;
  public static final int IMAGE_BOXART_TALL = 9;
  public static final int IMAGE_BOXART_THUMB_TALL = 10;
  private static final int LEGACY_PHOTO_TALL_INDEX = 0;
  private static final int LEGACY_PHOTO_WIDE_INDEX = 1;
  private static final int LEGACY_POSTER_TALL_INDEX = 2;
  private static final int LEGACY_POSTER_WIDE_INDEX = 3;

  // for packed images
  public static final int IMAGE_TYPE_PHOTO_TALL = 0;
  public static final int IMAGE_TYPE_PHOTO_WIDE = 1;
  public static final int IMAGE_TYPE_POSTER_TALL = 2;
  public static final int IMAGE_TYPE_POSTER_WIDE = 3;
  public static final int IMAGE_TYPE_BOX_ART_TALL = 4;

  public static final int IMAGE_TYPE_BIT_OFFSET = 10;
  public static final int IMAGE_SOURCE_BIT_OFFSET = 14;
  public static final int IMAGE_TYPE_MASK = 0xF;
  public static final int IMAGE_ID_MASK = 0x3FF;
  public static final int IMAGE_SOURCE_MASK = 0x3;
  public static final int IMAGE_SOURCE_BONES_GSTATIC = 0;
  public static final int IMAGE_SOURCE_FIBER_GSTATIC = 1;

  // Used for localized check on isMovie() method.
  static String movieString = Sage.rez("Movie");

  public static String getRoleString(int x)
  {
    if (x < 0 || x >= ROLE_NAMES.length)
      return "";
    else
      return ROLE_NAMES[x];
  }

  public static int getRoleForString(String s)
  {
    for (int i = 1; i < ROLE_NAMES.length; i++)
      if (ROLE_NAMES[i].equalsIgnoreCase(s))
        return i;
    return 0;
  }

  public long getDuration() { return duration; }

  public String getTitle()
  {
    if (title != null)
      return title.name;
    else return ""; // "NO TITLE"
  }

  public String getEpisodeName()
  {
    if (episodeNameBytes != null)
    {
      // Doing it this way should be thread safe, we may create the String twice but we'd always
      // do it from a valid byte array
      byte[] testBytes = episodeNameBytes;
      if (testBytes != null)
      {
        try {
          episodeNameStr = new String(testBytes, Sage.I18N_CHARSET);
          episodeNameBytes = null;
        }
        catch (UnsupportedEncodingException uee) {
          if (Sage.DBG) System.out.println("Unicode ERROR creating String of:" + uee);
        }
      }
    }
    return episodeNameStr;
  }

  public String getDesc()
  {
    if (descBytes != null)
    {
      // Doing it this way should be thread safe, we may create the String twice but we'd always
      // do it from a valid byte array
      byte[] testBytes = descBytes;
      if (testBytes != null)
      {
        try {
          descStr = new String(testBytes, Sage.I18N_CHARSET);
          descBytes = null;
        }
        catch (UnsupportedEncodingException uee) {
          if (Sage.DBG) System.out.println("Unicode ERROR creating String of:" + uee);
        }
      }
    }
    return descStr;
  }

  public String getCategory() { return (categories.length == 0) ? "" : categories[0].name; }

  /**
   * Checks case if the provided string matches the category.
   *
   * @param compare This is a Stringer optimized lookup that requires the incoming string to already
   *               be all lowercase.
   * @return <code>true</code> if the string matches the category.
   */
  public boolean isCategory(String compare)
  {
    return categories.length != 0 && categories[0].equalsIgnoreCase(compare);
  }

  public String getSubCategory() { return (categories.length < 2) ? "" : categories[1].name; }

  public String[] getCategories()
  {
    String[] rv = new String[categories.length];
    for (int i = 0; i < categories.length; i++)
      rv[i] = categories[i].name;
    return rv;
  }

  public String getCategory(int index) { return (index < categories.length) ? categories[index].name : ""; }

  public int getNumCategories() { return categories.length; }

  public boolean hasCategory(Stringer testMe)
  {
    for (int i = 0; i < categories.length; i++)
      if (categories[i] == testMe)
        return true;
    return false;
  }

  public String getRated() { return (rated == null) ? "" : rated.name; }

  public String[] getExpandedRatings()
  {
    String[] rv = new String[ers.length];
    for (int i = 0; i < rv.length; i++)
      rv[i] = ers[i].name;
    return rv;
  }

  public boolean isSHorDTExternalID()
  {
    return (externalID != null) && (externalID.length > 2) && ((externalID[0] == 'S' && externalID[1] == 'H') ||
        (externalID[0] == 'D' && externalID[1] == 'T'));
  }

  public boolean isEPExternalID()
  {
    return (externalID != null) && (externalID.length > 2) && externalID[0] == 'E' && externalID[1] == 'P';
  }

  public boolean isDTExternalID()
  {
    return (externalID != null) && (externalID.length > 2) && externalID[0] == 'D' && externalID[1] == 'T';
  }

  public String getExternalID()
  {
    try
    {
      return new String(externalID, Sage.I18N_CHARSET);
    }
    catch(UnsupportedEncodingException e){
      return "";
    }
  }

  public void appendExpandedRatingsString(StringBuilder rv)
  {
    for (int i = 0; i < ers.length; i++)
    {
      rv.append(ers[i].name);
      if (i < ers.length - 1)
        rv.append(", ");
    }
  }

  public String getExpandedRatingsString()
  {
    StringBuilder rv = new StringBuilder();
    appendExpandedRatingsString(rv);
    return rv.toString();
  }

  public String getYear() { return (year == null) ? "" : year.name; }

  public String getParentalRating() { return (pr == null) ? "" : pr.name; }

  public String[] getBonuses()
  {
    String[] rv = new String[bonuses.length];
    for (int i = 0; i < rv.length; i++)
      rv[i] = bonuses[i].name;
    return rv;
  }

  public void appendBonusesString(StringBuilder rv)
  {
    for (int i = 0; i < bonuses.length; i++)
    {
      rv.append(bonuses[i].name);
      if (i < bonuses.length - 1)
        rv.append(", ");
    }
  }

  public String getBonusesString()
  {
    StringBuilder rv = new StringBuilder();
    appendBonusesString(rv);
    return rv.toString();
  }

  public long getOriginalAirDate() { return originalAirDate; }

  void setLastWatched(long time)
  {
    try {
      Wizard.getInstance().acquireWriteLock(Wizard.SHOW_CODE);
      lastWatched = (time <= 0) ? 0 : Math.max(time, lastWatched);
      Wizard.getInstance().logUpdate(this, Wizard.SHOW_CODE);
    } finally {
      Wizard.getInstance().releaseWriteLock(Wizard.SHOW_CODE);
    }
  }

  void setDontLike(boolean x)
  {
    if (x == dontLike) return;
    try {
      Wizard.getInstance().acquireWriteLock(Wizard.SHOW_CODE);
      dontLike = x;
      Wizard.getInstance().logUpdate(this, Wizard.SHOW_CODE);
    } finally {
      Wizard.getInstance().releaseWriteLock(Wizard.SHOW_CODE);
    }
  }

  @Override
  void update(DBObject x)
  {
    Show fromMe = (Show) x;
    duration = fromMe.duration;
    lastWatched = fromMe.lastWatched;
    dontLike = fromMe.dontLike;
    title = fromMe.title;
    episodeNameBytes = fromMe.episodeNameBytes;
    episodeNameStr = fromMe.episodeNameStr;
    externalID = fromMe.externalID;
    descBytes = fromMe.descBytes;
    descStr = fromMe.descStr;
    categories = fromMe.categories;
    people = fromMe.people;
    roles = fromMe.roles;
    rated = fromMe.rated;
    ers = fromMe.ers;
    year = fromMe.year;
    pr = fromMe.pr;
    bonuses = fromMe.bonuses;
    language = fromMe.language;
    originalAirDate = fromMe.originalAirDate;
    seasonNum = fromMe.seasonNum;
    episodeNum = fromMe.episodeNum;
    if (fromMe.cachedUnique == FORCED_UNIQUE)
      cachedUnique = FORCED_UNIQUE;
    altEpisodeNum = fromMe.altEpisodeNum;
    seriesID = fromMe.seriesID;
    showcardID = fromMe.showcardID;
    imageIDs = fromMe.imageIDs;
    imageURLs = fromMe.imageURLs;
    super.update(fromMe);
  }
  @Override

  boolean validate()
  {
    for (int i = 0; i < people.length; i++)
      if (people[i] == null) return false;
    for (int i = 0; i < ers.length; i++)
      if (ers[i] == null) return false;
    for (int i = 0; i < bonuses.length; i++)
      if (bonuses[i] == null) return false;
    for (int i = 0; i < imageURLs.length; i++)
      if (imageURLs[i] == null) return false;
    return true;
  }

  public boolean isIdentical(Show other)
  {
    if ((title == other.title) &&
        getEpisodeName().equals(other.getEpisodeName()) &&
        getDesc().equals(other.getDesc()) &&
        (categories.length == other.categories.length) &&
        (rated == other.rated) &&
        (year == other.year) &&
        (pr == other.pr) &&
        (people.length == other.people.length) &&
        (ers.length == other.ers.length) &&
        (bonuses.length == other.bonuses.length) &&
        (language == other.language) &&
        (originalAirDate == other.originalAirDate) &&
        (duration == other.duration) &&
        (getMediaMask() == other.getMediaMask()) &&
        (seasonNum == other.seasonNum) &&
        (episodeNum == other.episodeNum) &&
        ((cachedUnique == FORCED_UNIQUE) == (other.cachedUnique == FORCED_UNIQUE)) &&
        (seriesID == other.seriesID) &&
        (showcardID == other.showcardID) &&
        (altEpisodeNum == other.altEpisodeNum) &&
        (imageIDs.length == other.imageIDs.length) &&
        (imageURLs.length == other.imageURLs.length))
    {
      for (int i = 0; i < people.length; i++)
        if (people[i] != other.people[i] ||	roles[i] != other.roles[i]) return false;
      for (int i = 0; i < ers.length; i++)
        if (ers[i] != other.ers[i]) return false;
      for (int i = 0; i < bonuses.length; i++)
        if (bonuses[i] != other.bonuses[i]) return false;
      for (int i = 0; i < categories.length; i++)
        if (categories[i] != other.categories[i]) return false;
      for (int i = 0; i < imageIDs.length; i++)
        if (imageIDs[i] != other.imageIDs[i]) return false;
      for (int i = 0; i < imageURLs.length; i++)
      {
        if (imageURLs[i].length != other.imageURLs[i].length)
          return false;
        for (int j = 0; j < imageURLs[i].length; j++)
          if (imageURLs[i][j] != other.imageURLs[i][j])
            return false;
      }

      return true;
    }
    return false;
  }

  Show(int inID)
  {
    super(inID);
    externalID = Pooler.EMPTY_BYTE_ARRAY;
    episodeNameStr = "";
    descStr = "";
  }

  Show(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    Wizard wiz = Wizard.getInstance();
    duration = in.readLong();
    title = wiz.getTitleForID(readID(in, idMap));

    // We lazily create these String to speed up loading time and reduce memory overhead
    int size = in.readShort();
    if (size == 0)
      episodeNameStr = "";
    else {
      episodeNameBytes = new byte[size];
      in.readFully(episodeNameBytes);
    }
    size = in.readShort();
    if (size == 0)
      descStr = "";
    else {
      descBytes = new byte[size];
      in.readFully(descBytes);
    }

    Stringer category = wiz.getCategoryForID(readID(in, idMap));
    Stringer subCategory = wiz.getSubCategoryForID(readID(in, idMap));
    int numPeople = in.readInt();
    if (numPeople > Wizard.STUPID_SIZE)
      throw new IOException("Stupid array size:" + numPeople);
    people = numPeople == 0 ? Pooler.EMPTY_PERSON_ARRAY : new Person[numPeople];
    roles = numPeople == 0 ? Pooler.EMPTY_BYTE_ARRAY : new byte[numPeople];
    for (int i = 0; i < numPeople; i++)
    {
      people[i] = wiz.getPersonForID(readID(in, idMap));
      if (ver >= 0x1C)
      {
        roles[i] = in.readByte();
      }
    }

    rated = wiz.getRatedForID(readID(in, idMap));
    int numERs = in.readInt();
    if (numERs > Wizard.STUPID_SIZE)
      throw new IOException("Stupid array size.");
    ers = numERs == 0 ? Pooler.EMPTY_STRINGER_ARRAY : new Stringer[numERs];
    for (int i = 0; i < numERs; i++)
      ers[i] = wiz.getERForID(readID(in, idMap));

    year = wiz.getYearForID(readID(in, idMap));
    pr = wiz.getPRForID(readID(in, idMap));
    int numBonus = in.readInt();
    if (numBonus > Wizard.STUPID_SIZE)
      throw new IOException("Stupid array size.");
    bonuses = numBonus == 0 ? Pooler.EMPTY_STRINGER_ARRAY : new Stringer[numBonus];
    for (int i = 0; i < numBonus; i++)
      bonuses[i] = wiz.getBonusForID(readID(in, idMap));

    // The Show Don't Like & last Watched are both encoded into this field
    long lastWatchedData = in.readLong();
    if (lastWatchedData == 0) {
      lastWatched = 0;
      dontLike = false;
    } else if (lastWatchedData == -1) {
      lastWatched = 0;
      dontLike = true;
    } else if (lastWatchedData < -1) {
      lastWatched = -1 * lastWatchedData;
      dontLike = true;
    } else {
      lastWatched = lastWatchedData;
      dontLike = false;
    }
    if (ver < 0x2A)
    {
      Stringer primeTitle = wiz.getPrimeTitleForID(readID(in, idMap));
      if (primeTitle != null)
      {
        title = wiz.getTitleForName(primeTitle.name);
      }
    }
    else
      /*forcedFirstRun =*/ in.readBoolean();
    size = in.readShort();
    externalID = new byte[size];
    if (size > 0)
      in.readFully(externalID);
    /*updateCount =*/ in.readInt();
    language = wiz.getBonusForID(readID(in, idMap));
    originalAirDate = in.readLong();
    if (ver >= 0x4A)
    {
      seasonNum = in.readShort();
      episodeNum = in.readShort();
      int extraCats = in.readShort();
      int baseCats = 0;
      if (category != null)
      {
        baseCats++;
        if (subCategory != null)
          baseCats++;
      }
      categories = (baseCats + extraCats == 0) ? Pooler.EMPTY_STRINGER_ARRAY : new Stringer[baseCats + extraCats];
      if (category != null)
      {
        categories[0] = category;
        if (subCategory != null)
          categories[1] = subCategory;
      }
      for (int i = 0; i < extraCats; i++)
        categories[baseCats + i] = wiz.getSubCategoryForID(readID(in, idMap));
      if (in.readBoolean())
        cachedUnique = FORCED_UNIQUE;
      if (ver < 0x4F)
      {
        byte photoCountTall = (byte)(in.readByte() & 0xFF);
        byte photoCountWide = (byte)(in.readByte() & 0xFF);
        byte photoThumbCountTall = (byte)(in.readByte() & 0xFF);
        byte photoThumbCountWide = (byte)(in.readByte() & 0xFF);
        byte posterCountTall = (byte)(in.readByte() & 0xFF);
        byte posterCountWide = (byte)(in.readByte() & 0xFF);
        byte posterThumbCountTall = (byte)(in.readByte() & 0xFF);
        byte posterThumbCountWide = (byte)(in.readByte() & 0xFF);
        imageIDs = convertLegacyShowImageData(photoCountTall, photoCountWide, posterCountTall, posterCountWide);
      }
    }
    else
    {
      int catCount = 0;
      if (category != null)
      {
        catCount++;
        if (subCategory != null)
          catCount++;
      }
      categories = (catCount == 0) ? Pooler.EMPTY_STRINGER_ARRAY : new Stringer[catCount];
      if (category != null)
      {
        categories[0] = category;
        if (subCategory != null)
          categories[1] = subCategory;
      }
    }

    if (ver >= 0x4F)
    {
      altEpisodeNum = in.readShort();
      showcardID = in.readInt();
      seriesID = in.readInt();
      int numImages = in.readShort();
      imageIDs = (numImages == 0) ? Pooler.EMPTY_SHORT_ARRAY : new short[numImages];
      for (int i = 0; i < numImages; i++)
        imageIDs[i] = in.readShort();
    }
    else if (imageIDs == null)
      imageIDs = Pooler.EMPTY_SHORT_ARRAY;

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

    // Fix anything wrong with the category arrays due to legacy issues
    int numValidCats = 0;
    for (int i = 0; i < categories.length; i++)
      if (categories[i] != null)
        numValidCats++;
    if (numValidCats != categories.length)
    {
      Stringer[] newCats = numValidCats == 0 ? Pooler.EMPTY_STRINGER_ARRAY : new Stringer[numValidCats];
      numValidCats = 0;
      for (int i = 0; i < categories.length; i++)
        if (categories[i] != null)
          newCats[numValidCats++] = categories[i];
      categories = newCats;
    }
  }

  @Override
  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    boolean useLookupIdx = (flags & Wizard.WRITE_OPT_USE_ARRAY_INDICES) != 0;
    out.writeLong(duration);
    out.writeInt((title == null) ? 0 : (useLookupIdx ? title.lookupIdx : title.id));
    byte[] barr = episodeNameBytes;
    if (barr != null) {
      out.writeShort(barr.length);
      out.write(barr);
    }
    else
      out.writeUTF(episodeNameStr);
    barr = descBytes;
    if (barr != null) {
      out.writeShort(barr.length);
      out.write(barr);
    }
    else
      out.writeUTF(descStr);
    out.writeInt((categories.length == 0) ? 0 : (useLookupIdx ? categories[0].lookupIdx : categories[0].id));
    out.writeInt((categories.length < 2) ? 0 : (useLookupIdx ? categories[1].lookupIdx : categories[1].id));

    out.writeInt(people.length);
    for (int i = 0; i < people.length; i++)
    {
      out.writeInt(useLookupIdx ? people[i].lookupIdx : people[i].id);
      out.writeByte(roles[i]);
    }

    out.writeInt((rated == null) ? 0 : (useLookupIdx ? rated.lookupIdx : rated.id));
    out.writeInt(ers.length);
    for (int i = 0; i < ers.length; i++)
      out.writeInt(useLookupIdx ? ers[i].lookupIdx : ers[i].id);

    out.writeInt((year == null) ? 0 : (useLookupIdx ? year.lookupIdx : year.id));
    out.writeInt((pr == null) ? 0 : (useLookupIdx ? pr.lookupIdx : pr.id));

    out.writeInt(bonuses.length);
    for (int i = 0; i < bonuses.length; i++)
      out.writeInt(useLookupIdx ? bonuses[i].lookupIdx : bonuses[i].id);

    long lastWatchedData = lastWatched;
    if (dontLike) {
      if (lastWatchedData == 0)
        lastWatchedData = -1;
      else
        lastWatchedData *= -1;
    }
    out.writeLong(lastWatchedData);
    out.writeBoolean(/*forcedFirstRun*/false); // old first run data
    out.writeShort(externalID.length);
    if (externalID.length > 0)
      out.write(externalID);
    out.writeInt(/*updateCount*/id);
    out.writeInt((language == null) ? 0 : (useLookupIdx ? language.lookupIdx : language.id));
    out.writeLong(originalAirDate);
    out.writeShort(seasonNum);
    out.writeShort(episodeNum);
    out.writeShort(Math.max(0, categories.length - 2));
    for (int i = 2; i < categories.length; i++)
      out.writeInt(useLookupIdx ? categories[i].lookupIdx : categories[i].id);
    out.writeBoolean(cachedUnique == FORCED_UNIQUE);
    out.writeShort(altEpisodeNum);
    out.writeInt(showcardID);
    out.writeInt(seriesID);
    out.writeShort(imageIDs.length);
    for (int i = 0; i < imageIDs.length; i++)
      out.writeShort(imageIDs[i]);
    out.writeShort(imageURLs.length);
    for (int i = 0; i < imageURLs.length; i++)
    {
      out.writeShort(imageURLs[i].length);
      out.write(imageURLs[i]);
    }
  }

  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("Show[Title=");
    sb.append(getTitle());
    String s = getEpisodeName();
    if (s.length() > 0) sb.append(", Episode=" + s);
    s = getDesc();
    if (s.length() > 0) sb.append(", Desc=" + s);
    if (categories.length > 0) sb.append(", Cat=" + getCategory());
    if (categories.length > 1) sb.append(", SubCat=" + getSubCategory());
    if (people.length > 0)
    {
      sb.append(", People=[");
      for (int i = 0; i < people.length; i++)
        sb.append(people[i].name + ", ");
      sb.append(']');
    }
    if (rated != null) sb.append(", Rated=" + getRated());
    if (ers.length > 0)
    {
      sb.append(", ERs=[");
      for (int i = 0; i < ers.length; i++)
        sb.append(ers[i].name + ", ");
      sb.append(']');
    }
    if (year != null) sb.append(", Year=" + getYear());
    if (pr != null) sb.append(", PR=" + getParentalRating());
    if (bonuses.length > 0)
    {
      sb.append(", Bonus=[");
      for (int i = 0; i < bonuses.length; i++)
        sb.append(bonuses[i].id + ", ");
      sb.append(']');
    }
    sb.append(", id=");
    sb.append(id);
    if (isWatched())
    {
      sb.append(", lastWatched=");
      sb.append(Sage.df(Math.abs(lastWatched)));
    }
    if (isDontLike()) {
      sb.append(", DontLike");
    }
    sb.append(", extID=" + getExternalID());
    sb.append(']');
    return sb.toString();
  }

  public String getPeopleString(int role, String separator)
  {
    StringBuilder rv = new StringBuilder();
    for (int i = 0; i < people.length; i++)
    {
      if (roles[i] == role || role == ALL_ROLES)
      {
        if (rv.length() > 0)
          rv.append(separator);
        rv.append(people[i].name);
      }
    }
    return rv.toString();
  }

  public String getPeopleString(int role)
  {
    return getPeopleString(role, ", ");
  }

  public String getPersonInRole(byte b)
  {
    for (int i = 0; i < people.length; i++)
      if (roles[i] == b || b == ALL_ROLES)
        return people[i].name;
    return "";
  }

  public Person getPersonObjInRole(byte b)
  {
    for (int i = 0; i < people.length; i++)
      if (roles[i] == b || b == ALL_ROLES)
        return people[i];
    return null;
  }

  public byte[] getRoles()
  {
    return roles;
  }

  // Used for fast lookups of people after analyzing the getRoles data
  public String getPerson(int idx)
  {
    return people[idx].name;
  }

  public Person getPersonObj(int idx)
  {
    return (idx < 0 || idx >= people.length) ? null : people[idx];
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

  public String getLanguage() { return (language == null) ? "" : language.name; }

  void addMediaMaskRecursive(int mediaMaskToAdd)
  {
    Wizard wiz = Wizard.getInstance();
    if (!hasMediaMask(mediaMaskToAdd))
    {
      addMediaMask(mediaMaskToAdd);
      wiz.logUpdate(this, Wizard.SHOW_CODE);
    }
    if (title != null && !title.hasMediaMask(mediaMaskToAdd))
    {
      title.addMediaMask(mediaMaskToAdd);
      wiz.logUpdate(title, Wizard.TITLE_CODE);
    }
    for (int i = 0; people != null && i < people.length; i++)
    {
      if (people[i] != null && !people[i].hasMediaMask(mediaMaskToAdd))
      {
        people[i].addMediaMask(mediaMaskToAdd);
        wiz.logUpdate(people[i], Wizard.PEOPLE_CODE);
      }
    }
    if (categories.length > 0 && !categories[0].hasMediaMask(mediaMaskToAdd))
    {
      categories[0].addMediaMask(mediaMaskToAdd);
      wiz.logUpdate(categories[0], Wizard.CATEGORY_CODE);
    }
    if (categories.length > 1)
    {
      for (int i = 1; i < categories.length; i++)
      {
        if (categories[i] != null && !categories[i].hasMediaMask(mediaMaskToAdd))
        {
          categories[i].addMediaMask(mediaMaskToAdd);
          wiz.logUpdate(categories[i], Wizard.SUBCATEGORY_CODE);
        }
      }
    }
    if (rated != null && !rated.hasMediaMask(mediaMaskToAdd))
    {
      rated.addMediaMask(mediaMaskToAdd);
      wiz.logUpdate(rated, Wizard.RATED_CODE);
    }
    for (int i = 0; ers != null && i < ers.length; i++)
    {
      if (ers[i] != null && !ers[i].hasMediaMask(mediaMaskToAdd))
      {
        ers[i].addMediaMask(mediaMaskToAdd);
        wiz.logUpdate(ers[i], Wizard.ER_CODE);
      }
    }
    if (year != null && !year.hasMediaMask(mediaMaskToAdd))
    {
      year.addMediaMask(mediaMaskToAdd);
      wiz.logUpdate(year, Wizard.YEAR_CODE);
    }
    if (pr != null && !pr.hasMediaMask(mediaMaskToAdd))
    {
      pr.addMediaMask(mediaMaskToAdd);
      wiz.logUpdate(pr, Wizard.PR_CODE);
    }
    for (int i = 0; bonuses != null && i < bonuses.length; i++)
    {
      if (bonuses[i] != null && !bonuses[i].hasMediaMask(mediaMaskToAdd))
      {
        bonuses[i].addMediaMask(mediaMaskToAdd);
        wiz.logUpdate(bonuses[i], Wizard.BONUS_CODE);
      }
    }
    if (language != null && !language.hasMediaMask(mediaMaskToAdd))
    {
      language.addMediaMask(mediaMaskToAdd);
      wiz.logUpdate(language, Wizard.BONUS_CODE);
    }
  }

  public String[] getPeopleList(int role)
  {
    List<String> rv = new ArrayList<String>();
    for (int i = 0; i < people.length; i++)
    {
      if (roles[i] == role || role == ALL_ROLES)
      {
        rv.add(people[i].name);
      }
    }
    return rv.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public String[] getPeopleList(int[] targetRoles)
  {
    if (targetRoles.length == 1)
      return getPeopleList(targetRoles[0]);
    List<String> rv = new ArrayList<String>();
    for (int i = 0; i < people.length; i++)
    {
      for (int j = 0; j < targetRoles.length; j++)
      {
        if (roles[i] == targetRoles[j])
        {
          rv.add(people[i].name);
          break;
        }
      }
    }
    return rv.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public Person[] getPeopleObjList(int role)
  {
    List<Person> rv = new ArrayList<Person>();
    for (int i = 0; i < people.length; i++)
    {
      if (roles[i] == role || role == ALL_ROLES)
      {
        rv.add(people[i]);
      }
    }
    return rv.toArray(Pooler.EMPTY_PERSON_ARRAY);
  }

  public Person[] getPeopleObjList(int[] targetRoles)
  {
    if (targetRoles.length == 1)
      return getPeopleObjList(targetRoles[0]);
    List<Person> rv = new ArrayList<Person>();
    for (int i = 0; i < people.length; i++)
    {
      for (int j = 0; j < targetRoles.length; j++)
      {
        if (roles[i] == targetRoles[j])
        {
          rv.add(people[i]);
          break;
        }
      }
    }
    return rv.toArray(Pooler.EMPTY_PERSON_ARRAY);
  }

  // This is in the bonus data; it'll either be the first thing or it'll be after the star rating
  public String getStudio()
  {
    if (externalID.length < 8 || externalID[0] != 'M' || externalID[1] != 'V' || bonuses.length == 0) return "";
    for (int i = 0; i < bonuses.length - 1; i++)
    {
      if (bonuses[i].name.startsWith("*"))
        return bonuses[i + 1].name;
    }
    return bonuses[0].name;
  }

  public String getRating()
  {
    if (externalID.length < 8 || externalID[0] != 'M' || externalID[1] != 'V' || bonuses.length == 0) return "";
    for (int i = 0; i < bonuses.length; i++)
      if (bonuses[i].name.startsWith("*"))
        return bonuses[i].name;
    return "";
  }

  public int getSeriesID() { return seriesID; }
  public int getShowcardID() { return showcardID; }

  public SeriesInfo getSeriesInfo()
  {
		if (externalID.length < 2)
			return null;
    // Add the 'MI' & 'IE' prefix for plugins to be able to link series info to imported media items
    if ((externalID[0] != 'E' || externalID[1] != 'P') && (externalID[0] != 'S' || externalID[1] != 'H') && (externalID[0] != 'M' || externalID[1] != 'I') &&
        (externalID[0] != 'I' || externalID[1] != 'E'))
      return null;
    if (showcardID != 0)
    {
      SeriesInfo rv = Wizard.getInstance().getSeriesInfoForShowcardID(showcardID);
      if (rv != null)
        return rv;
    }
    if (seriesID != 0)
    {
      SeriesInfo rv = Wizard.getInstance().getSeriesInfoForShowcardID(seriesID);
      if (rv != null)
        return rv;
    }
    if (showcardID != 0 || seriesID != 0)
      return null;
    // Legacy Show object handling...
    if (externalID.length < 8)
      return null;
    int legacySeriesID;
    try
    {
      legacySeriesID = Integer.parseInt(new String(externalID, 2, externalID.length - 6, Sage.BYTE_CHARSET));
    }
    catch (Exception e)
    {
      return null;
    }
    return Wizard.getInstance().getSeriesInfoForLegacySeriesID(legacySeriesID);
  }

  boolean hasEpisodeName()
  {
    return getEpisodeName().length() > 0;
  }

  public String[] getPeopleCharacterList(int role)
  {
    SeriesInfo si = getSeriesInfo();
    List<String> rv = new ArrayList<String>();
    for (int i = 0; i < people.length; i++)
    {
      if (roles[i] == role || role == ALL_ROLES)
      {
        String currName = people[i].name;
        if (si != null)
        {
          String currChar = si.getCharacterForActor(people[i]);
          if (currChar.length() > 0)
            currName += " " + Sage.rez("as") + " " + currChar;

        }
        rv.add(currName);
      }
    }
    return rv.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public String getPeopleCharacterString(int role)
  {
    SeriesInfo si = getSeriesInfo();
    StringBuilder rv = new StringBuilder();
    String asStr = " " + Sage.rez("as") + " ";
    for (int i = 0; i < people.length; i++)
    {
      if (roles[i] == role || role == ALL_ROLES)
      {
        if (rv.length() > 0)
          rv.append(", ");
        rv.append(people[i].name);
        if (si != null)
        {
          String currChar = si.getCharacterForActor(people[i]);
          if (currChar.length() > 0)
          {
            rv.append(asStr);
            rv.append(currChar);
          }
        }
      }
    }
    return rv.toString();
  }

  public int getSeasonNumber()
  {
    return seasonNum;
  }

  public int getEpisodeNumber()
  {
    return episodeNum;
  }

  public int getAltEpisodeNumber()
  {
    return altEpisodeNum;
  }

  public boolean isForcedUnique() { return cachedUnique == FORCED_UNIQUE; }

  private int getImageMaskForImageType(int imageType)
  {
    switch (imageType)
    {
      case IMAGE_PHOTO_THUMB_TALL:
      case IMAGE_PHOTO_TALL:
        return IMAGE_TYPE_PHOTO_TALL;
      case IMAGE_PHOTO_WIDE:
      case IMAGE_PHOTO_THUMB_WIDE:
        return IMAGE_TYPE_PHOTO_WIDE;
      case IMAGE_POSTER_TALL:
      case IMAGE_POSTER_THUMB_TALL:
        return IMAGE_TYPE_POSTER_TALL;
      case IMAGE_POSTER_WIDE:
      case IMAGE_POSTER_THUMB_WIDE:
        return IMAGE_TYPE_POSTER_WIDE;
      case IMAGE_BOXART_TALL:
      case IMAGE_BOXART_THUMB_TALL:
        return IMAGE_TYPE_BOX_ART_TALL;
      default:
        return 0;
    }
  }

  public int getImageCount()
  {
    // This first index is the image type indexes, so it doesn't count towards the total.
    if (imageURLs.length > 1)
    {
      return imageURLs.length - 1;
    }

    if (imageIDs == null || imageIDs.length == 0) return 0;
    if ((imageIDs[0] & 0x8000) != 0)
      return (imageIDs[0] & 0xFFF) + (imageIDs[1] & 0xFFF) + (imageIDs[2] & 0xFFF) + (imageIDs[3] & 0xFFF);
    else
      return imageIDs.length;
  }

  public int getImageCount(int imageType)
  {
    // The first index is the indexes of the image types.
    if (imageURLs.length > 1)
    {
      int count = SDImages.getShowImageCount(imageType, imageURLs);
      return count;
    }
    int imageMask = getImageMaskForImageType(imageType) << 12;
    int count = 0;
    for (int i = 0; i < imageIDs.length; i++)
    {
      if ((imageIDs[i] & 0x7000) == imageMask)
      {
        if ((imageIDs[i] & 0x8000) != 0)
        {
          // legacy image count
          return imageIDs[i] & 0xFFF;
        }
        else
          count++;
      }
    }
    return count;
  }

  public boolean hasAnyImages()
  {
    // The first index is the indexes of the image types.
    return imageIDs.length > 0 || imageURLs.length > 1;
  }

  public String getImageMetaStorageString()
  {
    if (imageIDs.length == 0)
      return null;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < imageIDs.length; i++)
    {
      if (i != 0)
        sb.append(',');
      sb.append(Short.toString(imageIDs[i]));
    }
    return sb.toString();
  }

  public String getImageUrl(int index, int imageType)
  {
    if (imageURLs.length > 1) return SDImages.getShowImageUrl(showcardID, imageURLs, index, imageType);

    int imageMask = getImageMaskForImageType(imageType) << 12;
    int count = 0;
    for (int i = 0; i < imageIDs.length; i++)
    {
      if ((imageIDs[i] & 0x7000) == imageMask)
      {
        if ((imageIDs[i] & 0x8000) != 0)
        {
          // legacy image count
          switch (imageType)
          {
            case IMAGE_PHOTO_TALL:
              return "http://download.sage.tv/epgimages/art/" + getExternalID() + "/PhotoTall-" + index + ".jpg";
            case IMAGE_PHOTO_WIDE:
              return "http://download.sage.tv/epgimages/art/" + getExternalID() + "/PhotoWide-" + index + ".jpg";
            case IMAGE_PHOTO_THUMB_TALL:
              return "http://download.sage.tv/epgimages/art/" + getExternalID() + "/PhotoTallThumb-" + index + ".jpg";
            case IMAGE_PHOTO_THUMB_WIDE:
              return "http://download.sage.tv/epgimages/art/" + getExternalID() + "/PhotoWideThumb-" + index + ".jpg";
            case IMAGE_POSTER_TALL:
              return "http://download.sage.tv/epgimages/art/" + getExternalID() + "/PosterTall-" + index + ".jpg";
            case IMAGE_POSTER_WIDE:
              return "http://download.sage.tv/epgimages/art/" + getExternalID() + "/PhotoWide-" + index + ".jpg";
            case IMAGE_POSTER_THUMB_TALL:
              return "http://download.sage.tv/epgimages/art/" + getExternalID() + "/PosterTallThumb-" + index + ".jpg";
            case IMAGE_POSTER_THUMB_WIDE:
              return "http://download.sage.tv/epgimages/art/" + getExternalID() + "/PosterWideThumb-" + index + ".jpg";
            default:
              return null;
          }
        }
        else
        {
          if (count == index)
          {
            return getImageIdUrl(imageIDs[i], seriesID, showcardID,  getExternalID(), (imageType == IMAGE_BOXART_THUMB_TALL ||
                imageType == IMAGE_PHOTO_THUMB_TALL || imageType == IMAGE_PHOTO_THUMB_WIDE ||
                imageType == IMAGE_POSTER_THUMB_TALL || imageType == IMAGE_POSTER_THUMB_WIDE));
          }
          count++;
        }
      }
    }
    return null;
  }

  public String getAnyImageUrl(int prefIndex)
  {
    if (imageURLs.length > 1)
    {
      // Thumbnails are even numbers and full images are odd.
      // Try to get preferred image URL.
      int i = 1;
      while (i < imageURLs[0].length)
      {
        if (SDImages.getImageCount(i, imageURLs) > prefIndex)
        {
          int realIndex = imageURLs[0][i] + prefIndex;
          return SDImages.decodeShowImageUrl(showcardID, imageURLs[realIndex]);
        }

        i += 2;
      }

      // Get any image URL.
      i = 1;
      while (i < imageURLs[0].length)
      {
        if (SDImages.getImageCount(i, imageURLs) > 0)
        {
          int realIndex = imageURLs[0][i];
          return SDImages.decodeShowImageUrl(showcardID, imageURLs[realIndex]);
        }

        i += 2;
      }

      return null;
    }

    if (imageIDs.length == 0) return null;
    if ((imageIDs[0] & 0x8000) != 0)
    {
      // legacy image data
      if ((imageIDs[LEGACY_PHOTO_TALL_INDEX] & 0xFFF) >= prefIndex)
        return getImageUrl(prefIndex, IMAGE_PHOTO_TALL);
      if ((imageIDs[LEGACY_PHOTO_WIDE_INDEX] & 0xFFF) >= prefIndex)
        return getImageUrl(prefIndex, IMAGE_PHOTO_WIDE);
      if ((imageIDs[LEGACY_POSTER_TALL_INDEX] & 0xFFF) >= prefIndex)
        return getImageUrl(prefIndex, IMAGE_POSTER_TALL);
      if ((imageIDs[LEGACY_POSTER_WIDE_INDEX] & 0xFFF) >= prefIndex)
        return getImageUrl(prefIndex, IMAGE_POSTER_WIDE);
      if ((imageIDs[LEGACY_PHOTO_TALL_INDEX] & 0xFFF) > 0)
        return getImageUrl(0, IMAGE_PHOTO_TALL);
      if ((imageIDs[LEGACY_PHOTO_WIDE_INDEX] & 0xFFF) > 0)
        return getImageUrl(0, IMAGE_PHOTO_WIDE);
      if ((imageIDs[LEGACY_POSTER_TALL_INDEX] & 0xFFF) > 0)
        return getImageUrl(0, IMAGE_POSTER_TALL);
      if ((imageIDs[LEGACY_POSTER_WIDE_INDEX] & 0xFFF) > 0)
        return getImageUrl(0, IMAGE_POSTER_WIDE);
      return null;
    }
    if (prefIndex < imageIDs.length)
      return getImageIdUrl(imageIDs[prefIndex], seriesID, showcardID, getExternalID(), false);
    else
      return getImageIdUrl(imageIDs[0], seriesID, showcardID, getExternalID(), false);
  }

  /**
   * Returns an image URL that's representative of this Movie
   * <p/>
   * If no images are available with the preferred index, any image will be returned.
   *
   * @param prefIndex The preferred image index.
   * @param thumb Request thumbnail size image.
   * @return A URL if an image was available or <code>null</code> if no images are available.
   */
  public String getAnyImageUrl(int prefIndex, boolean thumb)
  {
    if (imageURLs.length > 1)
    {
      // Thumbnails are even numbers and full images are odd. We are indexing in reverse because
      // this will get us box art, then poster and last photo which is generally preferred for
      // movies.

      // Try to get preferred image URL.
      int i = thumb ? imageURLs[0].length - 2 : imageURLs[0].length - 1;
      while (i > 0)
      {
        if (SDImages.getImageCount(i, imageURLs) > prefIndex)
        {
          int realIndex = imageURLs[0][i] + prefIndex;
          return SDImages.decodeShowImageUrl(showcardID, imageURLs[realIndex]);
        }

        i -= 2;
      }

      // Get any image URL.
      i = thumb ? imageURLs[0].length - 2 : imageURLs[0].length - 1;
      while (i > 0)
      {
        if (SDImages.getImageCount(i, imageURLs) > 0)
        {
          int realIndex = imageURLs[0][i];
          return SDImages.decodeShowImageUrl(showcardID, imageURLs[realIndex]);
        }

        i -= 2;
      }

      return null;
    }

    if (imageIDs.length == 0) return null;
    if ((imageIDs[0] & 0x8000) != 0)
    {
      // legacy image data
      if ((imageIDs[LEGACY_PHOTO_TALL_INDEX] & 0xFFF) >= prefIndex)
        return getImageUrl(prefIndex, thumb ? IMAGE_PHOTO_THUMB_TALL : IMAGE_PHOTO_TALL);
      if ((imageIDs[LEGACY_PHOTO_WIDE_INDEX] & 0xFFF) >= prefIndex)
        return getImageUrl(prefIndex, thumb ? IMAGE_PHOTO_THUMB_WIDE : IMAGE_PHOTO_WIDE);
      if ((imageIDs[LEGACY_POSTER_TALL_INDEX] & 0xFFF) >= prefIndex)
        return getImageUrl(prefIndex, thumb ? IMAGE_POSTER_THUMB_TALL : IMAGE_POSTER_TALL);
      if ((imageIDs[LEGACY_POSTER_WIDE_INDEX] & 0xFFF) >= prefIndex)
        return getImageUrl(prefIndex, thumb ? IMAGE_POSTER_THUMB_WIDE : IMAGE_POSTER_WIDE);
      if ((imageIDs[LEGACY_PHOTO_TALL_INDEX] & 0xFFF) > 0)
        return getImageUrl(0,  thumb ? IMAGE_PHOTO_THUMB_TALL : IMAGE_PHOTO_TALL);
      if ((imageIDs[LEGACY_PHOTO_WIDE_INDEX] & 0xFFF) > 0)
        return getImageUrl(0, thumb ? IMAGE_PHOTO_THUMB_WIDE : IMAGE_PHOTO_WIDE);
      if ((imageIDs[LEGACY_POSTER_TALL_INDEX] & 0xFFF) > 0)
        return getImageUrl(0, thumb ? IMAGE_POSTER_THUMB_TALL : IMAGE_POSTER_TALL);
      if ((imageIDs[LEGACY_POSTER_WIDE_INDEX] & 0xFFF) > 0)
        return getImageUrl(0,  thumb ? IMAGE_POSTER_THUMB_WIDE : IMAGE_POSTER_WIDE);
      return null;
    }
    if (prefIndex < imageIDs.length)
      return getImageIdUrl(imageIDs[prefIndex], seriesID, showcardID, getExternalID(), thumb);
    else
      return getImageIdUrl(imageIDs[0], seriesID, showcardID, getExternalID(), thumb);
  }

  public String getImageUrlForIndex(int idx, boolean thumb)
  {
    if (imageURLs.length > 1)
    {
      // Thumbnails are even numbers and full images are odd.
      int i = thumb ? 0 : 1;
      while (i < imageURLs[0].length)
      {
        int currentCount = SDImages.getImageCount(i, imageURLs);
        if (currentCount > idx)
        {
          int realIndex = imageURLs[0][i] + idx;
          return SDImages.decodeShowImageUrl(showcardID, imageURLs[realIndex]);
        }

        idx -= currentCount;
        i += 2;
      }

      return null;
    }

    if (imageIDs == null || imageIDs.length == 0)
      return null;
    if ((imageIDs[0] & 0x8000) != 0)
    {
      int currCount = imageIDs[LEGACY_PHOTO_TALL_INDEX] & 0xFFF;
      if (currCount > idx)
      {
        return getImageUrl(idx, thumb ? IMAGE_PHOTO_THUMB_TALL : IMAGE_PHOTO_TALL);
      }
      else
        idx -= currCount;
      currCount = imageIDs[LEGACY_PHOTO_WIDE_INDEX] & 0xFFF;
      if (currCount > idx)
      {
        return getImageUrl(idx, thumb ? IMAGE_PHOTO_THUMB_WIDE : IMAGE_PHOTO_WIDE);
      }
      else
        idx -= currCount;
      currCount = imageIDs[LEGACY_POSTER_TALL_INDEX] & 0xFFF;
      if (currCount > idx)
      {
        return getImageUrl(idx, thumb ? IMAGE_POSTER_THUMB_TALL : IMAGE_POSTER_TALL);
      }
      else
        idx -= currCount;
      currCount = imageIDs[LEGACY_POSTER_WIDE_INDEX] & 0xFFF;
      if (currCount > idx)
      {
        return getImageUrl(idx, thumb ? IMAGE_POSTER_THUMB_WIDE : IMAGE_POSTER_WIDE);
      }
      return null;
    }
    if (idx < imageIDs.length)
      return getImageIdUrl(imageIDs[idx], seriesID, showcardID, getExternalID(), thumb);
    else
      return null;
  }

  public String getCategoriesString(String delim)
  {
    if (delim == null)
      delim = " / ";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < categories.length; i++)
    {
      if (i != 0)
        sb.append(delim);
      sb.append(categories[i].name);
    }
    return sb.toString();
  }

  public boolean isMovie()
  {
    if (externalID.length > 1 && externalID[0] == 'M' && externalID[1] == 'V')
      return true;
    if (categories != null && categories.length > 0 && movieString.equals(categories[0].name))
      return true;
    return false;
  }

  public boolean isDontLike() {
    return dontLike;
  }

  public boolean isWatched() {
    return lastWatched > 0;
  }

  public static short[] convertLegacyShowImageData(byte photoCountTall, byte photoCountWide, byte posterCountTall, byte posterCountWide)
  {
    if (photoCountTall != 0 || photoCountWide != 0 || posterCountTall != 0 || posterCountWide != 0)
    {
      short[] rv = new short[4];
      rv[LEGACY_PHOTO_TALL_INDEX] = (short)((0x8000 | (IMAGE_TYPE_PHOTO_TALL << 12) |
          (Math.min(15, photoCountTall) & 0xFFF)) & 0xFFFF);
      rv[LEGACY_PHOTO_WIDE_INDEX] = (short)((0x8000 | (IMAGE_TYPE_PHOTO_WIDE << 12)|
          (Math.min(15, photoCountWide) & 0xFFF)) & 0xFFFF);
      rv[LEGACY_POSTER_TALL_INDEX] = (short)((0x8000 | (IMAGE_TYPE_POSTER_TALL << 12) |
          (Math.min(15, posterCountTall) & 0xFFF)) & 0xFFFF);
      rv[LEGACY_POSTER_WIDE_INDEX] = (short)((0x8000 | (IMAGE_TYPE_POSTER_WIDE << 12) |
          (Math.min(15, posterCountWide) & 0xFFF)) & 0xFFFF);
      return rv;
    }
    else
      return Pooler.EMPTY_SHORT_ARRAY;
  }

  /**
   *
   * @param packedImageId
   * @param rootId -- from show.getSeriesId
   * @param altFilmId -- from show.getSeasonId
   * @param getThumb
   */
  public static String getImageIdUrl(int packedImageId, int rootId, int altFilmId, String epgId, boolean getThumb){
    int imageSource=packedImageId>>IMAGE_SOURCE_BIT_OFFSET & IMAGE_SOURCE_MASK;
      int imageType=packedImageId>>IMAGE_TYPE_BIT_OFFSET & IMAGE_TYPE_MASK;
      int imageId = packedImageId & IMAGE_ID_MASK;
      String imageIdString=(char)((imageId/26)+'a')+""+(char)((imageId%26)+'a');

      switch ( imageSource ){
        case IMAGE_SOURCE_BONES_GSTATIC:
          switch(imageType){
            case IMAGE_TYPE_BOX_ART_TALL:
              return "http://www.gstatic.com/tv/thumb/dvdboxart/"+rootId+"/p"+rootId+"_d_v"+(getThumb?"6":"7")+"_"+imageIdString+".jpg";
            case IMAGE_TYPE_PHOTO_TALL:
            case IMAGE_TYPE_PHOTO_WIDE:
            case IMAGE_TYPE_POSTER_TALL:
            case IMAGE_TYPE_POSTER_WIDE:
              return "http://www.gstatic.com/tv/thumb/movies/"+altFilmId+"/"+altFilmId+"_"+imageIdString+(getThumb?"_t":"")+".jpg";
          }
          break;
        case IMAGE_SOURCE_FIBER_GSTATIC:
          // expand old-format 12 character EPG-IDs to new-format 14-character ones
          // as VOD-CMS does not do the EPGID...
          if ( epgId.length()==12 ){
            epgId=epgId.substring(0, 2)+"00"+epgId.substring(2);
          }
          switch(imageType){
            case IMAGE_TYPE_BOX_ART_TALL:
              return "http://www.gstatic.com/fiber/images/"+epgId+"/boxart_v_"+imageId+(getThumb?"_t":"")+".jpg";
            case IMAGE_TYPE_PHOTO_TALL:
              return "http://www.gstatic.com/fiber/images/"+epgId+"/photo_v_"+imageId+(getThumb?"_t":"")+".jpg";
            case IMAGE_TYPE_PHOTO_WIDE:
              return "http://www.gstatic.com/fiber/images/"+epgId+"/photo_h_"+imageId+(getThumb?"_t":"")+".jpg";
            case IMAGE_TYPE_POSTER_TALL:
              return "http://www.gstatic.com/fiber/images/"+epgId+"/poster_v_"+imageId+(getThumb?"_t":"")+".jpg";
            case IMAGE_TYPE_POSTER_WIDE:
              return "http://www.gstatic.com/fiber/images/"+epgId+"/poster_h_"+imageId+(getThumb?"_t":"")+".jpg";
          }
          break;
      }
      return null;
  }

  long duration;
  Stringer title;
  volatile String episodeNameStr;
  byte[] episodeNameBytes;
  volatile String descStr;
  byte[] descBytes;
  Stringer[] categories;
  Person[] people;
  byte[] roles;
  Stringer rated;
  Stringer[] ers;
  Stringer year;
  Stringer pr;
  Stringer[] bonuses;
  Stringer language;
  long originalAirDate;
  byte[] externalID;
  long lastWatched;
  boolean dontLike = false;
  // 2 for cachedUnique means we've forced it that way due to the EPG data
  byte cachedUnique = UNKNOWN_UNIQUE;
  short seasonNum;
  short episodeNum;
  short altEpisodeNum; // for multi-episode Shows
  int showcardID; // links to the SeriesInfo object for this Show, or the film ID for movies
  int seriesID; // links to the id that represents this actual TV show
  short[] imageIDs; // identifiers to resolve what all the images are
  byte[][] imageURLs; // partial URL's for Schedules Direct images

  public static final Comparator<Show> EXTID_COMPARATOR =
      new Comparator<Show>()
  {
    public int compare(Show s1, Show s2)
    {
      if (s1 == s2)
        return 0;
      else if (s1 == null)
        return 1;
      else if (s2 == null)
        return -1;

      return Wizard.byteStringCompare(s1.externalID, s2.externalID);
    }
  };
}
