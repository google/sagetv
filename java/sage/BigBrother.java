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

public class BigBrother
{
  static final long MILLIS_PER_HOUR = 60*60000L;
  static final long MILLIS_PER_DAY = 24*MILLIS_PER_HOUR;
  static final long WATCH_IGNORE_TIME = 5*60000L;
  static final long WATCH_IGNORE_TIME_MOVIE = 10*60000L;
  static final long MIN_WATCH_TIME = 5000L;
  // The duration of the Show cannot be more than this less than the Airing
  // duration in order to use the Show duration. This is to account for channels
  // that have commercials in movies as alignment on commercial free movie
  // channels is generally never this much padding.
  static final long MAX_DURATION_DIFF_TO_USE_SHOW = 20*Sage.MILLIS_PER_MIN;
  static final double COMPLETE_WATCH_FRACTION = 0.66;

  public static final int NO_ALIGN = 0;
  public static final int DAY_ALIGN = 1;
  public static final int TIME_ALIGN = 2;
  public static final int FULL_ALIGN = 3;

  private static String[] getDOWs()
  {
    // i81n the days of the week
    String[] javaDows = new java.text.DateFormatSymbols(Sage.userLocale).getWeekdays();
    String[] rv = new String[7];
    for (int i = java.util.Calendar.SUNDAY; i <= java.util.Calendar.SATURDAY; i++)
    {
      rv[i - java.util.Calendar.SUNDAY] = javaDows[i];
    }
    return rv;
  }
  // This method is used purely for display purposes, so the format doesn't matter
  public static String getTimeslotString(int slotType, int slotValue)
  {
    String rv = "";
    if (slotType == DAY_ALIGN || slotType == FULL_ALIGN)
      rv += getDOWs()[Math.max(Math.min(6, slotValue/24), 0)];
    if (slotType == FULL_ALIGN)
      rv += " ";
    if (slotType == TIME_ALIGN || slotType == FULL_ALIGN)
    {
      slotValue %= 24;
      // Uses AM/PM
      if (((java.text.SimpleDateFormat)java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM,
          Sage.userLocale)).toPattern().indexOf("a") != -1)
      {
        if (slotValue == 0)
          rv += "12" +
              new java.text.DateFormatSymbols(Sage.userLocale).getAmPmStrings()[java.util.Calendar.AM];
        else if (slotValue < 12)
          rv += Integer.toString(slotValue) +
          new java.text.DateFormatSymbols(Sage.userLocale).getAmPmStrings()[java.util.Calendar.AM];
        else
          rv += Integer.toString((slotValue == 12) ? 12 : (slotValue - 12)) +
          new java.text.DateFormatSymbols(Sage.userLocale).getAmPmStrings()[java.util.Calendar.PM];
      }
      else
      {
        rv += Integer.toString(slotValue) + ":00";
      }
    }
    return rv;
  }
  public static int[] getTimeslotForString(String slt)
  {
    if (slt == null || slt.length() == 0)
      return new int[] { 0, 0};
    slt = slt.toLowerCase().trim();
    String[] dows = getDOWs();
    for (int i = 0; i < dows.length; i++)
    {
      if (slt.startsWith(dows[i].toLowerCase()))
      {
        if (slt.equalsIgnoreCase(dows[i]))
          return new int[] { DAY_ALIGN, i*24 };
        else
        {
          slt = slt.substring(dows[i].length() + 1);
          int ts;
          int colonIdx = slt.indexOf(':');
          if (slt.endsWith(new java.text.DateFormatSymbols(Sage.userLocale).
              getAmPmStrings()[java.util.Calendar.PM].toLowerCase()))
          {
            ts = Integer.parseInt(slt.substring(0, (colonIdx == -1) ? (slt.length() - 2) : colonIdx));
            if (ts != 12)
              ts += 12;
          }
          else if (slt.endsWith(new java.text.DateFormatSymbols(Sage.userLocale).
              getAmPmStrings()[java.util.Calendar.AM].toLowerCase()))
          {
            ts = Integer.parseInt(slt.substring(0, (colonIdx == -1) ? (slt.length() - 2) : colonIdx));
            if (ts == 12)
              ts = 0;
          }
          else
            ts = Integer.parseInt((colonIdx == -1) ? slt : slt.substring(0, colonIdx));
          return new int[] { FULL_ALIGN, ts + i*24 };
        }
      }
    }
    int ts;
    int colonIdx = slt.indexOf(':');
    if (slt.endsWith(new java.text.DateFormatSymbols(Sage.userLocale).
        getAmPmStrings()[java.util.Calendar.PM].toLowerCase()))
    {
      ts = Integer.parseInt(slt.substring(0, (colonIdx == -1) ? (slt.length() - 2) : colonIdx));
      if (ts != 12)
        ts += 12;
    }
    else if (slt.endsWith(new java.text.DateFormatSymbols(Sage.userLocale).
        getAmPmStrings()[java.util.Calendar.AM].toLowerCase()))
    {
      ts = Integer.parseInt(slt.substring(0, (colonIdx == -1) ? (slt.length() - 2) : colonIdx));
      if (ts == 12)
        ts = 0;
    }
    else
      ts = Integer.parseInt((colonIdx == -1) ? slt : slt.substring(0, colonIdx));
    return new int[] { TIME_ALIGN, ts };
  }

  // Returns the latest time the Airing has been watched through
  public static long getLatestWatch(Airing air)
  {
    if (air == null) return 0;
    Watched ws = Wizard.getInstance().getWatch(air);
    long maxWatch = air.getStartTime();
    if (ws == null) return maxWatch;
    maxWatch = Math.max(maxWatch, ws.getWatchEnd());

    long endTime = air.getSchedulingEnd();
    MediaFile mf = Wizard.getInstance().getFileForAiring(air);
    if (mf != null && !mf.isRecording())
    {
      endTime = Math.min(mf.getRecordEnd(), endTime);
    }
    // Within 15 seconds of the end, start from the beginning unless
    // it's still on which means we're watching the end of it.
    if ((endTime - maxWatch < 15000L) &&
        (air.getSchedulingEnd() < Sage.time() || (air.getMediaMask() & DBObject.MEDIA_MASK_TV) == 0))
      maxWatch = air.getStartTime();
    // We used to always add 3 seconds to the prior watch time when we resume, this was so that
    // when we channel surfed, we wouldn't start right at the end of a segment but we have other
    // mechanisms to protect against that so let's disable this finally
    //else
    //  maxWatch += 3000;

    return maxWatch;
  }

  private static void triggerWatchedChangeEvent(Airing a)
  {
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.WATCHED_STATE_CHANGED,
        new Object[] { sage.plugin.PluginEventManager.VAR_AIRING, a });
    SchedulerSelector.getInstance().kick(true);
  }
  public static void clearWatched(Airing a)
  {
    if (a == null) return;
    if (Sage.client)
    {
      NetworkClient.getSN().setWatch(false, a, 0, 0, 0, 0);
      return;
    }
    if (a.getShow() != null)
    {
      a.getShow().setLastWatched(0);
    }
    Watched prior = Wizard.getInstance().getWatch(a);
    if (prior != null) Wizard.getInstance().removeWatched(prior);
    if (a.isTV())
      Carny.getInstance().submitJob(new Object[] { Carny.WATCH_CLEAR_JOB, a });
    triggerWatchedChangeEvent(a);
  }

  public static boolean setWatched(Airing a)
  {
    return setWatched(a, 0, 0, 0, 0, false);
  }
  public static boolean setWatched(Airing a, long airWatchStart, long airWatchEnd,
      long realWatchStart, long realWatchEnd, boolean checkOnly)
  {
    return setWatched(a, airWatchStart, airWatchEnd, realWatchStart, realWatchEnd, 0, checkOnly);
  }
  static boolean setWatched(Airing a, long airWatchStart, long airWatchEnd,
      long realWatchStart, long realWatchEnd, int titleNum, boolean checkOnly)
  {
    ((byte[])Sage.q)[32] = (byte) (((byte[])Sage.q)[11] * ((byte[])Sage.q)[25]); // piracy protection
    if (a == null) return false;
    // Only track watches for TV & Video Airings & DVDs/BDs
    if (!a.isTV() && !a.isVideo() && !a.isDVD() && !a.isBluRay()) return false;
    Show s = a.getShow();
    if (airWatchStart == 0 && airWatchEnd == 0 && realWatchStart == 0 && realWatchEnd == 0)
    {
      if (checkOnly) return false;

      // This is manual setting of it.
      if (Sage.client)
      {
        NetworkClient.getSN().setWatch(true, a, airWatchStart, airWatchEnd, realWatchStart, realWatchEnd);
      }
      else
      {
        if (s != null)
          s.setLastWatched(a.time);
        Wizard.getInstance().addWatched(a, (a.isDVD() || a.isBluRay()) ? 0 : a.getStartTime(),
            (a.isDVD() || a.isBluRay()) ? a.getDuration() : a.getEndTime(),
                0, 0);
        if (a.isTV())
          Carny.getInstance().submitJob(new Object[] { Carny.WATCH_MARK_JOB, a });
        triggerWatchedChangeEvent(a);
      }
      return true;
    }
    airWatchStart = (a.isDVD() || a.isBluRay()) ? airWatchStart : Math.max(a.time, airWatchStart);
    airWatchEnd = (a.isDVD() || a.isBluRay()) ? airWatchEnd : Math.min(a.getEndTime(), airWatchEnd);
    long watchedDur = airWatchEnd - airWatchStart;
    boolean rv = false;
    Watched priorWatch = Wizard.getInstance().getWatch(a);
    if (watchedDur > MIN_WATCH_TIME)
    {
      // It's not so short we ignore it.
      // See if we want to apply this to the show or not
      // as being completely seen.
      long validDuration = (a.isDVD() && !a.isBluRay()) ? Long.MAX_VALUE : a.duration;
      if (s != null && s.duration > 10000 && s.duration < a.duration && // ignore running times that are in the wrong units
          a.duration - s.duration < MAX_DURATION_DIFF_TO_USE_SHOW)
        validDuration = s.duration;
      if (priorWatch != null) {
        // Include prior watched data in calculating whether or not this new one
        // is a full watch duration or not.
        watchedDur = Math.max(airWatchEnd, priorWatch.getWatchEnd()) - Math.min(airWatchStart, priorWatch.getWatchStart());
      }
      if (validDuration - watchedDur < getWatchIgnoreTime(a) &&
          (((double) watchedDur)/validDuration) > COMPLETE_WATCH_FRACTION)
      {
        // Saw enough of it to have seen the whole thing
        rv = true;
      }

      if (!checkOnly)
      {
        if (Sage.client)
        {
          NetworkClient.getSN().setWatch(true, a, airWatchStart, airWatchEnd, realWatchStart, realWatchEnd, titleNum);
        }
        else
        {
          Wizard.getInstance().addWatched(a, airWatchStart, airWatchEnd,
              realWatchStart, realWatchEnd, titleNum);

          if (rv)
          {
            if (s != null)
              s.setLastWatched(a.time);
            if (a.isTV())
              Carny.getInstance().submitJob(new Object[] { Carny.WATCH_REAL_JOB, a });
          }
          triggerWatchedChangeEvent(a);
        }
      }
    }
    return rv;
  }

  public static boolean isFullWatch(Watched w)
  {
    if (w == null) return false;
    Airing wa = w.getAiring();
    if (wa == null) return false;
    if (wa.isDVD() && !wa.isBluRay())
    {
      // DVDs don't have valid durations so we need to check the explicit watched setting on them
      if (wa.getShow() != null && wa.getShow().isWatched())
        return true;
      else
        return false;
    }
    return (wa.duration - w.getWatchDuration() < getWatchIgnoreTime(wa) &&
        (((double) w.getWatchDuration())/wa.duration) > COMPLETE_WATCH_FRACTION);
  }

  public static long getWatchIgnoreTime(Airing a)
  {
    return (a == null || a.getShow() == null || !a.getShow().isMovie()) ? WATCH_IGNORE_TIME : WATCH_IGNORE_TIME_MOVIE;
  }

  public static boolean areSameShow(Airing a1, Airing a2, boolean matchFavs)
  {
    return areSameShow(a1, a2, matchFavs, null);
  }

  public static boolean areSameShow(Airing a1, Airing a2, boolean matchFavs, StringBuilder sbCache)
  {
    if (a1 != null && a2 != null && a1.getShow() != null && a1.showID == a2.showID)
    {
      if ((isUnique(a1) || a1.time == a2.time) && (!matchFavs ||
          Carny.getInstance().areSameFavorite(a1, a2, sbCache)))
        return true;
      // Check for the Eastern/Pacific channel difference
      Channel c1 = a1.getChannel();
      Channel c2 = a2.getChannel();
      if ((c1 != null && c2 != null && ((c1.name.length() > 1 && c1.name.charAt(c1.name.length() - 1) == 'P') ||
          (c2.name.length() > 1 && c2.name.charAt(c2.name.length() - 1) == 'P')) && Math.abs(a1.time - a2.time) == 3*Sage.MILLIS_PER_HR) &&
          (!matchFavs || Carny.getInstance().areSameFavorite(a1, a2, sbCache)))
        return true;
    }
    return false;
  }

  public static boolean isWatched(Airing air)
  {
    return isWatched(air, false);
  }
  public static boolean isWatched(Airing air, boolean schedulingPurpose)
  {
    if (air == null) return false;
    boolean showTest = false;
    if (air.getShow() != null && isUnique(air))
    {
      showTest = air.getShow().isWatched();
      if (showTest && !schedulingPurpose)
        return true;
    }
      Watched w = Wizard.getInstance().getWatch(air);
    if (w == null && showTest && schedulingPurpose)
      return true; // This is for another copy of this was already watched, so we don't need to compare timing here

    if (!schedulingPurpose)
      return (w != null) && isFullWatch(w);
    else if (w == null)
      return showTest;
    else
    {
      // This is our special case where there's a Watched object for this Airing and we
      // don't want it to prevent scheduling unless it's truly over already...however if this
      // was manually marked watched, then we do allow it to prevent scheduling
      return (w.getRealDuration() == 0 || air.getSchedulingEnd() < Sage.time()) && isFullWatch(w);
    }
  }

  // NARFLEX - 2/16/10 - There's a case for uniqueness that we've always failed on. This relates to multipart content that
  // has a non-EP ID attached to it. For example; a MFTV movie will have the same MV ID for each part of it; but will end up
  // using different part #s for each Airing. We won't end up recording more than one part though because we assume they're all the same
  // content. The solution looks like it would be labelling content like that as non-unique; so then we end up recording all of the parts
  // because we don't think any are the same. The downside of this is that it will re-record them as well; and you can't mark it as Watched.
  // But if we tried to fix those; we'd run into major problems since the Watched tracking links with the Show and each part uses the same Show.
  // So while the downside to to labelling these as unique is re-recording and no watched tracking; the upside is it will actually record
  // them all...so that seems like an obvious choice. We had to add an Airing version of isUnique since the part information is stored in there and
  // not in the Show object.
  public static boolean isUnique(Airing a)
  {
    if (a == null) return true;
    Show s = a.getShow();
    if (s == null) return true;
    if (a.getPartNum() > 0 && !s.isEPExternalID())
    {
      // Correct this error in Tribune's data
      s.cachedUnique = Show.NON_UNIQUE;
      return false;
    }
    return isUnique(s);
  }
  public static boolean advRedCheck = Sage.getBoolean("epg/advanced_show_redundancy_check", false);
  public static boolean isUnique(Show s)
  {
    if (s == null) return true;
    if (advRedCheck)
    {
      if (s.cachedUnique != Show.UNKNOWN_UNIQUE) return (s.cachedUnique == Show.FORCED_UNIQUE) || (s.cachedUnique == Show.PRESUMED_UNIQUE);

      if (Wizard.getInstance().isNoShow(s))
      {
        s.cachedUnique = Show.NON_UNIQUE; return false;
      }
      if (!s.isSHorDTExternalID())
      {
        s.cachedUnique = Show.PRESUMED_UNIQUE; return true;
      }

      if (s.isCategory("movie") || s.isCategory("sports") || s.hasEpisodeName())
      {
        s.cachedUnique = Show.PRESUMED_UNIQUE;
        return true;
      }

      if (Wizard.getInstance().doesShowHaveEPs(s))
      {
        s.cachedUnique = Show.NON_UNIQUE;
        return false;
      }

      Airing[] allAirs = Wizard.getInstance().getAirings(s, 0);
      if (allAirs.length < 2)
      {
        s.cachedUnique = Show.PRESUMED_UNIQUE;
        return true;
      }

      java.util.Set slotBag = new java.util.HashSet();
      for (int i = 0; i < allAirs.length; i++)
      {
        String str = "";
        str += Profiler.determineSlots(allAirs[i])[0];
        str += ":";
        str += allAirs[i].stationID;
        // Multipart SH tagged content should not be considered unique or otherwise
        // we wouldn't record all of the parts if its a Favorite
        if (!slotBag.add(str) || allAirs[i].getPartNum() > 0)
        {
          s.cachedUnique = Show.NON_UNIQUE;
          return false;
        }
      }
      s.cachedUnique = Show.PRESUMED_UNIQUE;
      return true;
    }
    else
    {
      if (s.cachedUnique == Show.FORCED_UNIQUE) return true;
      if (Wizard.getInstance().isNoShow(s))
      {
        return false;
      }
      if (!s.isSHorDTExternalID())
      {
        return true;
      }

      if (s.isCategory("movie") || s.isCategory("sports") || s.hasEpisodeName())
      {
        return true;
      }

      return false;
    }
  }

  static boolean alignsSlot(Airing a, int slotType, int slotValue)
  {
    int[] slotRange = Profiler.determineSlots(a);
    for (int i = slotRange[0]; i <= slotRange[1]; i++)
    {
      if (slotType == FULL_ALIGN)
      {
        if (i == slotValue) return true;
      }
      else if (slotType == DAY_ALIGN)
      {
        if (i/24 == slotValue/24) return true;
      }
      else if (slotType == TIME_ALIGN)
      {
        if (i%24 == slotValue%24) return true;
      }
    }
    return false;
  }

  // This determines whether the passed in Airing is possibly a 'spoiler' and therefore should not be played
  // as part of selecting something to view by default
  // USPTO 8,943,529
  public static boolean isPossibleSpoiler(Airing a)
  {
    if (a == null)
      return false;
    Show s = a.getShow();
    if (s == null)
      return false;
    // There's 2 rules to check here
    // Rule #1 - If this program is a Favorite/MR, and there are other unwatched recorded Favorite/MRs of this show with an
    // original airing date before this one....then this program could be a spoiler. The purpose of this one is to prevent showing
    // the beginning of an episode that is newer than where the viewer is in the series. We also disregard 'News' content as being spoilable.
    if ((Carny.getInstance().isLoveAir(a) || Wizard.getInstance().getManualRecord(a) != null) &&
        (s.getOriginalAirDate() != 0 || s.getSeasonNumber() != 0 || s.getEpisodeNumber() != 0) &&
        !s.getCategory().startsWith("News"))
    {
      DBObject[] rawFiles = Wizard.getInstance().getRawAccess(Wizard.MEDIAFILE_CODE, Wizard.MEDIAFILE_BY_AIRING_ID_CODE);
      for (int i = 0; i < rawFiles.length; i++)
      {
        MediaFile mf = (MediaFile) rawFiles[i];
        if (mf != null && mf.hasMediaMask(DBObject.MEDIA_MASK_TV))
        {
          Show ms = mf.getShow();
          if (ms != null && ms.title == s.title && !mf.getContentAiring().isWatched())
          {
            if (ms.getOriginalAirDate() < s.getOriginalAirDate() ||
                (ms.getOriginalAirDate() == s.getOriginalAirDate() && mf.getContentAiring().getStartTime() < a.getStartTime()) ||
                (ms.getSeasonNumber() != 0 && ms.getEpisodeNumber() != 0 &&
                (ms.getSeasonNumber() < s.getSeasonNumber() ||
                    (ms.getSeasonNumber() == s.getSeasonNumber() && ms.getEpisodeNumber() < s.getEpisodeNumber()))))
            {
              return true;
            }
          }
        }
      }
    }

    // Rule #2 - If a program proceeding this one is an unwatched Favorite/ManualRecord then it could be a spoiler; the time window
    // for checking that is determined by program type....we check 15 minutes in the past for non-sports content, and 2 hours in the past
    // for something w/ the category of "Sports Event"...so we always need to check at least 2 hours prior to see if anything in that
    // window hits this rule. The purpose of this one is to prevent the prior programs ending from bleeding into the next program and showing that ending which
    // would ruin that prior show. We also disregard 'News' content as being spoilable.
    long lookbackSports = a.getStartTime() - 2 * Sage.MILLIS_PER_HR;
    long lookbackAll = a.getStartTime() - 15 * Sage.MILLIS_PER_MIN;
    Airing prior = Wizard.getInstance().getTimeRelativeAiring(a, -1);
    Airing lastPrior = null;
    Stringer sportsCat = Wizard.getInstance().getCategoryForName("Sports event");
    while (prior != null && prior != lastPrior && prior.getEndTime() > lookbackSports)
    {
      Show ps = prior.getShow();
      if (ps != null)
      {
        if ((prior.getEndTime() > lookbackAll && !ps.getCategory().startsWith("News")) || (prior.getEndTime() > lookbackSports &&
            ps.hasCategory(sportsCat)))
        {
          if ((Carny.getInstance().isLoveAir(prior) || Wizard.getInstance().getManualRecord(prior) != null) &&
              !prior.isWatched())
            return true;
        }
      }
      lastPrior = prior;
      prior = Wizard.getInstance().getTimeRelativeAiring(prior, -1);
    }
    return false;
  }
}