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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

public abstract class DBObject implements Cloneable
{
  public static final int MEDIA_MASK_TV = 0x01;
  // The following are mutually exclusive masks
  public static final int MEDIA_MASK_MUSIC = 0x02;
  public static final int MEDIA_MASK_VIDEO = 0x04;
  public static final int MEDIA_MASK_DVD = 0x08;
  public static final int MEDIA_MASK_PICTURE = 0x10;
  public static final int MEDIA_MASK_BLURAY = 0x20;
  public static final int MEDIA_MASK_ALL = 0x1FF;

  DBObject(int inID)
  {
    id = inID;
    Wizard.getInstance().notifyOfID(id);
  }

  DBObject(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    id = readID(in, idMap);
    Wizard.getInstance().notifyOfID(id);
    readMediaMask(in, ver);
  }

  /**
   * Read media mask from data file <p>
   * File Versions before 0x37 did not have a media mask so skip <br/>
   * File versions between 0x37 and 0x51(inc) had a byte media mask <br/>
   * File versions greater than 0x52(inc) had a int media mask <br/>
   * @param in data input stream
   * @param ver database version
   * @throws IOException when read fails
   */
  private void readMediaMask(DataInput in, byte ver) throws IOException {
    if (ver >= 0x37 && ver < 0x52 ) {
      mediaMask = (in.readByte()) & MEDIA_MASK_ALL; // fix negative media masks...
    } else if ( ver >= 0x52 ) {
      mediaMask = in.readInt() & MEDIA_MASK_ALL;
    }
  }

  DBObject(DataInput in, byte ver, Map<Integer, Integer> idMap, int baseID) throws IOException
  {
    id = readID(in, idMap, baseID);
    Wizard.getInstance().notifyOfID(id);
    readMediaMask(in, ver);
  }

  void write(DataOutput out, int flags) throws IOException
  {
    out.writeInt(id);
    out.writeInt(mediaMask);
  }

  int readID(DataInput in, Map<Integer, Integer> idMap, int baseID) throws IOException
  {
    if (baseID != 0)
      return in.readInt() + baseID;
    return readID(in, idMap);
  }

  int readID(DataInput in, Map<Integer, Integer> idMap) throws IOException
  {
    int inID = in.readInt();
    if (idMap == null || inID == 0)
      return inID;
    Integer wrappedID = new Integer(inID);
    Integer newID = idMap.get(wrappedID);
    if (newID != null)
      return newID;
    else
    {
      newID = new Integer(Wizard.getInstance().getNextWizID());
      idMap.put(wrappedID, newID);
      return newID;
    }
  }

  public Object clone()
  {
    try
    {
      return super.clone();
    }
    catch (CloneNotSupportedException e)
    {
      throw new InternalError("I can clone, so bite me!!!");
    }
  }

  void update(DBObject fromMe)
  {
    this.mediaMask = fromMe.mediaMask;
  }

  public int getID() { return id; }

  boolean validate() { return true; }

  public final void addMediaMask(int addMask)
  {
    setMediaMask((mediaMask | addMask ) & MEDIA_MASK_ALL);
  }

  public final void removeMediaMask(int removeMask)
  {
    setMediaMask((mediaMask & (~removeMask)) & MEDIA_MASK_ALL);
  }

  public final void setMediaMask(int newMask)
  {
    mediaMask = newMask & MEDIA_MASK_ALL;
  }

  public final boolean hasMediaMask(int testMediaMask)
  {
    return ((mediaMask & testMediaMask) == testMediaMask);
  }

  public final boolean hasMediaMaskAny(int testMediaMask)
  {
    return ((mediaMask & testMediaMask) != 0);
  }

  public final int getMediaMask()
  {
    return mediaMask;
  }

  public final String getMediaMaskString()
  {
    StringBuffer sb = new StringBuffer();
    if (hasMediaMask(MEDIA_MASK_TV))
      sb.append('T');
    else
      sb.append(' ');
    if (hasMediaMask(MEDIA_MASK_DVD))
      sb.append('D');
    else
      sb.append(' ');
    if (hasMediaMask(MEDIA_MASK_VIDEO))
      sb.append('V');
    else
      sb.append(' ');
    if (hasMediaMask(MEDIA_MASK_MUSIC))
      sb.append('M');
    else
      sb.append(' ');
    if (hasMediaMask(MEDIA_MASK_PICTURE))
      sb.append('P');
    else
      sb.append(' ');
    if (hasMediaMask(MEDIA_MASK_BLURAY))
      sb.append('B');
    else
      sb.append(' ');
    return sb.toString();
  }

  public final boolean isTV()
  {
    return (mediaMask & MEDIA_MASK_TV) == MEDIA_MASK_TV;
  }

  public final boolean isMusic()
  {
    return (mediaMask & MEDIA_MASK_MUSIC) == MEDIA_MASK_MUSIC;
  }

  public final boolean isPicture()
  {
    return (mediaMask & MEDIA_MASK_PICTURE) == MEDIA_MASK_PICTURE;
  }

  public final boolean isVideo()
  {
    return (mediaMask & MEDIA_MASK_VIDEO) == MEDIA_MASK_VIDEO;
  }

  public final boolean isDVD()
  {
    return (mediaMask & MEDIA_MASK_DVD) == MEDIA_MASK_DVD;
  }

  public final boolean isBluRay()
  {
    return (mediaMask & MEDIA_MASK_BLURAY) == MEDIA_MASK_BLURAY;
  }

  /**
   * Convert a media mask Character to the corresponding bitmask
   * @param mediaMaskChar Character representing media mask
   * @return int containg media mask bitmask
   */
  public static int getMediaMaskFromChar(char mediaMaskChar){
    if ( mediaMaskChar >= 'a' ) {
      // uppercase char
      mediaMaskChar=(char) (mediaMaskChar-'a'+'A');
    }
    switch (mediaMaskChar){
      case 'T':
        return MEDIA_MASK_TV;
      case 'D':
        if (MediaFile.INCLUDE_BLURAYS_AS_DVDS){
          return MEDIA_MASK_BLURAY|MEDIA_MASK_DVD;
        } else {
          return MEDIA_MASK_DVD;
        }
      case 'V':
        return MEDIA_MASK_VIDEO;
      case 'M':
        return MEDIA_MASK_MUSIC;
      case 'P':
        return MEDIA_MASK_PICTURE;
      case 'B':
        return MEDIA_MASK_BLURAY;
      default:
        return 0;
    }
  }

  public static int getMediaMaskFromString(String mediaMaskString)
  {
    int rv=0;
    for (int i = 0; i < mediaMaskString.length(); i++) {
      rv|=getMediaMaskFromChar(mediaMaskString.charAt(i));
    }
    return rv;
  }

  final int id;
  private int mediaMask;
  // This is used for when we save out the DB fully we will be able to load
  // it much faster since we do not need to lookup objects with binary search
  // and instead can just reference their array index
  int lookupIdx;

  public static final Comparator<DBObject> ID_COMPARATOR =
      new Comparator<DBObject>()
  {
    public int compare(DBObject o1, DBObject o2)
    {
      if (o1 == o2)
        return 0;
      else if (o1 == null)
        return 1;
      else if (o2 == null)
        return -1;

      return o1.id - o2.id;
    }
  };
}
