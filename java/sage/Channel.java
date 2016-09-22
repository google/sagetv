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

public final class Channel extends DBObject
{
  static final int NUM_SLOTS = 7 * 24;
  Channel(int inID)
  {
    super(inID);
    wiz = Wizard.getInstance();
    logoImages = Pooler.EMPTY_INT_ARRAY;
    logoURL = Pooler.EMPTY_BYTE_ARRAY;
  }
  Channel(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    wiz = Wizard.getInstance();
    name = in.readUTF();
    stationID = in.readInt();

    network = wiz.getNetworkForID(readID(in, idMap));
    if (ver >= 0x2F)
      in.readBoolean(); // padding to help avoid reversing db encryption algorithms
    longName = in.readUTF();
    if (ver < 0x4A)
    {
      in.readShort();//majorMinorDTVChan
    }
    else
    {
      logoMask = in.readInt();
    }
    if (ver >= 0x4C && ver < 0x54) {
      if (ver >= 0x51)
        in.readLong(); // aid
      else
        in.readInt(); // aid
    }
    if (ver >= 0x4E && ver < 0x54) {
      in.readInt(); // mask
    }
    if (ver > 0x4F)
    {
      int numLogos = in.readShort();
      if (numLogos == 0)
        logoImages = Pooler.EMPTY_INT_ARRAY;
      else
      {
        logoImages = new int[numLogos];
        for (int i = 0; i < numLogos; i++)
        {
          logoImages[i] = in.readInt();
        }
      }
    }
    else
      logoImages = Pooler.EMPTY_INT_ARRAY;
    if (ver >= 0x56)
    {
      int urlLen = in.readShort();
      if (urlLen == 0)
      {
        logoURL = Pooler.EMPTY_BYTE_ARRAY;
      }
      else
      {
        logoURL = new byte[urlLen];
        in.readFully(logoURL, 0, urlLen);
      }
    }
    else
      logoURL = Pooler.EMPTY_BYTE_ARRAY;
  }

  private static final int SMALL_PRIMARY_LOGO_MASK = 0x01000000;
  private static final int MED_PRIMARY_LOGO_MASK = 0x02000000;
  private static final int LARGE_PRIMARY_LOGO_MASK = 0x04000000;
  private static final int SMALL_SECONDARY_LOGO_MASK = 0x08000000;
  private static final int MED_SECONDARY_LOGO_MASK = 0x10000000;
  private static final int LARGE_SECONDARY_LOGO_MASK = 0x20000000;
  public static final int SD_LOGO_MASK = 0x40000000;
  public static final int LOGO_SMALL = 1;
  public static final int LOGO_MED = 2;
  public static final int LOGO_LARGE = 3;
  private static final int LOGO_STATION_MASK = 0xFFFFFF;

  // encoded image format:
  // bits 0-11 = image ID
  // bits 12-15 = thumb rescode
  // bits 16-23 = fullsize rescode

  // IMAGE ID mask
  public static final int IMAGE_ID_MASK = 0x3FF;
  public static final int IMAGE_RESCODE_MASK = 0xF;
  public static final int IMAGE_SMALL_RESCODE_SHIFT = 12;
  public static final int IMAGE_MED_RESCODE_SHIFT = 16;
  public static final int IMAGE_LARGE_RESCODE_SHIFT = 24;

  public static final int IMAGE_GFIBER_GSTATIC_SHIFT = 28;
  public static final int IMAGE_GFIBER_GSTATIC_MASK = 0x1;

  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    boolean useLookupIdx = (flags & Wizard.WRITE_OPT_USE_ARRAY_INDICES) != 0;
    out.writeUTF(name);
    out.writeInt(stationID);
    out.writeInt((network == null) ? 0 : (useLookupIdx ? network.lookupIdx : network.id));
    out.writeBoolean(true); // padding to help avoid reversing db encryption algorithms
    out.writeUTF(longName);
    out.writeInt(logoMask);
    out.writeShort(logoImages.length);
    for (int i = 0; i < logoImages.length; i++)
      out.writeInt(logoImages[i]);
    out.writeShort(logoURL.length);
    out.write(logoURL, 0, logoURL.length);
  }

  public String getNetwork() { return (network == null) ? null : network.name; }

  public String getName() { return name; }

  public String getCleanedName(long viaProvider)
  {
    String cleanName = name;
    String nums = getNumber(viaProvider);
    if (cleanName.endsWith("000" + nums))
      return cleanName.substring(0, cleanName.length() - (3 + nums.length()));
    else if (cleanName.endsWith("00" + nums))
      return cleanName.substring(0, cleanName.length() - (2 + nums.length()));
    else if (cleanName.endsWith("0" + nums))
      return cleanName.substring(0, cleanName.length() - (1 + nums.length()));
    else if (cleanName.endsWith(nums))
      return cleanName.substring(0, cleanName.length() - nums.length());
    else
      return cleanName;
  }

  public String getLongName() { return longName; }

  public boolean isViewable()
  {
    return EPG.getInstance().canViewStation(stationID);
  }
  public boolean isViewable(long viaProvider)
  {
    EPGDataSource ds = EPG.getInstance().getSourceForProviderID(viaProvider);
    if (ds == null) return false;
    return ds.canViewStation(stationID) && getNumber(viaProvider).length() > 0;
  }

  public void setCanViewChannel(long viaProvider, boolean avail)
  {
    EPGDataSource ds = EPG.getInstance().getSourceForProviderID(viaProvider);
    if (ds != null)
      ds.setCanViewStation(stationID, avail);
  }

  public String getNumber()
  {
    return getNumber(0);
  }
  public String getNumber(long viaProvider)
  {
    EPG epg = EPG.getInstance();
    long[] provIDs = (viaProvider == 0) ? epg.getAllProviderIDs() : new long[] { viaProvider };
    String rv = null;
    for (int i = 0; i < provIDs.length; i++)
    {
      String[] nums = epg.getChannels(provIDs[i], stationID);
      for (int j = 0; j < nums.length; j++)
      {
        if (nums[j].length() > 0)
        {
          rv = nums[j];
          // This can return a null value.
          EPGDataSource source = epg.getSourceForProviderID(provIDs[i]);
          if (source != null && source.canViewStationOnChannel(stationID, nums[j]))
            return rv;
        }
      }
    }
    return rv == null ? "" : rv;
  }


  public int getStationID()
  {
    return stationID;
  }

  public String getFullName()
  {
    String netName = getNetwork();
    if ((netName == null) || name.equals(netName))
      return name;
    else
      return name + ' ' + netName;
  }

  void update(DBObject x)
  {
    Channel fromMe = (Channel) x;
    network = fromMe.network;
    name = fromMe.name;
    longName = fromMe.longName;
    stationID = fromMe.stationID;
    logoMask = fromMe.logoMask;
    logoImages = (fromMe.logoImages.length == 0) ? Pooler.EMPTY_INT_ARRAY : fromMe.logoImages.clone();
    logoURL = (fromMe.logoURL.length == 0) ? Pooler.EMPTY_BYTE_ARRAY : fromMe.logoURL.clone();
    super.update(fromMe);
  }

  public String toString()
  {
    StringBuilder sb = new StringBuilder("Channel[");
    sb.append("stationID=");
    sb.append(stationID);
    sb.append(", Name=");
    sb.append(name);
    if (network != null) sb.append(", Network=" + getNetwork());
    sb.append(']');
    return sb.toString();
  }

  public int getLogoCount(int imageType)
  {
    if (logoImages.length > 0)
      return logoImages.length;

    // Schedules Direct
    if ((logoMask & SD_LOGO_MASK) != 0)
      return 1;

    switch (imageType)
    {
      case LOGO_SMALL:
        return (((logoMask & SMALL_PRIMARY_LOGO_MASK) != 0) ? 1 : 0) + (((logoMask & SMALL_SECONDARY_LOGO_MASK) != 0) ? 1 : 0);
      case LOGO_MED:
        return (((logoMask & MED_PRIMARY_LOGO_MASK) != 0) ? 1 : 0) + (((logoMask & MED_SECONDARY_LOGO_MASK) != 0) ? 1 : 0);
      case LOGO_LARGE:
        return (((logoMask & LARGE_PRIMARY_LOGO_MASK) != 0) ? 1 : 0) + (((logoMask & LARGE_SECONDARY_LOGO_MASK) != 0) ? 1 : 0);
      default:
        return 0;
    }
  }

  public String getLogoUrl(int index, int imageType)
  {
    if ( logoImages.length > 0 ){
      if ( index < logoImages.length){
        return getImageIdUrl(logoImages[index], logoMask&LOGO_STATION_MASK, imageType);
      } else {
        return null; // index out of range, should not happen...
      }
    }
    else if ((logoMask & SD_LOGO_MASK) != 0)
    {
      // Schedules Direct
      return SDImages.decodeLogoURL(logoURL, logoMask & LOGO_STATION_MASK);
    }
    else
    {
      // use classic logo locations.

      if ((logoMask & LOGO_STATION_MASK) == 0) return null;
      switch (imageType)
      {
        case LOGO_SMALL:
          return "http://download.sage.tv/epgimages/logos/" + (logoMask & LOGO_STATION_MASK) + "/Small-" + index + ".png";
        case LOGO_MED:
          return "http://download.sage.tv/epgimages/logos/" + (logoMask & LOGO_STATION_MASK) + "/Med-" + index + ".png";
        case LOGO_LARGE:
          return "http://download.sage.tv/epgimages/logos/" + (logoMask & LOGO_STATION_MASK) + "/Large-" + index + ".png";
        default:
          return null;
      }
    }
  }

  /**
   *
   * @param packedImageId
   * @param logoStationId -- the station ID for the logo
   * @param imageSize (LOGO_SMALL|LOGO_MED|LOGO_LARGE)
   */
  public static String getImageIdUrl(int packedImageId, int logoStationId, int imageSize) {
    if (((packedImageId >> IMAGE_GFIBER_GSTATIC_SHIFT) & IMAGE_GFIBER_GSTATIC_MASK) > 0) {
      // Gfiber custom image
      int imageId = packedImageId & IMAGE_ID_MASK;
      String size;
      switch (imageSize) {
        case LOGO_SMALL:
          size = "small";
          break;
        case LOGO_MED:
          size = "medium";
          break;
        case LOGO_LARGE:
        default:
          size = "large";
          break;
      }
      return "http://www.gstatic.com/fiber/channels/" + logoStationId + "/" + "logo_" + imageId
          + "_" + size + ".png";

    } else {
      // TMS images from bones
      int imageId = packedImageId & IMAGE_ID_MASK;
      String imageIdString = (char) ((imageId / 26) + 'a') + "" + (char) ((imageId % 26) + 'a');
      int rescodeShift;
      switch (imageSize) {
        case LOGO_SMALL:
          rescodeShift = IMAGE_SMALL_RESCODE_SHIFT;
          break;
        case LOGO_MED:
          rescodeShift = IMAGE_MED_RESCODE_SHIFT;
          break;
        case LOGO_LARGE:
        default:
          rescodeShift = IMAGE_LARGE_RESCODE_SHIFT;
          break;
      }

      int rescode = (packedImageId >> rescodeShift) & IMAGE_RESCODE_MASK;

      return "http://www.gstatic.com/tv/thumb/sources/" + logoStationId + "/s" + logoStationId
          + "_h" + rescode + "_" + imageIdString + ".png";
    }
  }

  String name;
  String longName;
  int stationID;
  Stringer network;
  int logoMask;
  int[] logoImages;
  byte[] logoURL;
  private Wizard wiz;

  public static final Comparator<Channel> STATION_ID_COMPARATOR =
      new Comparator<Channel>()
  {
    public int compare(Channel c1, Channel c2)
    {
      if (c1 == c2)
        return 0;
      else if (c1 == null)
        return 1;
      else if (c2 == null)
        return -1;

      return c1.stationID - c2.stationID;
    }
  };

  /**
   *  Returns the name for a channel if the passed in String is a station ID,
   *  otherwise just return the pased in String
   * @param s the potential station ID
   * @return the channel name for the station ID, otherwise the passed in String
   */
  public static String convertPotentialStationIDToName(String s)
  {
    try {
      int x = Integer.parseInt(s);
      Channel c = Wizard.getInstance().getChannelForStationID(x);
      if (c != null)
        return c.getName();
    } catch (NumberFormatException nfe) {}
    return s;
  }
}
