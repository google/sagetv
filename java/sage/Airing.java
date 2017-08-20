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
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Airing extends DBObject implements Schedulable
{
  public static final long MILLIS_PER_HOUR = 60*60*1000L;


  public String getFullString()
  {
    StringBuilder sb = new StringBuilder();
    Show myShow = getShow();
    if (myShow == null)
      return Sage.rez("InvalidAiring");
    sb.append(myShow.getTitle());
    if (myShow.getEpisodeName().length() > 0)
    {
      sb.append(" - ");
      sb.append(myShow.getEpisodeName());
    }
    sb.append('\n');
    if (myShow.getDesc().length() > 0)
    {
      sb.append(myShow.getDesc());
      sb.append('\n');
    }
    boolean extras = false;
    if (myShow.categories.length > 0)
    {
      extras = true;
      sb.append(myShow.getCategory());
      if (myShow.categories.length > 1)
      {
        sb.append('/');
        sb.append(myShow.getSubCategory());
      }
      sb.append(' ');
    }
    if (myShow.year != null)
    {
      extras = true;
      sb.append(myShow.getYear());
      sb.append(' ');
    }
    if (myShow.rated != null)
    {
      extras = true;
      sb.append(myShow.getRated());
      sb.append(' ');
    }
    if (myShow.pr != null)
    {
      extras = true;
      sb.append(myShow.getParentalRating());
      sb.append(' ');
    }
    if (isFirstRun())
    {
      extras = true;
      sb.append(Sage.rez("FirstRun") + " ");
    }
    String[] bon = myShow.getBonuses();
    for (int i = 0; i < bon.length; i++)
    {
      extras = true;
      sb.append(bon[i]);
      sb.append(' ');
    }
    if (extras) sb.append('\n');

    return sb.toString();
  }

  public String getPartialString()
  {
    StringBuilder sb = new StringBuilder();
    Show myShow = getShow();
    if (myShow != null)
    {
      sb.append(myShow.getTitle());
      if (myShow.getEpisodeName().length() > 0)
      {
        sb.append("-");
        sb.append(myShow.getEpisodeName());
      }
      else if (myShow.getDesc().length() > 0 &&
          myShow.getDesc().length() < 32)
      {
        sb.append("-");
        sb.append(myShow.getDesc());
      }
      sb.append('\n');
    }
    sb.append(Sage.rez("Airing_Duration_Channel_Time", new Object[] { Sage.durFormatHrMinPretty(duration),
        (getChannel() != null ? (getChannelNum(0) + ' ' + getChannel().getName()) : getChannelNum(0)),
        ZClock.getSpecialTimeString(new Date(time))
    }));
    return sb.toString();
  }

  public String getShortString()
  {
    StringBuilder sb = new StringBuilder();
    if (isMusic())
    {
      Show myShow = getShow();
      if (myShow == null)
        return Sage.rez("Unknown");
      for (int i = 0; i < myShow.people.length; i++)
      {
        if (myShow.roles[i] == Show.ARTIST_ROLE)
        {
          if (myShow.getEpisodeName().length() > 0)
          {
            sb.append(Sage.rez("Song_By_Artist", new Object[] { myShow.getEpisodeName(),
                myShow.people[i].name}));
          }
          else
          {
            sb.append(myShow.people[i].name);
          }
          break;
        }
      }
      if (sb.length() == 0)
        return myShow.getEpisodeName();
      if (sb.length() == 0)
        return Sage.rez("Unknown");
    }
    else
    {
      sb.append(Sage.rez("Airing_Title_Time_Channel", new Object[] { getTitle(),
          ZClock.getSpecialTimeString(new Date(time)),
          (getChannel() != null ? (getChannelNum(0) + ' ' + getChannel().getName()) : getChannelNum(0)),
      }));
    }
    return sb.toString();
  }

  public String getChannelNum(long providerID)
  {
    EPG epg = EPG.getInstance();
    long[] provIDs = (providerID == 0) ? epg.getAllProviderIDs() : new long[] { providerID };
    String rv = null;
    for (int i = 0; i < provIDs.length; i++)
    {
      String[] nums = epg.getChannels(provIDs[i], stationID);
      for (int j = 0; j < nums.length; j++)
      {
        if (nums[j].length() > 0)
        {
          rv = nums[j];
          EPGDataSource epgds = epg.getSourceForProviderID(provIDs[i]);
          if (epgds != null && epgds.canViewStationOnChannel(stationID, nums[j]))
            return rv;
        }
      }
    }
    return rv == null ? "" : rv;
  }

  public int getStationID() { return stationID; }

  public long getStartTime() { return time; }

  public long getDuration() { return duration; }

  public long getEndTime() { return time + duration; }

  /**
   * @return First Run state - defined as Live or New or within 28 days of the Original Air Date.
   */
  public boolean isFirstRun() {
    Show s = getShow();
    return ((miscB & (LIVE_MASK | NEW_MASK)) != 0)
        || (s != null && ((time - s.originalAirDate) < 28 * Sage.MILLIS_PER_DAY));
  }

  /**
   * Get adjusted the starting time for this airing.
   * <p/>
   * For Favorites that are back-to-back, we don't apply any padding at that junction.
   *
   * @return Adjusted starting time for this airing
   */
  public long getSchedulingStart()
  {
    ManualRecord mr = Wizard.getInstance().getManualRecord(this);
    if (mr != null)
      return mr.getStartTime();
    Agent bond = Carny.getInstance().getCauseAgent(this);
    if (bond == null || bond.startPad == 0)
      return time;
    // For Favorites that are back-to-back, we don't apply any padding at that junction
    if (!SeekerSelector.USE_BETA_SEEKER && Carny.getInstance().isLoveAir(this) &&
      Sage.getBoolean("remove_padding_on_back_to_back_favorites", true))
    {
      return getAdjustedSchedulingStart(bond);
    }
    return (time - bond.startPad);
  }
  private long getAdjustedSchedulingStart(Agent bond)
  {
    Airing priorAir = Wizard.getInstance().getTimeRelativeAiring(this, -1);
    ManualRecord priorMR = null;
    if (priorAir != this && Carny.getInstance().isLoveAir(priorAir))
    {
      if ((priorMR = Wizard.getInstance().getManualRecord(priorAir)) != null)
      {
        if (time - bond.startPad >= priorMR.getEndTime())
        {
          return time - bond.startPad;
        }
        else
        {
          return Math.max(time - bond.startPad, Math.min(time, priorMR.getEndTime()));
        }
      }
      else if (!priorAir.isWatchedForSchedulingPurpose())
      {
        Agent priorBond = Carny.getInstance().getCauseAgent(priorAir);
        if (priorBond != null)
        {
          // See if there's actually a conflict here that needs to be removed, don't remove padding if
          // we don't have to
          if (time - bond.startPad >= priorAir.time + priorAir.duration + priorBond.stopPad)
          {
            return time - bond.startPad;
          }
          else
          {
            return Math.max(time - bond.startPad, Math.min(time, priorAir.time + priorAir.duration + priorBond.stopPad));
          }
        }
      }
    }
    return time - bond.startPad;
  }

  /**
   * Get adjusted the full duration of this airing.
   * <p/>
   * For Favorites that are back-to-back, we don't apply any padding at that junction.
   *
   * @return Adjusted full duration time of this airing
   */
  public long getSchedulingDuration()
  {
    ManualRecord mr = Wizard.getInstance().getManualRecord(this);
    if (mr != null)
      return mr.duration;
    Agent bond = Carny.getInstance().getCauseAgent(this);
    if (bond == null || (bond.stopPad == 0 && bond.startPad == 0))
      return duration;
    if (!SeekerSelector.USE_BETA_SEEKER && Carny.getInstance().isLoveAir(this) &&
        Sage.getBoolean("remove_padding_on_back_to_back_favorites", true))
    {
      return getAdjustedSchedulingEnd(bond) - getAdjustedSchedulingStart(bond);
    }
    return duration + bond.stopPad + bond.startPad;
  }

  /**
   * Get adjusted the ending time for this airing.
   * <p/>
   * For Favorites that are back-to-back, we don't apply any padding at that junction.
   *
   * @return Adjusted ending time for this airing
   */
  public long getSchedulingEnd()
  {
    ManualRecord mr = Wizard.getInstance().getManualRecord(this);
    if (mr != null)
      return mr.getEndTime();
    Agent bond = Carny.getInstance().getCauseAgent(this);
    if (bond == null || bond.stopPad == 0)
      return time + duration;
    else if (!SeekerSelector.USE_BETA_SEEKER && Carny.getInstance().isLoveAir(this) &&
      Sage.getBoolean("remove_padding_on_back_to_back_favorites", true))
    {
      return getAdjustedSchedulingEnd(bond);
    }
    return time + duration + bond.stopPad;
  }
  private long getAdjustedSchedulingEnd(Agent bond)
  {
    Airing nextAir = Wizard.getInstance().getTimeRelativeAiring(this, 1);
    ManualRecord nextMR = null;
    if (nextAir != this && Carny.getInstance().isLoveAir(nextAir))
    {
      if ((nextMR = Wizard.getInstance().getManualRecord(nextAir)) != null)
      {
        if (time + duration + bond.stopPad <= nextMR.getStartTime())
        {
          return time + duration + bond.stopPad;
        }
        else
        {
          return Math.min(time + duration + bond.stopPad, Math.max(time + duration, nextMR.getStartTime()));
        }
      }
      else if (!nextAir.isWatchedForSchedulingPurpose())
      {
        Agent nextBond = Carny.getInstance().getCauseAgent(nextAir);
        if (nextBond != null)
        {
          // See if there's actually a conflict here that needs to be removed, don't remove padding if
          // we don't have to
          if (time + duration + bond.stopPad <= nextAir.time - nextBond.startPad)
          {
            return time + duration + bond.stopPad;
          }
          else
          {
            return Math.min(time + duration + bond.stopPad, Math.max(time + duration, nextAir.time - nextBond.startPad));
          }
        }
      }
    }
    return time + duration + bond.stopPad;
  }

  @Override
  boolean validate()
  {
    getShow();
    if (myShow == null)
      return false;
    // Be sure all the objects below us have our mask applied to them
    if (getMediaMask() != 0 && Wizard.GENERATE_MEDIA_MASK)
      myShow.addMediaMaskRecursive(getMediaMask());
    return true;
  }
  public Show getShow()
  {
    return (myShow != null) ? myShow : (myShow = Wizard.getInstance().getShowForID(showID));
  }

  public int getShowID()
  {
    return showID;
  }

  public long getTTA() { return Math.max(0, time - Sage.time()); }

  public Channel getChannel() { return Wizard.getInstance().getChannelForStationID(stationID); }

  public String getChannelName()
  {
    Channel c = getChannel();
    return (c == null) ? "" : c.name;
  }

  public boolean doesOverlap(Airing testMe)
  {
    return ((testMe.getEndTime() > time) && (testMe.time < getEndTime()));
  }

  public boolean doesOverlap(long startTime, long endTime)
  {
    return (endTime > time) && (startTime < getEndTime());
  }

  public boolean doesSchedulingOverlap(Airing testMe)
  {
    return ((testMe.getSchedulingEnd() > getSchedulingStart()) && (testMe.getSchedulingStart() < getSchedulingEnd()));
  }

  public boolean doesSchedulingOverlap(long startTime, long endTime)
  {
    return (endTime > getSchedulingStart()) && (startTime < getSchedulingEnd());
  }

  public boolean isWatched()
  {
    return BigBrother.isWatched(this);
  }

  public boolean isWatchedForSchedulingPurpose()
  {
    return BigBrother.isWatched(this, true);
  }
  public boolean isViewable()
  {
    return EPG.getInstance().canViewStation(stationID);
  }

  public Airing[] getNextWatchableAirings(long afterTime)
  {
    afterTime = Math.min(afterTime, time);
    Show myShow = getShow();
    if (myShow == null)
      return Pooler.EMPTY_AIRING_ARRAY;
    Airing[] showAirs = Wizard.getInstance().getAirings(myShow, afterTime);
    List<Airing> rv = new ArrayList<Airing>();
    for (int i = 0; i < showAirs.length; i++)
    {
      if (showAirs[i].isTV() && showAirs[i].isViewable())
        rv.add(showAirs[i]);
    }
    return rv.toArray(Pooler.EMPTY_AIRING_ARRAY);
  }

  public boolean hasLaterWatchableAiring()
  {
    Show myShow = getShow();
    if (myShow == null)
      return false;
    Airing[] showAirs = Wizard.getInstance().getAirings(myShow, getEndTime());
    for (int i = 0; i < showAirs.length; i++)
    {
      if (showAirs[i].isTV() && showAirs[i].isViewable())
        return true;
    }
    return false;
  }

  public boolean isMustSee()
  {
    return Profiler.isMustSee(this);
  }

  public boolean isDontLike() {
    Show myShow = getShow();
    if (myShow != null && myShow.isDontLike())
      return true;
    Wasted w = Wizard.getInstance().getWastedForAiring(id);
    return (w != null) && w.isManual();
  }

  @Override
  void update(DBObject fromMe)
  {
    Airing a = (Airing) fromMe;
    if (showID != a.showID)
      myShow = null;
    showID = a.showID;
    time = a.time;
    duration = a.duration;
    partsB = a.partsB;
    miscB = a.miscB;
    prB = a.prB;
    stationID = a.stationID;
    persist = a.persist;
    super.update(fromMe);
  }

  Airing(int inID)
  {
    super(inID);
  }
  Airing(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    showID = readID(in, idMap);
    stationID = in.readInt();
    time = in.readLong();
    duration = in.readLong();
    if (duration < 0)
    {
      Wizard.INVALID_AIRING_DURATIONS = true;
      if (Sage.DBG) System.out.println("BAD AIRING DURATION: id=" + id + " showID=" + showID + " time=" + time + " duration=" + duration + " mask=" +
          getMediaMaskString());
    }
    partsB = in.readByte();
    if (ver >= 0x4A)
      miscB = in.readInt();
    else
      miscB = in.readByte() & 0xFF;
    prB = in.readByte();
    if (ver >= 0x41)
      persist = in.readByte();
    if (ver >= 0x4C && ver < 0x54) {
      int size = in.readShort();
      in.skipBytes(size); // url bytes
      long foo = (ver >= 0x51) ? in.readLong() : in.readInt();
      if (foo != 0) {
        in.readShort(); // price
        in.readInt(); // flags
        in.readInt(); // winstart
        if (ver >= 0x4E) {
          size = in.readShort();
          in.skipBytes(size); // provider
        }
      }
    }
  }

  @Override
  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    out.writeInt(showID);
    out.writeInt(stationID);
    out.writeLong(time);
    out.writeLong(duration);
    out.writeByte(partsB);
    out.writeInt(miscB);
    out.writeByte(prB);
    out.writeByte(persist);
  }

  @Override
  public Object clone()
  {
    Airing rv = (Airing) super.clone();
    return rv;
  }

  public String getTitle()
  {
    if (getShow() == null) return Sage.rez("Invalid_Airing");
    else return getShow().getTitle();
  }

  // For music files
  public int getTrack() { return partsB & 0xFF; }

  // For picture files
  public int getOrientation() { return partsB & 0xFF; }

  @Override
  public String toString()
  {
    if (getShow() == null) return "BAD AIRING";
    StringBuilder sb = new StringBuilder("A[");
    sb.append(id);
    sb.append(',');
    sb.append(showID);
    sb.append(",\"");
    sb.append(getShow().getTitle());
    sb.append("\",");
    sb.append(stationID);
    sb.append('@');
    sb.append(Sage.dfLittle(time));
    sb.append(',');
    sb.append(duration/60000L);
    sb.append(',');
    sb.append(getMediaMaskString().trim());
    sb.append(']');
    return sb.toString();
  }

  public boolean isHDTV()
  {
    return ((miscB & HDTV_MASK) == HDTV_MASK);
  }

  public boolean isCC() { return ((miscB & CC_MASK) == CC_MASK); }
  public boolean isStereo() { return ((miscB & STEREO_MASK) == STEREO_MASK); }
  public boolean isSubtitled() { return ((miscB & SUBTITLE_MASK) == SUBTITLE_MASK); }
  public boolean isSAP() { return ((miscB & SAP_MASK) == SAP_MASK); }
  public boolean isPremiere() { return ((miscB & PREMIERES_BITMASK) == PREMIERE_MASK); }
  public boolean isSeasonPremiere() { return ((miscB & PREMIERES_BITMASK) == SEASON_PREMIERE_MASK); }
  public boolean isSeriesPremiere() { return ((miscB & PREMIERES_BITMASK) == SERIES_PREMIERE_MASK); }
  public boolean isChannelPremiere() { return ((miscB & PREMIERES_BITMASK) == CHANNEL_PREMIERE_MASK); }
  public boolean isSeasonFinale() { return ((miscB & PREMIERES_BITMASK) == SEASON_FINALE_MASK); }
  public boolean isSeriesFinale() { return ((miscB & PREMIERES_BITMASK) == SERIES_FINALE_MASK); }
  public boolean is3D() { return ((miscB & THREED_MASK) == THREED_MASK); }
  public boolean isDD51() { return ((miscB & DD51_MASK) == DD51_MASK); }
  public boolean isDolby() { return ((miscB & DOLBY_MASK) == DOLBY_MASK); }
  public boolean isLetterbox() { return ((miscB & LETTERBOX_MASK) == LETTERBOX_MASK); }
  public boolean isLive() { return ((miscB & LIVE_MASK) == LIVE_MASK); }
  public boolean isNew() { return ((miscB & NEW_MASK) == NEW_MASK); }
  public boolean isWidescreen() { return ((miscB & WIDESCREEN_MASK) == WIDESCREEN_MASK); }
  public boolean isSurround() { return ((miscB & SURROUND_MASK) == SURROUND_MASK); }
  public boolean isDubbed() { return ((miscB & DUBBED_MASK) == DUBBED_MASK); }
  public boolean isTaped() { return ((miscB & TAPE_MASK) == TAPE_MASK); }
  public int getTotalParts() { return partsB & 0x0F; }
  public int getPartNum() { return (partsB >> 4) & 0x0F; }

  public void appendMiscInfo(StringBuilder sb)
  {
    boolean addComma = false;
    if ((partsB & 0x0F) > 1)
    {
      sb.append(Sage.rez("Part_Of_Parts", new Object[] { new Integer((partsB >> 4) & 0x0F),
          new Integer(partsB & 0x0F) }));
      addComma = true;
    }
    if (isCC())
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Closed_Captioned"));
      addComma = true;
    }
    if (isStereo())
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Stereo"));
      addComma = true;
    }
    if (isHDTV())
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("HDTV"));
      addComma = true;
    }
    if (isSubtitled())
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Subtitled"));
      addComma = true;
    }
    if ((miscB & PREMIERES_BITMASK) == PREMIERE_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Premiere"));
      addComma = true;
    }
    if ((miscB & PREMIERES_BITMASK) == SEASON_PREMIERE_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Season_Premiere"));
      addComma = true;
    }
    if ((miscB & PREMIERES_BITMASK) == SERIES_PREMIERE_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Series_Premiere"));
      addComma = true;
    }
    if ((miscB & PREMIERES_BITMASK) == CHANNEL_PREMIERE_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Channel_Premiere"));
      addComma = true;
    }
    if ((miscB & PREMIERES_BITMASK) == SEASON_FINALE_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Season_Finale"));
      addComma = true;
    }
    if ((miscB & PREMIERES_BITMASK) == SERIES_FINALE_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Series_Finale"));
      addComma = true;
    }
    if ((miscB & SAP_MASK) == SAP_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("SAP"));
      addComma = true;
    }
    if ((miscB & THREED_MASK) == THREED_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("3D"));
      addComma = true;
    }
    if ((miscB & DD51_MASK) == DD51_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("DD5.1"));
      addComma = true;
    }
    else if ((miscB & DOLBY_MASK) == DOLBY_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Dolby"));
      addComma = true;
    }
    if ((miscB & LETTERBOX_MASK) == LETTERBOX_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Letterbox"));
      addComma = true;
    }
    if ((miscB & LIVE_MASK) == LIVE_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Live"));
      addComma = true;
    }
    if ((miscB & WIDESCREEN_MASK) == WIDESCREEN_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Widescreen"));
      addComma = true;
    }
    if ((miscB & SURROUND_MASK) == SURROUND_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Surround"));
      addComma = true;
    }
    if ((miscB & DUBBED_MASK) == DUBBED_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Dubbed"));
      addComma = true;
    }
    if ((miscB & TAPE_MASK) == TAPE_MASK)
    {
      if (addComma)
        sb.append(", ");
      sb.append(Sage.rez("Taped"));
      addComma = true;
    }
  }
  public String getMiscInfo()
  {
    StringBuilder sb = new StringBuilder();
    appendMiscInfo(sb);
    return sb.toString();
  }

  public static byte getMiscBMaskForSearch(String str, boolean caseSensitive, boolean fullMatch)
  {
    return getMiscBMaskForSearch(null, str, caseSensitive, fullMatch);
  }
  public static byte getMiscBMaskForSearch(Pattern pat)
  {
    return getMiscBMaskForSearch(pat, null, false, false);
  }
  private static byte getMiscBMaskForSearch(Pattern pat, String str, boolean caseSensitive, boolean fullMatch)
  {
    int rv = 0;
    if (pat == null && (str == null || str.length() == 0))
      return 0;
    if (pat == null && !caseSensitive)
      str = str.toLowerCase();
    String test = Sage.rez("Closed_Captioned");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | CC_MASK;
    test = Sage.rez("Stereo");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | STEREO_MASK;
    test = Sage.rez("HDTV");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | HDTV_MASK;
    test = Sage.rez("Subtitled");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | SUBTITLE_MASK;
    test = Sage.rez("SAP");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | SAP_MASK;
    test = Sage.rez("3D");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | THREED_MASK;
    test = Sage.rez("Dolby Digital 5.1");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | DD51_MASK;
    test = Sage.rez("Dolby");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | DOLBY_MASK;
    test = Sage.rez("Letterbox");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | LETTERBOX_MASK;
    test = Sage.rez("Live");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | LIVE_MASK;
    test = Sage.rez("New");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | NEW_MASK;
    test = Sage.rez("Widescreen");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | WIDESCREEN_MASK;
    test = Sage.rez("Surround");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | SURROUND_MASK;
    test = Sage.rez("Dubbed");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | DUBBED_MASK;
    test = Sage.rez("Taped");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv = rv | TAPE_MASK;
    return (byte)(rv & 0xFF);
  }

  static byte getMiscBMaskForSearchNTE(String nteString)
  {
    int rv = 0;
    if (nteString == null || nteString.length() == 0)
      return 0;
    if (StringMatchUtils.wordMatchesNte("Closed_Captioned", nteString)) rv = rv | CC_MASK;
    if (StringMatchUtils.wordMatchesNte("Stereo", nteString)) rv = rv | STEREO_MASK;
    if (StringMatchUtils.wordMatchesNte("HDTV", nteString)) rv = rv | HDTV_MASK;
    if (StringMatchUtils.wordMatchesNte("Subtitled", nteString)) rv = rv | SUBTITLE_MASK;
    if (StringMatchUtils.wordMatchesNte("SAP", nteString)) rv = rv | SAP_MASK;
    if (StringMatchUtils.wordMatchesNte("3D", nteString)) rv = rv | THREED_MASK;
    if (StringMatchUtils.wordMatchesNte("Dolby Digital 5.1", nteString)) rv = rv | DD51_MASK;
    if (StringMatchUtils.wordMatchesNte("Dolby", nteString)) rv = rv | DOLBY_MASK;
    if (StringMatchUtils.wordMatchesNte("Letterbox", nteString)) rv = rv | LETTERBOX_MASK;
    if (StringMatchUtils.wordMatchesNte("Live", nteString)) rv = rv | LIVE_MASK;
    if (StringMatchUtils.wordMatchesNte("New", nteString)) rv = rv | NEW_MASK;
    if (StringMatchUtils.wordMatchesNte("Widescreen", nteString)) rv = rv | WIDESCREEN_MASK;
    if (StringMatchUtils.wordMatchesNte("Surround", nteString)) rv = rv | SURROUND_MASK;
    if (StringMatchUtils.wordMatchesNte("Dubbed", nteString)) rv = rv | DUBBED_MASK;
    if (StringMatchUtils.wordMatchesNte("Taped", nteString)) rv = rv | TAPE_MASK;
    return (byte)(rv & 0xFF);
  }

  public static byte[] getPremiereBValuesForSearch(String str, boolean caseSensitive, boolean fullMatch)
  {
    return getPremiereBValuesForSearch(null, str, caseSensitive, fullMatch);
  }
  public static byte[] getPremiereBValuesForSearch(Pattern pat)
  {
    return getPremiereBValuesForSearch(pat, null, false, false);
  }
  private static byte[] getPremiereBValuesForSearch(Pattern pat, String str, boolean caseSensitive, boolean fullMatch)
  {
    if (pat == null && (str == null || str.length() == 0))
      return new byte[0];
    List<Byte> rv = new ArrayList<Byte>();
    if (pat == null && !caseSensitive)
      str = str.toLowerCase();
    String test = Sage.rez("Premiere");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv.add((byte) PREMIERE_MASK);
    test = Sage.rez("Season_Premiere");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv.add((byte) SEASON_PREMIERE_MASK);
    test = Sage.rez("Series_Premiere");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv.add((byte) SERIES_PREMIERE_MASK);
    test = Sage.rez("Channel_Premiere");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv.add((byte) CHANNEL_PREMIERE_MASK);
    test = Sage.rez("Season_Finale");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv.add((byte) SEASON_FINALE_MASK);
    test = Sage.rez("Series_Finale");
    if (pat == null && !caseSensitive)
      test = test.toLowerCase();
    if ((pat != null && pat.matcher(test).matches()) || (str != null && ((fullMatch && test.equals(str)) || (!fullMatch && test.indexOf(str) != -1))))
      rv.add((byte) SERIES_FINALE_MASK);
    if (rv.isEmpty())
      return new byte[0];
    byte[] rb = new byte[rv.size()];
    for (int i = 0; i < rb.length; i++)
      rb[i] = rv.get(i);
    return rb;
  }

  static byte[] getPremiereBValuesForSearchNTE(String nteString)
  {
    if (nteString == null || nteString.length() == 0)
      return new byte[0];
    List<Byte> rv = new ArrayList<Byte>();

    if ( StringMatchUtils.wordMatchesNte("Premiere", nteString))
      rv.add((byte) PREMIERE_MASK);
    if ( StringMatchUtils.wordMatchesNte("Season_Premiere", nteString))
      rv.add((byte) SEASON_PREMIERE_MASK);
    if ( StringMatchUtils.wordMatchesNte("Series_Premiere", nteString))
      rv.add((byte) SERIES_PREMIERE_MASK);
    if ( StringMatchUtils.wordMatchesNte("Channel_Premiere", nteString))
      rv.add((byte) CHANNEL_PREMIERE_MASK);
    if ( StringMatchUtils.wordMatchesNte("Season_Finale", nteString))
      rv.add((byte) SEASON_FINALE_MASK);
    if ( StringMatchUtils.wordMatchesNte("Series_Finale", nteString))
      rv.add((byte) SERIES_FINALE_MASK);
    if (rv.isEmpty())
      return new byte[0];
    byte[] rb = new byte[rv.size()];
    for (int i = 0; i < rb.length; i++)
      rb[i] = rv.get(i);
    return rb;
  }


  public String[] getRatingRestrictables()
  {
    List<String> v = new ArrayList<String>();
    if (prB > 0)
      v.add(PR_NAMES[prB]);
    Show s = getShow();
    if (s != null)
    {
      if (s.rated != null)
        v.add(s.rated.name);
      for (int i = 0; i < s.ers.length; i++)
        v.add(s.ers[i].name);
    }
    if (v.isEmpty())
      v.add("Unrated");
    if (stationID != 0)
      v.add(Integer.toString(stationID));
    return v.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public String getParentalRating()
  {
    return (prB > 0) ? PR_NAMES[prB] : "";
  }

  void setPersist(byte how)
  {
    if (persist != how)
    {
      persist = how;
      Wizard.getInstance().logUpdate(this, Wizard.AIRING_CODE);
    }
  }

  int showID;
  int stationID;
  long time;
  long duration;
  byte partsB;
  int miscB;
  byte prB;
  byte persist;

  private transient Show myShow;

  public static final int CC_MASK = 0x01;
  public static final int STEREO_MASK = 0x02;
  public static final int HDTV_MASK = 0x04;
  public static final int SUBTITLE_MASK = 0x08;
  public static final int PREMIERES_BITMASK = 0x70;
  public static final int PREMIERE_MASK = 0x10;
  public static final int SEASON_PREMIERE_MASK = 0x20;
  public static final int SERIES_PREMIERE_MASK = 0x30;
  public static final int CHANNEL_PREMIERE_MASK = 0x40;
  public static final int SEASON_FINALE_MASK = 0x50;
  public static final int SERIES_FINALE_MASK = 0x60;
  public static final int SAP_MASK = 0x80;

  // For extended misc data
  public static final int THREED_MASK = 0x100;
  public static final int DD51_MASK = 0x200;
  public static final int DOLBY_MASK = 0x400;
  public static final int LETTERBOX_MASK = 0x800;
  public static final int LIVE_MASK = 0x1000;
  public static final int NEW_MASK = 0x2000;
  public static final int WIDESCREEN_MASK = 0x4000;
  public static final int SURROUND_MASK = 0x8000;
  public static final int DUBBED_MASK = 0x10000;
  public static final int TAPE_MASK = 0x20000;

  public static final byte PERSIST_TV_MEDIAFILE_LINK = 0x1;

  public static final byte TVY_VALUE = 1;
  public static final byte TVY7_VALUE = 2;
  public static final byte TVG_VALUE = 3;
  public static final byte TVPG_VALUE = 4;
  public static final byte TV14_VALUE = 5;
  public static final byte TVMA_VALUE = 6;

  public static final String[] PR_NAMES = { "", Sage.rez("TVY"), Sage.rez("TVY7"), Sage.rez("TVG"), Sage.rez("TVPG"),
    Sage.rez("TV14"), Sage.rez("TVM") };

  public static final Comparator<Airing> SHOW_ID_COMPARATOR =
      new Comparator<Airing>()
  {
    public int compare(Airing a1, Airing a2)
    {
      if (a1 == a2)
        return 0;
      else if (a1 == null)
        return 1;
      else if (a2 == null)
        return -1;

      return (a1.showID == a2.showID)
          ? Long.signum(a1.time - a2.time)
          : a1.showID - a2.showID;
    }
  };

  /**
   * If start times are the same, sort by station ID. If the start times are different, sort by
   * start time. Sorting is low to high.
   */
  public static final Comparator<Airing> TIME_CHANNEL_COMPARATOR =
      new Comparator<Airing>()
  {
    public int compare(Airing a1, Airing a2)
    {
      if (a1 == a2)
        return 0;
      else if (a1 == null)
        return 1;
      else if (a2 == null)
        return -1;

      return (a1.time == a2.time)
          ? a1.stationID - a2.stationID
          : Long.signum(a1.time - a2.time);
    }
  };

  /**
   * If station ID's are the same, the sort by start time. If station ID's are different, sort by
   * station ID. Sorting is low to high.
   */
  public static final Comparator<Airing> CHANNEL_TIME_COMPARATOR =
      new Comparator<Airing>()
  {
    public int compare(Airing a1, Airing a2)
    {
      if (a1 == a2)
        return 0;
      else if (a1 == null)
        return 1;
      else if (a2 == null)
        return -1;

      return (a1.stationID == a2.stationID)
          ? Long.signum(a1.time - a2.time)
          : a1.stationID - a2.stationID;
    }
  };
}
