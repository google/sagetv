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
import java.util.Comparator;
import java.util.Map;

/**
 *
 * @author jkardatzke
 */
public class Person extends DBObject
{
  public static final int PERSON_TYPE_MASK = 0xF0000000;
  public static final int PERSON_ID_MASK = 0x0FFFFFFF;
  public static final int PERSON_TYPE_PERSON = 0x00000000;
  public static final int PERSON_TYPE_TEAM = 0x10000000;
  public static final int PERSON_TYPE_COLLEGE = 0x20000000;

  Person(int inID)
  {
    super(inID);
    extID = 0;
    yearList = Pooler.EMPTY_SHORT_ARRAY;
    awardNames = Pooler.EMPTY_STRINGER_ARRAY;
    headshotImageId = -1;
    headshotUrls = Pooler.EMPTY_2D_BYTE_ARRAY;
  }
  Person(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    name = in.readUTF();
    ignoreCaseHash = name.toLowerCase().hashCode();
    if (ver >= 0x4F)
    {
      Wizard wiz = Wizard.getInstance();
      extID = in.readInt();
      dateOfBirth = in.readInt();
      dateOfDeath = in.readInt();
      birthPlace = wiz.getBonusForID(in.readInt());
      short numAwards = in.readShort();
      if (numAwards > 0)
      {
        yearList = new short[numAwards];
        for (int i = 0; i < numAwards; i++)
          yearList[i] = in.readShort();
        awardNames = new Stringer[numAwards];
        for (int i = 0; i < numAwards; i++)
          awardNames[i] = wiz.getBonusForID(in.readInt());
      }
      else
      {
        yearList = Pooler.EMPTY_SHORT_ARRAY;
        awardNames = Pooler.EMPTY_STRINGER_ARRAY;
      }
      headshotImageId = in.readShort();
    }
    else
    {
      extID = 0;
      yearList = Pooler.EMPTY_SHORT_ARRAY;
      awardNames = Pooler.EMPTY_STRINGER_ARRAY;
      headshotImageId = -1;
    }

    if (ver >= 0x57)
    {
      byte headshotLen = in.readByte();
      headshotUrls = headshotLen == 0 ? Pooler.EMPTY_2D_BYTE_ARRAY : new byte[headshotLen][];
      for (int i = 0; i < headshotLen; i++)
      {
        int headshotUrlLen = in.readShort();
        headshotUrls[i] = new byte[headshotUrlLen];
        in.readFully(headshotUrls[i], 0, headshotUrlLen);
      }
    }
    else
    {
      headshotUrls = Pooler.EMPTY_2D_BYTE_ARRAY;
    }
  }

  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    boolean useLookupIdx = (flags & Wizard.WRITE_OPT_USE_ARRAY_INDICES) != 0;
    out.writeUTF(name);
    out.writeInt(extID);
    out.writeInt(dateOfBirth);
    out.writeInt(dateOfDeath);
    out.writeInt(birthPlace == null ? 0 : (useLookupIdx ? birthPlace.lookupIdx : birthPlace.id));
    out.writeShort(yearList == null ? 0 : yearList.length);
    if (yearList != null)
    {
      for (int i = 0; i < yearList.length; i++)
        out.writeShort(yearList[i]);
      for (int i = 0; i < awardNames.length; i++)
        out.writeInt(useLookupIdx ? awardNames[i].lookupIdx : awardNames[i].id);
    }
    out.writeShort(headshotImageId);
    // This will always be the case if there are images.
    if (headshotUrls.length == 2)
    {
      out.writeByte(2);
      out.writeShort(headshotUrls[0].length);
      out.write(headshotUrls[0]);
      out.writeShort(headshotUrls[1].length);
      out.write(headshotUrls[1]);
    }
    else
    {
      out.writeByte(0);
    }
  }

  public String toString()
  {
    return name;
  }

  public String getFullString()
  {
    return "Person[" + name + ", extID=" + extID + " dob=" + getDateOfBirth() + " dod=" + getDateOfDeath() + " bplace=" +
        birthPlace + " imageID=" + headshotImageId + "]";
  }

  void update(DBObject x)
  {
    Person fromMe = (Person) x;
    name = fromMe.name;
    ignoreCaseHash = name.toLowerCase().hashCode();
    extID = fromMe.extID;
    dateOfBirth = fromMe.dateOfBirth;
    dateOfDeath = fromMe.dateOfDeath;
    birthPlace = fromMe.birthPlace;
    yearList = fromMe.yearList;
    awardNames = fromMe.awardNames;
    headshotImageId = fromMe.headshotImageId;
    headshotUrls = fromMe.headshotUrls;
    super.update(fromMe);
  }

  public boolean hasImage()
  {
    if (extID < 0)
    {
      resolveAlias();
      if (orgPerson != null)
        return orgPerson.hasImage();
    }

    return headshotImageId >= 0 || headshotUrls.length == 2;
  }

  public String getImageURL(boolean thumb)
  {
    if (headshotUrls.length == 2)
    {
      return SDImages.decodeGeneralImageUrl(headshotUrls[thumb ? 1 : 0]);
    }

    if (extID < 0)
    {
      resolveAlias();
      if (orgPerson != null)
        return orgPerson.getImageURL(thumb);
    }
    return getImageUrl(headshotImageId, extID, thumb);
  }

  public String getDateOfBirth()
  {
    if (extID < 0)
    {
      resolveAlias();
      if (orgPerson != null)
        return orgPerson.getDateOfBirth();
    }
    if (dateOfBirth == 0)
      return "";
    return getDateString(dateOfBirth);
  }

  public static String getDateString(int packedDate)
  {
    int[] dateInfo = unPackDate(packedDate);
    return dateInfo[1] + "/" + dateInfo[2] + "/" + dateInfo[0];
  }

  public String getDateOfDeath()
  {
    if (extID < 0)
    {
      resolveAlias();
      if (orgPerson != null)
        return orgPerson.getDateOfDeath();
    }
    if (dateOfDeath == 0)
      return "";
    return getDateString(dateOfDeath);
  }

  public boolean isPersonIdentical(Person testMe)
  {
    if (testMe.name.equals(name) &&
        testMe.extID == extID &&
        testMe.dateOfBirth == dateOfBirth &&
        testMe.dateOfDeath == dateOfDeath &&
        testMe.birthPlace == birthPlace &&
        testMe.yearList.length == yearList.length &&
        testMe.awardNames.length == awardNames.length &&
        testMe.headshotImageId == headshotImageId &&
        testMe.headshotUrls.length == headshotUrls.length &&
        testMe.getMediaMask() == getMediaMask())
    {
      for (int i = 0; i < yearList.length; i++)
        if (yearList[i] != testMe.yearList[i])
          return false;
      for (int i = 0; i < awardNames.length; i++)
        if (awardNames[i] != testMe.awardNames[i])
          return false;
      for (int i = 0; i < headshotUrls.length; i++)
      {
        if (headshotUrls[i].length != testMe.headshotUrls[i].length)
          return false;
        for (int j = 0; j < headshotUrls[i].length; j++)
          if (headshotUrls[i][j] != testMe.headshotUrls[i][j])
            return false;
      }
      return true;
    }
    else
      return false;
  }

  public String getName() { return name; }

  public boolean equalsIgnoreCase(Person compare)
  {
    // The hash could be 0, but it's not going to be any more often than any other hash and in case
    // we miss a case where the title is set, this covers that problem.
    if (ignoreCaseHash == 0 || compare.ignoreCaseHash == 0)
      return toString().equalsIgnoreCase(compare.toString());
    return compare.ignoreCaseHash == ignoreCaseHash && toString().equalsIgnoreCase(compare.toString());
  }

  public int getNumAwards()
  {
    if (extID < 0)
    {
      resolveAlias();
      if (orgPerson != null)
        return orgPerson.getNumAwards();
    }
    return (yearList == null) ? 0 : yearList.length;
  }
  public int getAwardYear(int idx)
  {
    if (extID < 0)
    {
      resolveAlias();
      if (orgPerson != null)
        return orgPerson.getAwardYear(idx);
    }
    return (yearList == null || idx < 0 || idx >= yearList.length) ? 0 : yearList[idx];
  }
  public String getAward(int idx)
  {
    if (extID < 0)
    {
      resolveAlias();
      if (orgPerson != null)
        return orgPerson.getAward(idx);
    }
    return (awardNames == null || idx < 0 || idx >= awardNames.length) ? "" : awardNames[idx].name;
  }

  public Person getOriginalAlias()
  {
    if (extID < 0)
    {
      resolveAlias();
      return orgPerson;
    }
    else
      return null;
  }

  public String getBirthplace()
  {
    return birthPlace == null ? "" : birthPlace.name;
  }

  private void resolveAlias()
  {
    if (extID >= 0 || orgPerson != null) return;
    orgPerson = Wizard.getInstance().getPersonForExtID(-1 * extID);
    // Our primary guide data source Schedules Direct doesn't always have the original person, so
    // this just creates a lot of annoying log entries.
    /*if (orgPerson == null)
    {
      if (Sage.DBG) System.out.println("ERROR Could not resolve aliased person back to original person id=" + (-1*extID) + " name=" + name);
    }*/
  }

  /**
   * convert an image ID into a URL depending
   * on what personType this personId is
   *
   * @param imageId
   * @param personId -- from show.getSeriesId
   * @param getThumb
   */
  public static String getImageUrl(int imageId, int personId, boolean getThumb){

    if ( imageId == -1)
      return null;

    String imageIdString=(char)((imageId/26)+'a')+""+(char)((imageId%26)+'a');
    int tmsId=personId&PERSON_ID_MASK;

    switch ( personId & PERSON_TYPE_MASK ) {
      case PERSON_TYPE_PERSON:
        return "http://www.gstatic.com/tv/thumb/persons/"+tmsId+"/"+tmsId+"_v"+(getThumb?"2":"4")+"_"+imageIdString+".jpg";
      case PERSON_TYPE_COLLEGE:
        return "http://www.gstatic.com/tv/thumb/sportslogos/"+tmsId+"/u"+tmsId+"_l_h"+(getThumb?"5":"6")+"_"+imageIdString+".png";
      case PERSON_TYPE_TEAM:
        return "http://www.gstatic.com/tv/thumb/sportslogos/"+tmsId+"/t"+tmsId+"_l_h"+(getThumb?"5":"6")+"_"+imageIdString+".png";
      default:
        return null;
    }
  }

  public static final int YEAR_SHIFT_BITS = 9;
  public static final int MONTH_SHIFT_BITS = 5;
  public static final int DAY_MASK = 0x1F;
  public static final int MONTH_MASK = 0xF;
  public static final int YEAR_MASK = 0xFFF;

  /**
   * convert a date in format YYYY-MM-DD to an packed integer
   * spare (11 bits ), year(12 bits 0-4095), month (4 bits=1-12 ), day(5 bits=1-31)
   * @param dateString
   * @return date packed into an int
   */
  static public int packDate(String dateString) throws NumberFormatException {
    if ( dateString.length()!=10 || dateString.charAt(4)!='-' || dateString.charAt(7) != '-')
      throw new NumberFormatException("invalid YYYY-MM-DD date format: "+dateString);
    int year=Integer.parseInt(dateString.substring(0,4));
    int month=Integer.parseInt(dateString.substring(5,7));
    int day=Integer.parseInt(dateString.substring(8,10));

    return ((year & Person.YEAR_MASK)<<Person.YEAR_SHIFT_BITS)
        | ((month & Person.MONTH_MASK)<<Person.MONTH_SHIFT_BITS)
        | ((day & Person.DAY_MASK));
  }

  /**
   * convert a packed date in format YYYY-MM-DD to an integer[3]
   * YYYY,MM,DD
   */
  static public int[] unPackDate(int packedDate ) {
    int retval[]=new int[3];

    retval[2]=packedDate&DAY_MASK;
    retval[1]=( packedDate>>MONTH_SHIFT_BITS) & MONTH_MASK;
    retval[0]=( packedDate>>YEAR_SHIFT_BITS) & YEAR_MASK;

    return retval;
  }

  String name;
  int extID;
  int dateOfBirth;
  int dateOfDeath;
  Stringer birthPlace;
  // awards
  short[] yearList;
  Stringer[] awardNames;
  short headshotImageId;
  // The first dimension is the large image URL. The second dimension is the thumbnail image URL.
  byte[][] headshotUrls;
  // For resolved aliases
  transient Person orgPerson;
  // To speed up case insensitive searches
  transient int ignoreCaseHash;

  public static final Comparator<Person> NAME_COMPARATOR =
      new Comparator<Person>()
  {
    public int compare(Person p1, Person p2)
    {
      if (p1 == p2)
        return 0;
      else if (p1 == null)
        return 1;
      else if (p2 == null)
        return -1;

      int rv = p1.name.compareTo(p2.name);
      if (rv != 0)
        return rv;
      return p1.extID - p2.extID;
    }
  };

  public static final Comparator<Person> EXTID_COMPARATOR =
      new Comparator<Person>()
  {
    public int compare(Person p1, Person p2)
    {
      if (p1 == p2)
        return 0;
      else if (p1 == null)
        return 1;
      else if (p2 == null)
        return -1;

      return (p1.extID != p2.extID)
          ? p1.extID - p2.extID
          : p1.name.compareTo(p2.name);
    }
  };

}
