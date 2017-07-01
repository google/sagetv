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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

public class Scheduler implements SchedulerInterface
{
  public static final long SCHEDULING_LOOKBEHIND = 180000L;// 3 min
  private static final float FUZZIFIER = 0.05f;//0.1f;
  private static final String SCHEDULER_EXPORT_FILE = "scheduler_export_file";

  private static final boolean EXPONENTIAL_RED = true;
  private static final boolean SDBG = Sage.DBG && "true".equals(Sage.get("scheduler_debug", null));
  private static final boolean GLOB_DEBUG = SDBG;

  private static class SchedulerHolder
  {
    public static final Scheduler instance = new Scheduler();
  }

  public static Scheduler getInstance()
  {
    return SchedulerHolder.instance;
  }

  private Scheduler()
  {
    wiz = Wizard.getInstance();
    god = Carny.getInstance();
    encoderScheduleMap = new HashMap<CaptureDevice, EncoderSchedule>();
    dontScheduleMap = new HashMap<Show, Set<Airing>>();
    scheduleRandoms = new HashMap<Integer, Float>();
    pendingConflicts = new HashMap<DBObject, Vector<Airing>>();
    pendingUnresolvedConflicts = new HashMap<DBObject, Vector<Airing>>();
    pubUnresolvedConflicts = new HashMap<DBObject, Vector<Airing>>();
    pubConflicts = new HashMap<DBObject, Vector<Airing>>();
    allQualsSet = new HashSet<String>();
    chanTunerQualMap = new HashMap<Integer, Integer>();
    //dontKnowConflicts = Collections.synchronizedSet(new HashSet());
    prepped = false;
  }

  public boolean isPrepped() { return prepped; }

  private void addToDontSchedule(Airing addMe)
  {
    //System.out.println("addToDontSchedule(" + addMe + ')');
    if (addMe == null) return;
    Set<Airing> s = dontScheduleMap.get(addMe.getShow());
    if (s == null)
    {
      s = new HashSet<Airing>();
      dontScheduleMap.put(addMe.getShow(), s);
    }
    s.add(addMe);
    if (addMe instanceof ManualRecord.FakeAiring)
    {
      s.add(((ManualRecord.FakeAiring) addMe).getManualRecord().getContentAiring());
    }
    if (addMe instanceof MediaFile.FakeAiring)
    {
      s.add(((MediaFile.FakeAiring) addMe).getMediaFile().getContentAiring());
    }
  }

  private void removeFromDontSchedule(Airing killMe)
  {
    //System.out.println("removeFromDontSchedule(" + killMe + ')');
    if (killMe == null) return;
    Set<Airing> s = dontScheduleMap.get(killMe.getShow());
    if (s != null)
    {
      s.remove(killMe);
      if (s.isEmpty())
      {
        dontScheduleMap.remove(killMe.getShow());
      }
    }
  }

  private void validateEncoderMap()
  {
    synchronized (this)
    {
      if (SageTV.isPoweringDown())
      {
        encoderScheduleMap.clear();
        return;
      }
      Set<CaptureDeviceInput> allInputs = new HashSet<CaptureDeviceInput>(
          Arrays.asList(MMC.getInstance().getConfiguredInputs()));
      Set<CaptureDevice> allDevs = new HashSet<CaptureDevice>();
      Iterator<CaptureDeviceInput> walker = allInputs.iterator();
      allQualsSet.clear();
      while (walker.hasNext())
      {
        CaptureDeviceInput cdi = walker.next();
        if (cdi.getCaptureDevice().isFunctioning() && cdi.getCaptureDevice().canEncode())
        {
          if (!cdi.getCaptureDevice().isLoaded())
          {
            try
            {
              cdi.getCaptureDevice().loadDevice();
              allDevs.add(cdi.getCaptureDevice());
            }
            catch (EncodingException e)
            {
              System.out.println("Schedule skipping encoder " + cdi.getCaptureDevice().getName() +
                  "because it failed to load: " + e);
              sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createDeviceLoadFailureMsg(cdi.getCaptureDevice()));
              walker.remove();
            }
          }
          else
            allDevs.add(cdi.getCaptureDevice());
        }
        else
          walker.remove();
      }
      encoderScheduleMap.keySet().retainAll(allDevs);
      for (EncoderSchedule es : encoderScheduleMap.values())
      {
        es.stationSet.clear();
        es.qualitySet.clear();
      }
      chanTunerQualMap.clear();
      for (CaptureDeviceInput cdi : allInputs)
      {
        CaptureDevice capdev = cdi.getCaptureDevice();
        EncoderSchedule es = encoderScheduleMap.get(capdev);
        if (es == null)
          encoderScheduleMap.put(capdev, es = new EncoderSchedule(capdev));
        es.qualitySet.addAll(Arrays.asList(capdev.getEncodingQualities()));
        allQualsSet.addAll(es.qualitySet);
        int[] allStats = EPG.getInstance().getAllStations(cdi.getProviderID());
        EPGDataSource ds = EPG.getInstance().getSourceForProviderID(cdi.getProviderID());
        if (ds != null)
        {
          int currInputType = cdi.getType();
          Integer currInputTypeInt = currInputType;
          for (int j = 0; j < allStats.length; j++)
          {
            if (ds.canViewStation(allStats[j]))
            {
              int currQualValue = currInputType;
              Integer statInt = allStats[j];
              es.stationSet.add(statInt);
              // NARFLEX: 06/17/08 - Build a cache of the best tuner quality available for each station
              // that's viewable. Then we can use this in the sort for airing preference to prefer
              // HDTV tuners over SDTV tuners even if the show isn't marked as HDTV
              Integer maxTunerType = chanTunerQualMap.get(statInt);
              if (maxTunerType == null || maxTunerType < currQualValue)
                chanTunerQualMap.put(statInt,
                    (currQualValue == currInputType) ? currInputTypeInt : currQualValue);
            }
          }
        }
      }
    }
  }

  public synchronized long getNextMustSeeTime()
  {
    // If we're going into standby then the capture devices are going to all get unloaded so this might be empty!
    if (SageTV.isPoweringDown())
      return lastNextMustSeeTime;
    long nextStart = Long.MAX_VALUE;
    for(EncoderSchedule es : encoderScheduleMap.values())
    {
      if (es.pubMustSee != null && es.pubMustSee.size() > 0)
      {
        nextStart = Math.min((es.pubMustSee.firstElement()).getSchedulingStart(), nextStart);
      }
    }
    return lastNextMustSeeTime = ((nextStart == Long.MAX_VALUE) ? 0 : nextStart);
  }

  public void run()
  {
    if (Sage.client) return;

    schedulerThread = Thread.currentThread();
    alive = true;

    validateEncoderMap();
    updateSchedule(Sage.time());

    if (Sage.get(SCHEDULER_EXPORT_FILE, "").length() > 0)
    {
      exportSchedule(new File(Sage.get(SCHEDULER_EXPORT_FILE, "")));
    }

    while (alive)
    {
      try
      {
        // We want to figure out what conflicts ended up not being recorded. We know this ahead of
        // time of course; but we want to know when its been really commited and is lost so we can
        // post a system message about it.
        // We can detect this by finding out what items have been removed from the conflict set since
        // our last run through. And if any of those items are still an MR or Favorite and don't have
        // a MediaFile and have a start time in the past more than the lookbehind; and if its a Favorite
        // doesn't have a later airing of this Favorite.
        Map<DBObject, Vector<Airing>> oldConflictMap = new HashMap<DBObject, Vector<Airing>>();
        oldConflictMap.putAll(pubConflicts);
        synchronized (this)
        {
          for (EncoderSchedule es : encoderScheduleMap.values())
          {
            es.pubSchedule = new Vector<Airing>(es.schedule);
            es.pubMustSee = new Vector<Airing>(es.mustSee);
          }
          pubConflicts = new HashMap<DBObject, Vector<Airing>>(pendingConflicts);
          Map<DBObject, Vector<Airing>> currentUnresolved =
              new HashMap<DBObject, Vector<Airing>>(pendingUnresolvedConflicts);
          currentUnresolved.keySet().removeAll(pubUnresolvedConflicts.keySet());
          pubUnresolvedConflicts =
              new HashMap<DBObject, Vector<Airing>>(pendingUnresolvedConflicts);
          if (!currentUnresolved.isEmpty())
          {
            Catbert.distributeHookToAll("NewUnresolvedSchedulingConflicts", null);
          }
          if (!oldConflictMap.keySet().equals(pubConflicts.keySet()))
          {
            sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.CONFLICT_STATUS_CHANGED, (Object[]) null);
          }
        }
        // What's left in the oldConflictMap should now be only shows that were previously in there; but
        // now are no longer in there.
        oldConflictMap.keySet().removeAll(pubConflicts.keySet());
        if (!oldConflictMap.isEmpty())
        {
          for (Map.Entry<DBObject, Vector<Airing>> currEnt : oldConflictMap.entrySet())
          {
            Vector<Airing> airVec = currEnt.getValue();
            if (Sage.DBG) System.out.println("Airing was dropped from conflict map; check for a missed recording: " + airVec);
            Airing currAir = airVec.get(0);
            if (wiz.getFileForAiring(currAir) == null && (wiz.getManualRecord(currAir) != null ||
                (god.isLoveAir(currAir) && !BigBrother.isWatched(currAir))) &&
                currAir.getSchedulingStart() <= Sage.time() - SCHEDULING_LOOKBEHIND)
            {
              if (Sage.DBG) System.out.println("Found missed recording for " + currAir + " post system message about it");
              sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createMissedRecordingFromConflictMsg(currAir, currAir.getChannel()));
            }
          }
        }

        /*
         * NOTE: I moved this on 9/18/02. It was before the block that published the schedule,
         * but that was wrong because then the Seeker could get woken up and execute on its
         * thread and take the schedule from us before we published it. I also had to break
         * the wait command to be after it since I didn't want to next sync blocks (deadlock baby).
         */
        Seeker.getInstance().kick();
        if (!kicked && Sage.get(SCHEDULER_EXPORT_FILE, "").length() > 0)
        {
          exportSchedule(new File(Sage.get(SCHEDULER_EXPORT_FILE, "")));
        }
        // If the wakeup time changes, kick the PM
        ServerPowerManagement.getInstance().softkick();
        sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.RECORDING_SCHEDULE_CHANGED, (Object[]) null);

        synchronized (this)
        {
          if (!kicked)
          {
            if (Sage.DBG) System.out.println("Scheduler starting wait...");
            try { wait(); }catch(InterruptedException e){}
          }
          kicked = false;
        }
        if (!alive) break;

        if (Sage.DBG) System.out.println("Scheduler awoken");
        validateEncoderMap();
        for (Map.Entry<CaptureDevice, EncoderSchedule> ent : encoderScheduleMap.entrySet())
        {
          MediaFile mf = Seeker.getInstance().getCurrRecordMediaFile(ent.getKey());
          EncoderSchedule es = ent.getValue();
          es.currRecord = (mf == null) ? null : mf.getContentAiring();
          // For the airing content after the scheduling time for favs/mrs
          boolean expiredAir = false;
          if (es.currRecord != null)
          {
            ManualRecord mr = wiz.getManualRecord(es.currRecord);
            if (mr != null)
            {
              // NOTE: This deals with the airing content after the MR stop time
              if (mr.getEndTime() > Sage.time())
                es.currRecord = mr.getSchedulingAiring();
            }
          }

          // Don't include the current record if it's not started yet or is over already unless
          // we're in the stop short time area
          if (es.currRecord != null)
          {
            if (es.currRecord.getSchedulingStart() > Sage.time())
              es.currRecord = null;
            else if (es.currRecord.getSchedulingEnd() <= Sage.time())
            {
              if (es.currRecord.getEndTime() > Sage.time())
                expiredAir = true;
              else
                es.currRecord = null;
            }
          }
          es.isForced = (es.currRecord != null && !expiredAir && (es.currRecord.isMustSee() ||
              wiz.getManualRecord(es.currRecord) != null));

          es.isDesired = Seeker.getInstance().isAClientControllingCaptureDevice(es.capDev);
        }

        // Process scheduling information. When this returns we then deal
        // with the current record issue.
        updateSchedule(Sage.time());
        if (!prepped) // once we're prepped, stay that way
          prepped = (god.isPrepped());
      }
      catch (Throwable throwy)
      {
        if (Sage.DBG) System.out.println("Scheduler EXCEPTION THROWN:" + throwy);
        throwy.printStackTrace();
      }
    }
  }

  public void iKnowNow()
  {
    if (Sage.client)
    {
      //SageTV.serverNotifier.askMeQuestions();
    }
    else
    {
      synchronized (this)
      {
        //        dontKnowConflicts.clear();
        kick(false);
      }
    }
  }

  public void setClientDontKnowFlag(boolean x) { clientDontKnowFlag = x; }

  public boolean areThereDontKnows()
  {
    if (Sage.client)
      return clientDontKnowFlag;
    else
      return !pubUnresolvedConflicts.isEmpty();
  }
  private boolean pendingKick=false;

  public void kick(boolean delay)
  {
    if (delay) {
      // check for any delayed kicks already queued
      synchronized (this) {
        if (pendingKick) {
          return;
        }
        pendingKick = true;
      }
      Pooler.execute(new Runnable() {
        public void run() {
          try {
            Thread.sleep(250);
          } catch (Exception e) {}
          synchronized (Scheduler.this) {
            Scheduler.this.notifyAll();
            kicked = true;
            pendingKick = false;
          }
        }
      });
    }
    else
    {
      synchronized (this)
      {
        notifyAll();
        kicked = true;
      }
    }
  }

  public synchronized void prepareForStandby()
  {
    kick(false);
    while (SageTV.poweringDown && !encoderScheduleMap.isEmpty())
    {
      try
      {
        wait(100);
      }
      catch (Exception e)
      {}
    }
  }

  public synchronized Map<DBObject, Vector<Airing>> getUnresolvedConflictsMap()
  {
    return pubUnresolvedConflicts;
  }

  public synchronized Map<DBObject, Vector<Airing>> getConflictsMap()
  {
    return pubConflicts;
  }

  public synchronized CaptureDevice[] getMyEncoders()
  {
    return encoderScheduleMap.keySet().toArray(new CaptureDevice[0]);
  }

  public synchronized String[] getMyEncoderNames()
  {
    List<String> rv = new ArrayList<String>();
    for (CaptureDevice device : encoderScheduleMap.keySet())
      rv.add(device.toString());
    return rv.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public synchronized Vector<Airing> getSchedule(CaptureDevice capDev)
  {
    EncoderSchedule es = encoderScheduleMap.get(capDev);
    return es == null ? new Vector<Airing>() : es.pubSchedule;
  }

  public synchronized Vector<Airing> getMustSee(CaptureDevice capDev)
  {
    EncoderSchedule es = encoderScheduleMap.get(capDev);
    return es == null ? new Vector<Airing>() : es.pubMustSee;
  }

  public void goodbye()
  {
    alive = false;
    synchronized (this)
    {
      notifyAll();
    }
    if (schedulerThread != null)
      try{schedulerThread.join(30000);}catch(InterruptedException e){}
  }

  // This does NOT care about conflicts
  // It DOES care about what's currently in all of the schedules (or the same as something
  // in the schedule), that the airing isn't over yet, that the dont schedule map
  // doesn't have any that are the same and that the WP is non-zero.
  private boolean okToSchedule(Airing testMe, long currTime)
  {
    if (getSchedulingEnd(testMe) <= currTime || god.getWP(testMe) <= 0 || testMe.isDontLike())
      return false;
    for (EncoderSchedule es : encoderScheduleMap.values())
    {
      if ((es.currRecord != null) && BigBrother.areSameShow(testMe, es.currRecord, true)) return false;
      for (int i = 0; i < es.schedule.size(); i++)
      {
        if (BigBrother.areSameShow(es.schedule.elementAt(i), testMe, true))
          return false;
      }
    }

    Set<Airing> s = dontScheduleMap.get(testMe.getShow());
    if (s != null)
    {
      for (Airing air : s)
      {
        if (BigBrother.areSameShow(air, testMe, true))
          return false;
      }
    }
    return true; // moved to top: god.getWP(testMe) > 0;
  }

  private int countScheduleRedundancy(Airing testMe)
  {
    if (Sage.getBoolean("scheduler/red_cache", true))
    {
      // NOTE: The red cache doesn't stay valid as we insert the potentials. But we accept it
      // as a 'quality' loss for the performance gain.
      long currTime = Sage.time();
      boolean futuristic = testMe.getStartTime() > currTime;
      int count = 0;
      Agent testMeAgent = god.getCauseAgent(testMe);
      if (testMeAgent == null) return 0;
      List<Airing> list = agentFileRedMap.get(testMeAgent);
      if (list != null)
      {
        for (Airing a : list)
        {
          if (a != testMe && (futuristic || a.time > testMe.time))
          {
            count++;
          }
        }
      }
      if (wiz.getFileForAiring(testMe) != null)
        return count;
      list = agentSchedRedMap.get(testMeAgent);
      if (list != null)
      {
        for (Airing a : list)
        {
          if (a == testMe || a.time > testMe.time)
            break;
          count++;
        }
      }
      return count;
    }
    else
    {
      /*
       * We want to distribute this count over each of the
       * redundant airings. The count increases going backwards
       * in time from the present and then forward from the
       * present.
       */
      long currTime = Sage.time();
      boolean futuristic = testMe.getStartTime() > currTime;
      int count = 0;
      boolean isFile = false;
      Agent testMeAgent = god.getCauseAgent(testMe);
      for (int i = 0; i < wizFileCache.length; i++)
      {
        if (wizFileCache[i] == null) continue;
        //if (!wizFileCache[i].isCompleteRecording()) continue;
        //if (wizFileCache[i].archive) continue;
        Airing fileAir = wizFileCache[i].getContentAiring();
        if (fileAir == null) continue;
        if (fileAir == testMe) isFile = true;
        if (fileAir != testMe &&
            !BigBrother.isWatched(fileAir) &&
            god.getCauseAgent(fileAir) == testMeAgent &&
            (futuristic || fileAir.time > testMe.time))
          count++;
      }
      // Files don't get affected by the schedule, only other files
      if (isFile) return count;
      for(EncoderSchedule es : encoderScheduleMap.values())
      {
        if (es.currRecord != null && es.currRecord != testMe &&
            god.getCauseAgent(es.currRecord) == testMeAgent)
          count++;
        int i = 0;
        if (!es.schedule.isEmpty() && es.schedule.firstElement() == es.currRecord) i++;
        for (; i < es.schedule.size(); i++)
        {
          Airing schedAir = es.schedule.elementAt(i);
          if (schedAir == testMe || schedAir.time > testMe.time) break;
          if (god.getCauseAgent(schedAir) == testMeAgent)
            count++;
        }
      }
      return count;
    }
  }

  /*
   * Goes through the passed in scheduling vector and inserts the passed in Airing
   * at the position of increasing time. If it overlapped with any other airings,
   * these will be returned in a vector and removed from the passed in vector.
   * Appropriate modifications to the dontScheduleMap are also made in here.
   */
  // This takes the 0-based numRedos and returns the scalar to multiply
  // the watch by.
  private float redScaler(float baseWatch, Airing theAir)
  {
    int numRedos = countScheduleRedundancy(theAir);
    if (EXPONENTIAL_RED)
      return (numRedos == 0) ? baseWatch : (float)Math.pow(baseWatch, numRedos + 1);
    else
      return (numRedos <= 1) ? 1 : 1/((numRedos - 1)*2.0f);
  }

  private void processMustSeeForAirs(Airing mustAir, long desiredStartLookTime,
      Map<DBObject, Vector<Airing>> showSetMap)
  {
    Vector<Airing> v = new Vector<Airing>();
    if (BigBrother.isUnique(mustAir))
    {
      Airing[] kitty = mustAir.getNextWatchableAirings(desiredStartLookTime);
      // NOTE: Do this sort here so we can prefer HD airings over non-HD airings of shows.
      Arrays.sort(kitty, airingHDSDComparator);

      // We need to check the future airings if they're must sees because of
      // channel restricted favorites.
      if (getSchedulingStart(mustAir) < desiredStartLookTime)
      {
        if (kitty.length == 0)
        {
          // It's a must see that's not on again, take it (done later)
        }
        else
        {
          for (int p = 0; p < kitty.length; p++)
          {
            // 2-2-05 Added the check for wasted so we don't schedule
            // things that are marked as don't like
            // 5-26-17 JS: Removed must see requirement because it can cause
            // us to have a conflict even when a future airing makes the
            // outcome incorrect.
            if (getSchedulingStart(kitty[p]) >= desiredStartLookTime &&
                god.areSameFavorite(kitty[p], mustAir) &&
                wiz.getWastedForAiring(kitty[p]) == null /*&&
                god.isMustSee(kitty[p])*/)
              v.add(kitty[p]);
          }
        }
      }
      else
      {
        //v.addElement(mustAir);
        for (int p = 0; p < kitty.length; p++)
        {
          // 2-2-05 Added the check for wasted so we don't schedule
          // things that are marked as don't like
          // 5-26-17 JS: Removed must see requirement because it can cause
          // us to have a conflict even when a future airing makes the
          // outcome incorrect.
          if (god.areSameFavorite(kitty[p], mustAir) &&
              wiz.getWastedForAiring(kitty[p]) == null /*&&
              god.isMustSee(kitty[p])*/)
            v.add(kitty[p]);
        }
      }
      if (v.isEmpty())
        v.add(mustAir);
      else
      {
        // NARFLEX - 08/30/10 - There's the issue with the scheduling lookbehind of 3 minutes where we can end up losing
        // the beginning of a Favorite recording because it decides to record one now that's already a few minutes in even
        // though it could just record the whole thing later. The easy solution to this is to just move any Airings to the end
        // of the Vector that have already started. That way we'll prefer complete airings over partial airings with hopefully
        // no negative side effects.
        int numMoves = 0;
        for (int i = 0; i < v.size() - numMoves; i++)
        {
          Airing a = v.get(i);
          if (getSchedulingStart(a) < Sage.time())
          {
            v.remove(i);
            v.add(a);
            i--;
            numMoves++;
          }
          else
            break;
        }
      }
      showSetMap.put(mustAir.getShow(), v);
    }
    else if (getSchedulingStart(mustAir) >= desiredStartLookTime)
    {
      v.addElement(mustAir);
      showSetMap.put(mustAir, v);
    }
    //System.out.println("Must See " + mustAir + " options=" + v);
  }

  private void updateSchedule(long currTime)
  {
    /*
     * NOTE: I REALLY SHOULD GET A LIST OF THE MRS AND THEN JUST REUSE THEM, ITS VERY DANGEROUS
     * TO QUERY THE WIZ ABOUT IT EACH TIME BECAUSE IT COULD CHANGE DURING THIS PROCESS AND WHO
     * KNOWS WHAT KIND OF EFFECT THAT COULD HAVE ON THE SCHEDULE...OF COURSE IT'LL JUST RE-EXECUTE
     * AGAIN IF THERE WERE CHANGES, SO IT'LL FIX ITSELF IN THE PROCESS
     */
    cachedSchedStarts.clear();
    cachedSchedEnds.clear();
    long schedUpdateStartTime = Sage.eventTime();
    if (Sage.DBG) System.out.println("Scheduler.updateSchedule() called " + (
      (" manual=" + Arrays.asList(wiz.getManualRecords()) + " schedules=" + encoderScheduleMap +
          " scheduleRandSize=" + scheduleRandoms.size())));

    /*
     * Manual Record scheduling maintenance.
     * 1. Make sure all MRs have their recurrences created for the next week
     * 2. Remove any MRs that are over and don't have a file
     */
    List<ManualRecord> allMRs = new ArrayList<ManualRecord>(Arrays.asList(wiz.getManualRecords()));
    for (int i = 0; i < allMRs.size(); i++)
    {
      ManualRecord thisMR = allMRs.get(i);
      if (thisMR == null) continue;
      if (thisMR.recur != 0)
      {
        // If this is an older MR it's start time might be more than a week in the past so
        // be sure we check all the way into a week into the future
        long[] allRecurs = ManualRecord.getRecurringStartTimes(thisMR.startTime, thisMR.duration, thisMR.recur,
            Math.max(Sage.MILLIS_PER_WEEK, (Sage.time() + Sage.MILLIS_PER_WEEK) - thisMR.startTime));
        for (int j = 0; j < allRecurs.length; j++)
        {
          // Check to see if its recurrence already exists
          boolean foundRecur = false;
          for (int k = 0; k < allMRs.size(); k++)
          {
            ManualRecord testMR = allMRs.get(k);
            if (testMR == null)
              continue;
            if (testMR.startTime == allRecurs[j] && testMR.duration == thisMR.duration &&
                testMR.stationID == thisMR.stationID)
            {
              foundRecur = true;
              // NOTE: Narflex 5/22/06 - Nulling this out so we don't duplicate work is a nice idea,
              // but the problem is that we won't intersect with MRs that are already expired. Then they'll still
              // be in there and cause duplicates to be created. The easiest fix for that is to just disable this line.
              //allMRs[k] = null;
              break;
            }
          }
          if (!foundRecur)
          {
            if (Sage.DBG) System.out.println("Scheduler created recurrence from:" + thisMR);
            ManualRecord newMR = null;
            allMRs.add(newMR = wiz.addManualRecord(allRecurs[j], thisMR.duration, thisMR.providerID,
                thisMR.stationID, "", thisMR.getRecordingQuality(), 0, thisMR.recur));
            newMR.setProperties(thisMR.mrProps);
          }
        }
      }
      if (thisMR.getEndTime() <= currTime && wiz.getFileForAiring(thisMR.getContentAiring()) == null)
      {
        if (Sage.DBG) System.out.println("Scheduler removing manual record because its expired: " + thisMR);
        wiz.removeManualRecord(thisMR);
      }
    }
    if (encoderScheduleMap.isEmpty())
      return;

    /*
     * Go through the SCHEDULE and remove any entries that are over or that
     * have been removed from the database.
     * Also remove them from the MUSTSEE if they were entries in that list.
     */
    for (EncoderSchedule es : encoderScheduleMap.values())
    {
      Airing lastAir = null;
      for (int i = 0; i < es.schedule.size(); i++)
      {
        Airing testAir = es.schedule.elementAt(i);
        if ((getSchedulingEnd(testAir) <= currTime) || !wiz.ok(testAir))
        {
          if (Sage.DBG) System.out.println("Removing from schedule cause expired " + testAir);
          es.schedule.removeElementAt(i--);
          es.mustSee.remove(testAir);
        }
        else if (!es.stationSet.contains(testAir.stationID))
        {
          if (Sage.DBG) System.out.println("Removing from schedule because this encoder no longer receives this channel: " + testAir);
          es.schedule.removeElementAt(i--);
          es.mustSee.remove(testAir);
        }
        else
        {
          if (lastAir != null && doesSchedulingOverlap(lastAir, testAir))
          {
            if (Sage.DBG) System.out.println("Removing from schedule cause overlapped " + testAir);
            es.schedule.removeElementAt(i--);
            es.mustSee.remove(testAir);
          }
          else
            lastAir = testAir;
        }
      }
    }

    // Rebuild the don't schedule map.
    dontScheduleMap.clear();
    for (EncoderSchedule es : encoderScheduleMap.values())
    {
      for (int i = 0; i < es.schedule.size(); i++)
        addToDontSchedule(es.schedule.elementAt(i));
    }
    wizFileCache = wiz.getFiles();
    for (int i = 0; i < wizFileCache.length; i++)
    {
      if (wizFileCache[i].isTV() && wizFileCache[i].isCompleteRecording() &&
          getSchedulingEnd(wizFileCache[i].getContentAiring()) <= currTime)
      {
        addToDontSchedule(wizFileCache[i].getContentAiring());
        // We don't want to re-record archive files so put them in dont schedule, but we also
        // don't want to count them in redundancy, so clear it from the cache list
        if (wizFileCache[i].archive)
          wizFileCache[i] = null;
      }
      else
        wizFileCache[i] = null; // we don't care about this file
    }

    PotVec potentials = new PotVec();

    // Clear these before we do the MR permutation
    pendingConflicts.clear();
    pendingUnresolvedConflicts.clear();

    /*
     * This used to remove old must sees from the mustsee schedule, but that's done above. It
     * also did the thisIsComplete() cleanup work on the MR-based files. That needs to be done
     * at somepoint....guess its OK to do here.
     */
    ManualRecord[] manualMustSee = wiz.getManualRecordsSortedByTime();

    Vector<Airing> mrList = new Vector<Airing>();
    for (int i = 0; i < manualMustSee.length; i++)
    {
      ManualRecord testRec = manualMustSee[i];
      if (testRec.getEndTime() <= currTime)
      {
        // Ensure forced completion of any files that were marked as manual records
        MediaFile mf = wiz.getFileForAiring(testRec.getContentAiring());
        if (mf != null)
          mf.thisIsComplete();
      }
      else if (wiz.isManualRecordOK(testRec))
      {
        /*
         * 10/3/06 - Check to make sure there's an encoder that can receive this station
         * or we're just adding complexity to these calculations that won't be resolved
         */
        boolean foundStation = false;
        for (EncoderSchedule es : encoderScheduleMap.values())
        {
          if (es.stationSet.contains(testRec.stationID))
          {
            foundStation = true;
            break;
          }
        }
        if (foundStation)
        {
          mrList.add(testRec.getSchedulingAiring());
        }
        else
        {
          Vector<Airing> lostAirVec = new Vector<Airing>();
          lostAirVec.add(testRec.getSchedulingAiring());
          pendingUnresolvedConflicts.put(testRec.getSchedulingAiring(), lostAirVec);
          pendingConflicts.put(testRec.getSchedulingAiring(), lostAirVec);
          if (Sage.DBG) System.out.println("Scheduler dropping ManualRecord because station is not received:" + testRec);
        }
      }
    }

    long desiredStartLookTime = currTime - SCHEDULING_LOOKBEHIND;
    lookaheadAirs = god.getPots();
    if (Sage.DBG) System.out.println("# Airs=" + lookaheadAirs.length);

    /*
     * HIGHEST PRIORITY for the schedule are the manually selected airings.
     * The list of manual must sees is maintained to have no overlaps. They
     * all are forced into the schedules here since they must be recorded.
     * The must see and the schedule are the relevant schedules.
     */
    Map<DBObject, Vector<Airing>> showSetMap = new HashMap<DBObject, Vector<Airing>>();
    Vector<EncAir> forcedEncodings = new Vector<EncAir>();
    Vector<Airing> forcedEncodingAirs = new Vector<Airing>();
    for (EncoderSchedule es : encoderScheduleMap.values())
    {
      if (es.currRecord != null && es.isForced)
      {
        forcedEncodings.add(new EncAir(es.currRecord, es.capDev, true));
        forcedEncodingAirs.add(es.currRecord);
        // Be sure they're scheduled
        es.forceIntoMustSee(es.currRecord);
        // Remove any forcedEncodings from the mrList so we don't have duplicates
        for (int i = 0; i < mrList.size(); i++)
        {
          Airing mrAir = mrList.elementAt(i);
          if (mrAir.id == es.currRecord.id ||
              ((mrAir instanceof ManualRecord.FakeAiring) &&
                  ((ManualRecord.FakeAiring)mrAir).getManualRecord().infoAiringID == es.currRecord.id))
            mrList.removeElementAt(i--);
        }
      }
    }

    // Create a list of the ES's thats sorted by merit
    CaptureDevice[] sortedEncNames = encoderScheduleMap.keySet().toArray(new CaptureDevice[0]);
    Arrays.sort(sortedEncNames, CaptureDevice.captureDeviceSorter);
    EncoderSchedule[] sortedEncs = new EncoderSchedule[sortedEncNames.length];
    for (int i = 0; i < sortedEncNames.length; i++)
      sortedEncs[i] = encoderScheduleMap.get(sortedEncNames[i]);

    if (SDBG) System.out.println("mrList=" + mrList + " forcedEncodings=" + forcedEncodings);
    Vector<EncAir> scheduledMRs = generateSingleMultiTunerSchedulingPermutation(mrList, sortedEncs, forcedEncodings);
    //quickMultiTunerSchedule(mrList, forcedEncodings);
    if (SDBG) System.out.println("scheduledMRs=" + scheduledMRs);

    // Our preferred schedule is maintained in the EncoderSchedule objects, what else would
    // they be used for?
    for (int i = 0; i < scheduledMRs.size(); i++)
    {
      EncAir currMR = scheduledMRs.elementAt(i);
      EncoderSchedule es = encoderScheduleMap.get(currMR.capDev);
      if (es != null)
        es.forceIntoMustSee(currMR.air);
    }

    /*
     * Remove any entries from the MUSTSEE that are not contained in the
     * MANUALMUSTSEE or are not considered must sees by the profiler, or are dead.
     *
     * 11/21/02 I changed this to just clear them all out and redo them since
     * that's why we're having problems with our scheduling algorithm. MustSees that
     * are already scheduled aren't having their alternatives checked against a new
     * must see to see if they can resolve a conflict automatically. In order to put
     * all of the must sees back into the conflict resolution pool this is required.
     */
    // This is to ensure we don't put an MR on more than on Encoder
    Set<ManualRecord> alreadyScheduledMRs = new HashSet<ManualRecord>();
    // We go through the must sees and clear everything out that isn't forced or a manual
    for (EncoderSchedule es : encoderScheduleMap.values())
    {
      for (int i = 0; i < es.mustSee.size(); i++)
      {
        Airing testAir = es.mustSee.elementAt(i);

        ManualRecord mr = wiz.getManualRecord(testAir);
        if (!forcedEncodingAirs.contains(testAir) && !mrList.contains(testAir))
        {
          removeFromDontSchedule(es.mustSee.remove(i--));
          es.schedule.remove(testAir);
        }
        else if (forcedEncodingAirs.contains(testAir) && es.currRecord != testAir)
        {
          // This airing is forced on another tuner but in our schedule, GET IT OUT!
          es.mustSee.remove(i--);
          es.schedule.remove(testAir);
        }
        else if (mr != null)
        {
          if (!alreadyScheduledMRs.add(mr))
          {
            // already scheduled as an MR, remove it
            es.mustSee.remove(i--);
            es.schedule.remove(testAir);
          }
        }
      }
    }

    /*
     * Remove all of the must sees from the schedules that are not in the must see schedule.
     * This is a result of must see status getting changed while something is already in
     * the schedule.
     */
    for (EncoderSchedule es : encoderScheduleMap.values())
    {
      for (int i = 0; i < es.schedule.size(); i++)
      {
        Airing currAir = es.schedule.elementAt(i);
        ManualRecord mr = wiz.getManualRecord(currAir);
        if ((currAir.isMustSee() || (mr != null && mr.getEndTime() > currTime)) &&
            !es.mustSee.contains(currAir))
        {
          removeFromDontSchedule(currAir);
          es.schedule.removeElementAt(i);
          i--;
        }
      }
    }
    potentials.clear();
    boolean irEnabled = true;

    for (int i = 0; i < lookaheadAirs.length; i++)
    {
      // The wiz.ok check is so we don't get airings from the Profiler that have since
      // been removed from the DB due to an EPG update
      if (!wiz.ok(lookaheadAirs[i]) || !okToSchedule(lookaheadAirs[i], currTime))
        continue;
      // If we've already put this Show into the must see map then continue on.
      if (showSetMap.containsKey(lookaheadAirs[i].getShow()))
        continue;

      if (lookaheadAirs[i].isMustSee())
      {
        processMustSeeForAirs(lookaheadAirs[i], desiredStartLookTime, showSetMap);
      }
      else if (irEnabled && getSchedulingStart(lookaheadAirs[i]) >= desiredStartLookTime)
        potentials.addElement(lookaheadAirs[i]);
    }

    /*
     * NEW SCHEDULING TECHNIQUE FOR 8/7/03
     *
     * 1. We've already went through the mrList and generated the schedule for it.
     *    Since all the MRs are immutable wrt time, then we don't need to maintain a list
     *    of options for each entry. The MRs may still be encoder swapped, but this is something
     *    that gets evaluated on a case-by-case basis.
     * 2. Now we need to create the ordered list of Favorites. Each element we need to process
     *    is one of the keys in the showSetMap. The first thing we need to do is sort this list
     *    based on priority. To do that, we need to get the Agent representation of this list.
     * 3. As we go through the list of Agents and evaluate the airings for each one, we will
     *    modify the showSetMap in place to reflect the options for each entry in the preferred
     *    schedule. Anything that has only one option will be removed from the showSetMap for
     *    simplicity.  We also build up the list of unresolved conflicts as we go through here,
     *    this is done just like before; when we get to a point where we can't schedule something
     *    then we find out whats causing it and if its not a user set priority, then we prompt
     *    them about the problem.
     */
    Set<Airing> allFavAirs = new HashSet<Airing>();
    if (SDBG) System.out.println("SCHED showSetMap=" + showSetMap);
    Map<Agent, Vector<DBObject>> agentMap = new HashMap<Agent, Vector<DBObject>>();
    for (Map.Entry<DBObject, Vector<Airing>> currEnt : showSetMap.entrySet())
    {
      Vector<Airing> nextAirVec = currEnt.getValue();
      allFavAirs.addAll(nextAirVec);
      Agent currAgent = god.getCauseAgent((nextAirVec.firstElement()));
      if (currAgent != null)
      {
        Vector<DBObject> agentVec = agentMap.get(currAgent);
        if (agentVec == null)
        {
          agentVec = new Vector<DBObject>();
          agentMap.put(currAgent, agentVec);
        }
        agentVec.add(currEnt.getKey());
        agentMap.put(currAgent, agentVec);
      }
    }
    if (SDBG) System.out.println("SCHED agentMap=" + agentMap);

    Object[] sortedAgents = god.sortAgentsByPriority(agentMap.keySet().toArray());

    if (SDBG) System.out.println("SCHED sortedAgents=" + Arrays.asList(sortedAgents));

    // Set the vars for when we timeout on the iterative scheduling
    // conflict_resolution_search_depth - Give up after trying this many permutations
    int conflict_resolution_search_depth = Sage.getInt("scheduler/limiter_conflict_resolution_search_depth", 5000);
    // conflict_resolution_search_time - give up after this timeout if at least conflict_resolution_search_min_for_timeout permutations were checked
    long conflict_resolution_search_time = Sage.getLong("scheduler/limiter_conflict_resolution_search_time", 1000); // WAS Sage.EMBEDDED ? 1000 : 2500
    int conflict_resolution_search_min_for_timeout = Sage.getInt("scheduler/limiter_conflict_resolution_search_min_for_timeout", 500); // WAS Sage.EMBEDDED ? 40 : 500
    // if there are insane_permute_count permutations to check then don't even bother and just give up
    float insane_permute_count = Sage.getFloat("scheduler/limiter_insane_permute_count", 10000);

    // NOTE: 04/06/2012 - After some very extensive evaluation of why super complex schedules with lots of conflicts take forever
    // there were a few new rules which increased the speed of the scheduler by multiple orders of magnitude. There were two spots
    // that really sucked up all the time; the one we always knew about was in the iterator of the permutations. But previously; we just
    // terminated after a certain number were evaluated....but as the permutations got more complex; it would take longer to check each individual one.
    // So if for example you had 16 possibilities; you might be able to check each one in 10 msec. But if you have 10e100 possibilities; it might
    // take half a second to evaluate each one. So using any kind of fixed count for this was a bad idea.  After some analysis; I found that pretty
    // much anything over 10000 permutations really had no chance of finding a solution (and if it could; that is such a miniscule case it doesn't
    // matter).  Then I also found that the code that would generate the web of conflicts was using lots of CPU as well. So now we cache the most
    // complex part of that calculation which is the scheduling times. We also added a check so that if the permute count reaches a certain level (10000)
    // then we don't even bother to try any permutations. So the conflict web checker also can bail out early if it has created a web that reaches that
    // level of complexity.  Having done all of this; an example that was taking hours to calculate will now finish in 4 seconds. About 3 orders of magnitude
    // improvement...and what's better is it's not linear like that...if they get even more complex; they'll still terminate quickly.

    // forcedEncodings tracks forcedEncodings
    // scheduledMRs tracks manual recordings, and encoder swaps are done in place on that list
    // NOTE: We'll need to sync the output of the multi tuner scheduling with the encoder schedule maps
    // after we're all done figuring out what the multi tuner schedule should be
    Vector<EncAir> mutableSchedule = new Vector<EncAir>();
    Vector<EncAir> immutableSchedule = new Vector<EncAir>();
    immutableSchedule.addAll(scheduledMRs);
    for (int i = 0; i < sortedAgents.length; i++)
    {
      Agent currAgent = (Agent) sortedAgents[i];
      Vector<? extends DBObject> agentVec = agentMap.get(currAgent);
      // The order we process an Agent's vector in is random currently, if we did it in
      // time order, there could be some benefit, but only if the title has shows conflicting with itself
      if (SDBG) System.out.println("agentVec=" + agentVec);
      // If these are all Airings in the Vec; then sort them by the rules we use for HD/SD airing priority for episodic content
      boolean allAirs = true;
      for (int j = 0; j < agentVec.size(); j++)
      {
        if (!(agentVec.get(j) instanceof Airing))
        {
          allAirs = false;
          break;
        }
      }
      if (allAirs)
        Collections.sort(agentVec, airingHDSDComparator);
      for (int vecNum = 0; vecNum < agentVec.size(); vecNum++)
      {
        if (SDBG) System.out.println("SCHEDULING mutSched=" + mutableSchedule + " immutSched=" + immutableSchedule + " ESMAP=" + encoderScheduleMap);
        DBObject showSetMapKey = agentVec.get(vecNum);
        if (SDBG) System.out.println("showSetMapKey=" + showSetMapKey);
        Vector<EncAir> swappedOut = new Vector<EncAir>();
        Vector<Airing> currAirOptions = new Vector<Airing>(showSetMap.get(showSetMapKey));
        if (SDBG) System.out.println("currAirOptions=" + currAirOptions);
        EncAir appendage = appendToMultiTunerSchedule(currAirOptions, mutableSchedule, immutableSchedule,
            forcedEncodings, swappedOut, sortedEncs);
        if (SDBG) System.out.println("appendage=" + appendage + " AFTERcurrAirOptions=" + currAirOptions);
        if (showSetMapKey instanceof Airing && !okToSchedule((Airing) showSetMapKey, currTime))
        {
          // Check to see if its still OK to schedule; it may not be since another Airing with an SH id could
          // now be scheduled which eliminates the need for this one if they're on at the same timeslot
          if (SDBG) System.out.println("Skipping Airing because its no longer OK to schedule it: " + showSetMapKey);
          continue;
        }
        if (appendage == null && currAirOptions.isEmpty())
        {
          // Unschedulable with no hopes of mutating the schedule to accommodate it
          // Check for what caused it to NOT get scheduled and if there's any ambiguous conflicts
          // in there, then add them to our pubUnresolvedConflicts list
          Vector<Airing> unwantedAirs = showSetMap.get(showSetMapKey);
          // alwaysKicked means that everything that's scheduled in place of the unwantedShow
          // for all of its airings has a defined higher priority, set it to false
          // if a situation is encountered where a case for the conflict doesn't
          // have a priority set
          boolean alwaysKicked = true;
          boolean mrExcluded = false;
          Vector<EncAir> currPermute = new Vector<EncAir>();
          currPermute.addAll(forcedEncodings);
          currPermute.addAll(immutableSchedule);
          currPermute.addAll(mutableSchedule);
          for (int j = 0; j < unwantedAirs.size(); j++)
          {
            // The mustBeWeakAir will NOT be an MR
            Airing mustBeWeakAir = unwantedAirs.elementAt(j);
            long mustBeWeakStart = getSchedulingStart(mustBeWeakAir);
            long mustBeWeakEnd = getSchedulingEnd(mustBeWeakAir);
            boolean hasOverlaps = false;
            for (int k = 0; k < currPermute.size(); k++)
            {
              EncAir encAirData = currPermute.elementAt(k);
              // testPermAir is what is in the schedule for this permute, and mustBeWeakAir is what is not
              Airing testPermAir = encAirData.air;
              ManualRecord testPermMR = wiz.getManualRecord(testPermAir);
              if (doesSchedulingOverlap(testPermAir, mustBeWeakStart, mustBeWeakEnd) &&
                  stationDeviceOverlapExists(mustBeWeakAir, testPermAir))
              {
                hasOverlaps = true;
                Airing godRes = god.doBattle(testPermAir, mustBeWeakAir);
                if (testPermMR != null)
                {
                  if (!testPermMR.isStronger(mustBeWeakAir) && godRes != testPermAir)
                    alwaysKicked = false;
                  if (testPermMR.isStronger(mustBeWeakAir))
                    mrExcluded = true;
                }
                else if (godRes == mustBeWeakAir)
                {
                  // This is a violation of the conflict preference so
                  // abandon this permutation
                  if (SDBG) System.out.println("ILLEGAL PERMUTATION BECAUSE OF PRIORITY:" + currPermute);
                }
                else if (godRes == testPermAir)
                  mrExcluded = true; // Added on 6/25/03, a single prioritized overlap gives us resolution!
                else if (godRes == null)
                  alwaysKicked = false;
              }
            }
          }
          if (SDBG) System.out.println("CASE 1 NO HOPE FOR SCHEDULING unresolvedConflicts=" + (!alwaysKicked && !mrExcluded));
          pendingConflicts.put(showSetMapKey, showSetMap.get(showSetMapKey));
          if (!alwaysKicked && !mrExcluded)
            pendingUnresolvedConflicts.put(showSetMapKey, showSetMap.get(showSetMapKey));
        }
        else if (appendage == null)
        {
          if (SDBG) System.out.println("CASE 2 TIME TO REARRANGE");
          // We couldn't figure out a way to schedule this straightforwardly or with encoder swapping.
          // BUT there were conflicts with mutable schedule entries, these are the ones that conflicts
          // with what's left in currAirOptions. No we need to find the conflict map of what's in the current
          // schedule for each of those air options. The first one that can create a complete schedule is the
          // preferred one we'll use. We go through them progressively, if any of them CANNOT make a complete
          // schedule, they get removed from currAirOptions. If we go through them all and can't find any full
          // schedule permutations, then we are unable to schedule this one and must add it to the pubConflicts
          // if its ambiguous why this occurred
          boolean rearrangeSucceeded = false;
          Airing currAirOpt = null;

          Map<Set<Integer>, Vector<CaptureDevice>> stationSetVecMap =
              new HashMap<Set<Integer>, Vector<CaptureDevice>>();
          Vector<CaptureDevice> optNameVec = new Vector<CaptureDevice>();
          Map<CaptureDevice, CaptureDevice> nameTransMap =
              new HashMap<CaptureDevice, CaptureDevice>();
          for (int a = 0; a < sortedEncs.length; a++)
          {
            Vector<CaptureDevice> tunables = stationSetVecMap.get(sortedEncs[a].stationSet);
            if (tunables == null)
            {
              stationSetVecMap.put(sortedEncs[a].stationSet,
                  tunables = new Vector<CaptureDevice>());
              optNameVec.add(sortedEncs[a].capDev);
              nameTransMap.put(sortedEncs[a].capDev, sortedEncs[a].capDev);
            }
            else
              nameTransMap.put(sortedEncs[a].capDev, tunables.firstElement());
            tunables.add(sortedEncs[a].capDev);
          }
          // These are the list of encoder names coalesced on stationSet to optimize performance
          // for scheduling with a large number of tuners.
          CaptureDevice[] optimumMultiNames = optNameVec.toArray(new CaptureDevice[0]);

          while (!currAirOptions.isEmpty())
          {
            currAirOpt = currAirOptions.firstElement();
            if (SDBG) System.out.println("currAirOpt=" + currAirOpt);

            boolean abandonedHope = false;

            // Find the conflict web for this airing against the current schedule
            // conflictMap is Show/Airing->Vector(Airing)
            Map<DBObject, Vector<Airing>> conflictMap = new HashMap<DBObject, Vector<Airing>>();
            HashSet<Airing> immutOverlapSet = Pooler.getPooledHashSet();
            Set<EncAir> forcedOverlaps = new HashSet<EncAir>();
            Vector<Airing> findConflicts = Pooler.getPooledVector();
            findConflicts.add(currAirOpt);
            // We know we're part of this conflict group, so put us in there already
            conflictMap.put(currAirOpt, new Vector<Airing>(findConflicts));
            // findConflicts contains all of the Airings that we still may find conflicts for
            HashSet<? super Airing> proccessedForOverlaps = Pooler.getPooledHashSet();
            double runningPermuteCount = 1;
            while (!findConflicts.isEmpty())
            {
              if (runningPermuteCount > insane_permute_count)
              {
                if (SDBG) System.out.println("Doing early abandon on scheduling due to extensive complexity of web of conflicts");
                abandonedHope = true;
                break;
              }
              // All of the Airings get pulled through here at one point or another
              Airing evilAir = findConflicts.remove(0);
              long evilAirStart = getSchedulingStart(evilAir);
              long evilAirEnd = getSchedulingEnd(evilAir);

              // First find the immutable overlaps
              for (int a = 0; a < forcedEncodings.size(); a++)
              {
                EncAir ea = forcedEncodings.get(a);
                if (doesSchedulingOverlap(ea.air, evilAirStart, evilAirEnd) && proccessedForOverlaps.add(ea.air))
                {
                  forcedOverlaps.add(new EncAir(ea.air, nameTransMap.get(ea.capDev), true));
                  findConflicts.add(ea.air);
                }
              }
              for (int a = 0; a < immutableSchedule.size(); a++)
              {
                EncAir ea = immutableSchedule.get(a);
                if (doesSchedulingOverlap(ea.air, evilAirStart, evilAirEnd) && proccessedForOverlaps.add(ea.air))
                {
                  immutOverlapSet.add(ea.air);
                  findConflicts.add(ea.air);
                  runningPermuteCount *= optimumMultiNames.length;
                }
              }

              // Check all of the remaining mutable entries for conflicts
              for (int a = 0; a < mutableSchedule.size(); a++)
              {
                EncAir ea = mutableSchedule.get(a);
                if (doesSchedulingOverlap(ea.air, evilAirStart, evilAirEnd) && proccessedForOverlaps.add(ea.air))
                {
                  // Airing/Show
                  DBObject testShowForConflict = (showSetMap.containsKey(ea.air) ? ea.air : ea.air.getShow());
                  Vector<Airing> currTestShowAirs = showSetMap.get(testShowForConflict);
                  // If any of them overlap, the whole list of its airings is part of this group, and
                  // all will need to be analyzed for further conflicts
                  findConflicts.addAll(currTestShowAirs);
                  conflictMap.put(testShowForConflict, currTestShowAirs);
                  runningPermuteCount *= optimumMultiNames.length * currTestShowAirs.size();
                }
              }
            }
            Pooler.returnPooledVector(findConflicts);
            findConflicts = null;
            Pooler.returnPooledHashSet(proccessedForOverlaps);
            proccessedForOverlaps = null;
            if (abandonedHope)
            {
              Pooler.returnPooledHashSet(immutOverlapSet);
              currAirOptions.remove(0);
              continue;
            }

            // Now we have all of the information about the web of scheduling conflicts for this instance
            // Now we run the iteration technique we came up with to find the best permutation there is.
            // This should go really fast because the web has been minimized, and a lot of redundancy
            // has been removed.
            if (SDBG) System.out.println("conflictMap=" + conflictMap + " immutOverlaps=" + immutOverlapSet +
                " forcedLaps=" + forcedOverlaps);

            // Put the mustsees with the most number of airs at the front of the list, that'll
            // give them higher bit positions and cause a higher reduction in permutation count
            Vector<Vector<Airing>> conflictAirList =
                new Vector<Vector<Airing>>(conflictMap.values());
            Collections.sort(conflictAirList, new Comparator<Vector<?>>()
            {
              public int compare(Vector<?> o1, Vector<?> o2)
              {
                return o2.size() - o1.size();
              }
            });

            /*
             * For multiple tuners that have identical station sets, we want to perform an
             * optimization. In this case, we will allow overlapping recordings to be
             * scheduled to a multi-tuner collective.  The rule for determining if a set
             * of airings A is schedule with N tuners is: The N tuners can schedule all of
             * the events in A if there exists no time span that is less than or equal to
             * that of an airing in A that intersects with more than N-1 other airings in A.
             */
            Vector<Airing> immutOverlaps = new Vector<Airing>(immutOverlapSet);
            Pooler.returnPooledHashSet(immutOverlapSet);
            immutOverlapSet = null;
            int numMRs = immutOverlaps.size();
            int numMustSees = conflictAirList.size();
            int numAlready = forcedOverlaps.size();
            int[] permuteCounts = new int[numMustSees + numMRs];
            /*
             * The forceRollBit is what accelerates our analysis
             * of the permutations. Whenever we find something that's illegal
             * we then mark the lower bit as the forceRollBit so it gets rolled
             * the next time. The bits represent the indexes in the mustsee/mr lists
             */
            int forceRollBit = permuteCounts.length - 1;
            boolean countNeedsInc = false;
            boolean foundCompleteMRSchedule = false;
            // Calculate how many permutations there's going to be
            double totalPermutes = 1;
            double[] permuteFactors = new double[permuteCounts.length];
            for (int a = permuteCounts.length - 1; a >= 0; a--)
            {
              permuteFactors[a] = totalPermutes;
              if (a < numMRs)
                totalPermutes *= optimumMultiNames.length;
              else
                totalPermutes *= (optimumMultiNames.length * conflictAirList.get(a - numMRs).size());
            }

            Vector<EncAir> bestPermute = null;

            if (SDBG) System.out.println("STARTING ITERATIVE SCHEDULE totalPermutes=" + totalPermutes + " runningPermutes=" + runningPermuteCount +
                " permutesCounts.length=" + permuteCounts.length);
            double totalReductions = 0;
            double permsEvaluated = 0;
            int permsChecked = 0;
            long lastPrintTime = 0;
            long iterStartTime = Sage.eventTime();
            while (true)
            {
              // This is for the horribly ugly case where we just shouldn't try the iterative stuff because the schedule is so
              // complex we'd never find the solution (if there even is one...and likely there is not)
              if (totalPermutes > insane_permute_count)
              {
                if (SDBG) System.out.println("Scheduler is abandoning this evaluation-1! totalPermutes=" + totalPermutes);
                break;
              }
              if (countNeedsInc)
              {
                // Increment/roll the permute counter, and if we're done then break out
                boolean rollThisOne = true;
                if (forceRollBit < permuteCounts.length - 1)
                {
                  totalReductions += permuteFactors[forceRollBit];
                  if (Sage.eventTime() - lastPrintTime > 2000)
                  {
                    if (SDBG) System.out.println("TotalPermutes:" + totalPermutes + " TotalReductions:" + totalReductions + " permsChecked=" + permsChecked);
                    lastPrintTime = Sage.eventTime();
                  }
                }
                permsChecked++;
                if (permsChecked > conflict_resolution_search_depth ||
                    (Sage.eventTime() - iterStartTime > conflict_resolution_search_time &&
                        (permsChecked > conflict_resolution_search_min_for_timeout))) // just in case, then we just abandon this as an option
                {
                  if (SDBG) System.out.println("Scheduler is abandoning this evaluation-2: Lost " +
                      (Sage.eventTime() - iterStartTime) + " millis before scheduler abandon");
                  break;
                }
                ///{
                //if (Sage.DBG) System.out.println("Scheudling iterative permute reduction by " + permuteFactors[forceRollBit]);
                //}
                for (int a = forceRollBit; a >= 0; a--)
                {
                  if (a < numMRs)
                  {
                    if (permuteCounts[a] < optimumMultiNames.length - 1)
                    {
                      permuteCounts[a]++;
                      rollThisOne = false;
                      break;
                    }
                    else
                      permuteCounts[a] = 0;
                  }
                  else
                  {
                    if (permuteCounts[a] <
                        (conflictAirList.get(a - numMRs).size()*optimumMultiNames.length) - 1)
                    {
                      permuteCounts[a]++;
                      rollThisOne = false;
                      break;
                    }
                    else
                      permuteCounts[a] = 0;
                  }
                }
                if (rollThisOne)
                  break;
                countNeedsInc = false;
              }
              forceRollBit = permuteCounts.length - 1;
              Vector<EncAir> currPermute = new Vector<EncAir>();
              int permIdx = 0;
              // Anything that's a forced encoding gets put in the list first
              currPermute.addAll(forcedOverlaps);

              boolean clean = true;
              // Go through all of the MRs and put in the permutation for the current
              // encoder that its on
              for (permIdx = 0; permIdx < numMRs && clean; permIdx++)
              {
                Airing currMR = immutOverlaps.get(permIdx);
                int currPermCount = permuteCounts[permIdx];
                EncoderSchedule basePermES = encoderScheduleMap.get(optimumMultiNames[currPermCount]);
                if (!basePermES.stationSet.contains(currMR.stationID) ||
                    !basePermES.supportsAirQuality(currMR))
                {
                  clean = false;
                  forceRollBit = permIdx;
                  break;
                }
                Vector<long[]> overlapIntersections = new Vector<long[]>();
                overlapIntersections.add(new long[] { getSchedulingStart(currMR), getSchedulingEnd(currMR), 1});
                for (int a = 0; a < currPermute.size(); a++)
                {
                  EncAir alreadyEnced = currPermute.get(a);
                  if (alreadyEnced.capDev.equals(optimumMultiNames[currPermCount]) &&
                      doesSchedulingOverlap(alreadyEnced.air, currMR))
                  {
                    // Check how many tuners have this lineup
                    int numTunersForStat = stationSetVecMap.get(basePermES.stationSet).size();
                    if (numTunersForStat == 1)
                    {
                      clean = false;
                      forceRollBit = permIdx;
                      break;
                    }
                    else
                    {
                      // Go through the overlap intersections and adjust them to account for
                      // this new overlap. If we find any intersections that are greater
                      // than the number of tuners for this lineup, then this permute is illegal
                      long intersectUpdateStart = Math.max(getSchedulingStart(alreadyEnced.air),
                          getSchedulingStart(currMR));
                      long intersectUpdateEnd = Math.min(getSchedulingEnd(alreadyEnced.air),
                          getSchedulingEnd(currMR));
                      for (int interCount = 0; interCount < overlapIntersections.size(); interCount++)
                      {
                        long[] currInterList = overlapIntersections.get(interCount);
                        if (currInterList[1] <= intersectUpdateStart)
                          continue;
                        if (currInterList[0] >= intersectUpdateEnd)
                          break;
                        if (currInterList[2] + 1 > numTunersForStat)
                        {
                          clean = false;
                          forceRollBit = permIdx;
                          break;
                        }
                        if (currInterList[0] == intersectUpdateStart &&
                            currInterList[1] <= intersectUpdateEnd)
                        {
                          // It covers this whole range, just inc the counter
                          currInterList[2]++;
                          intersectUpdateStart = currInterList[1];
                        }
                        else if (currInterList[0] < intersectUpdateStart &&
                            currInterList[1] <= intersectUpdateEnd)
                        {
                          // It covers the later part of this range. Break it
                          // into two and increment the appropriate counter
                          long[] newInterList = new long[3];
                          newInterList[0] = intersectUpdateStart;
                          newInterList[1] = currInterList[1];
                          newInterList[2] = currInterList[2] + 1;
                          currInterList[1] = intersectUpdateStart;
                          interCount++;
                          overlapIntersections.insertElementAt(newInterList, interCount);
                          intersectUpdateStart = newInterList[1];
                        }
                        else if (currInterList[0] == intersectUpdateStart &&
                            currInterList[1] > intersectUpdateEnd)
                        {
                          // It covers the front part of this range, break it
                          // into two.
                          long[] newInterList = new long[3];
                          newInterList[0] = intersectUpdateStart;
                          newInterList[1] = intersectUpdateEnd;
                          newInterList[2] = currInterList[2] + 1;
                          currInterList[0] = intersectUpdateEnd;
                          overlapIntersections.insertElementAt(newInterList, interCount);
                          interCount++;
                          intersectUpdateStart = intersectUpdateEnd;
                        }
                        else
                        {
                          // It covers the middle part of this range, break it
                          // into three
                          long[] newInterListMid = new long[3];
                          newInterListMid[0] = intersectUpdateStart;
                          newInterListMid[1] = intersectUpdateEnd;
                          newInterListMid[2] = currInterList[2] + 1;
                          long[] newInterListEnd = new long[3];
                          newInterListEnd[0] = intersectUpdateEnd;
                          newInterListEnd[1] = currInterList[1];
                          newInterListEnd[2] = currInterList[2];
                          currInterList[1] = intersectUpdateStart;
                          interCount++;
                          overlapIntersections.insertElementAt(newInterListMid, interCount);
                          interCount++;
                          overlapIntersections.insertElementAt(newInterListEnd, interCount);
                          intersectUpdateStart = intersectUpdateEnd;
                        }
                      }
                      if (!clean)
                        break;
                    }
                  }
                }
                if (clean)
                  currPermute.add(new EncAir(currMR, optimumMultiNames[currPermCount], true));
              }
              if (!clean)
              {
                // We can't schedule all of the MRs on this permute, it's no good!
                countNeedsInc = true;
                continue;
              }
              else
                foundCompleteMRSchedule = true;

              // Go through all of the Must Sees and put in the permutation for the current
              // encoder/airing that its on, it may be excluded also
              for (permIdx = numMRs; permIdx < permuteCounts.length && clean; permIdx++)
              {
                Vector<Airing> currMustSeeAirs = conflictAirList.get(permIdx - numMRs);
                int currPermCount = permuteCounts[permIdx];
                Airing currAir = currMustSeeAirs.get(currPermCount / optimumMultiNames.length);
                EncoderSchedule basePermES = encoderScheduleMap.get(optimumMultiNames[currPermCount % optimumMultiNames.length]);
                if (!basePermES.stationSet.contains(currAir.stationID) ||
                    !basePermES.supportsAirQuality(currAir))
                {
                  clean = false;
                  forceRollBit = permIdx;
                  break;
                }
                Vector<long[]> overlapIntersections = new Vector<long[]>();
                overlapIntersections.add(new long[] { getSchedulingStart(currAir), getSchedulingEnd(currAir), 1});
                for (int a = 0; a < currPermute.size(); a++)
                {
                  EncAir alreadyEnced = currPermute.get(a);
                  if (alreadyEnced.capDev.equals(optimumMultiNames[currPermCount % optimumMultiNames.length]) &&
                      doesSchedulingOverlap(alreadyEnced.air, currAir))
                  {
                    // Check how many tuners have this lineup
                    int numTunersForStat = stationSetVecMap.get(basePermES.stationSet).size();
                    if (numTunersForStat == 1)
                    {
                      clean = false;
                      forceRollBit = permIdx;
                      break;
                    }
                    else
                    {
                      // Go through the overlap intersections and adjust them to account for
                      // this new overlap. If we find any intersections that are greater
                      // than the number of tuners for this lineup, then this permute is illegal
                      long intersectUpdateStart = Math.max(getSchedulingStart(alreadyEnced.air),
                          getSchedulingStart(currAir));
                      long intersectUpdateEnd = Math.min(getSchedulingEnd(alreadyEnced.air),
                          getSchedulingEnd(currAir));
                      for (int interCount = 0; interCount < overlapIntersections.size(); interCount++)
                      {
                        long[] currInterList = overlapIntersections.get(interCount);
                        if (currInterList[1] <= intersectUpdateStart)
                          continue;
                        if (currInterList[0] >= intersectUpdateEnd)
                          break;
                        if (currInterList[2] + 1 > numTunersForStat)
                        {
                          clean = false;
                          forceRollBit = permIdx;
                          break;
                        }
                        if (currInterList[0] == intersectUpdateStart &&
                            currInterList[1] <= intersectUpdateEnd)
                        {
                          // It covers this whole range, just inc the counter
                          currInterList[2]++;
                          intersectUpdateStart = currInterList[1];
                        }
                        else if (currInterList[0] < intersectUpdateStart &&
                            currInterList[1] <= intersectUpdateEnd)
                        {
                          // It covers the later part of this range. Break it
                          // into two and increment the appropriate counter
                          long[] newInterList = new long[3];
                          newInterList[0] = intersectUpdateStart;
                          newInterList[1] = currInterList[1];
                          newInterList[2] = currInterList[2] + 1;
                          currInterList[1] = intersectUpdateStart;
                          interCount++;
                          overlapIntersections.insertElementAt(newInterList, interCount);
                          intersectUpdateStart = newInterList[1];
                        }
                        else if (currInterList[0] == intersectUpdateStart &&
                            currInterList[1] > intersectUpdateEnd)
                        {
                          // It covers the front part of this range, break it
                          // into two.
                          long[] newInterList = new long[3];
                          newInterList[0] = intersectUpdateStart;
                          newInterList[1] = intersectUpdateEnd;
                          newInterList[2] = currInterList[2] + 1;
                          currInterList[0] = intersectUpdateEnd;
                          overlapIntersections.insertElementAt(newInterList, interCount);
                          interCount++;
                          intersectUpdateStart = intersectUpdateEnd;
                        }
                        else
                        {
                          // It covers the middle part of this range, break it
                          // into three
                          long[] newInterListMid = new long[3];
                          newInterListMid[0] = intersectUpdateStart;
                          newInterListMid[1] = intersectUpdateEnd;
                          newInterListMid[2] = currInterList[2] + 1;
                          long[] newInterListEnd = new long[3];
                          newInterListEnd[0] = intersectUpdateEnd;
                          newInterListEnd[1] = currInterList[1];
                          newInterListEnd[2] = currInterList[2];
                          currInterList[1] = intersectUpdateStart;
                          interCount++;
                          overlapIntersections.insertElementAt(newInterListMid, interCount);
                          interCount++;
                          overlapIntersections.insertElementAt(newInterListEnd, interCount);
                          intersectUpdateStart = intersectUpdateEnd;
                        }
                      }
                      if (!clean)
                        break;
                    }
                  }
                }
                if (clean)
                  currPermute.add(new EncAir(currAir,
                      optimumMultiNames[currPermCount % optimumMultiNames.length], false));
              }
              if (!clean)
              {
                // We can't schedule all of the MRs on this permute, it's no good!
                countNeedsInc = true;
                continue;
              }
              countNeedsInc = true;

              Set<DBObject> undoneShows = new HashSet<DBObject>(conflictMap.keySet());
              for (int a = 0; a < currPermute.size(); a++)
              {
                EncAir encAirData = currPermute.elementAt(a);
                Airing removeThisShow = encAirData.air;
                undoneShows.remove(removeThisShow.getShow());
                undoneShows.remove(removeThisShow);
              }

              permsEvaluated++;

              //if (SDBG) System.out.println("CLEANED PERMUTATION " + currPermute + " UNRESOLVED=" + currUnresolved);
              // Now none of our permutations violate the rules of the user.

              // Ordering for this is
              // 1. Most # of recordings is the best
              // 2. Least # of unresolved
              // 3. Least interference with curr recordings
              // 4. Earliest completion
              // 5. Keeping the channel the encoders are on the same
              if (bestPermute == null)
              {
                bestPermute = currPermute;
                if (SDBG) System.out.println("NEW1 BestPermute=" + bestPermute);
              }
              else
              {
                if (currPermute.size() > bestPermute.size())
                {
                  bestPermute = currPermute;
                  if (SDBG) System.out.println("NEW2 BestPermute=" + bestPermute);
                }
              }

              // This is the best we can do, just stop now!!!
              if (bestPermute != null)
                break;
            }
            if (SDBG) System.out.println("SCHEDULING Total permutations checked:" + permsChecked + " evaluated:" + permsEvaluated +
                " reductions=" + totalReductions);
            if (SDBG) System.out.println("BestPermute=" + bestPermute);

            if (bestPermute == null)
            {
              currAirOptions.remove(0);
              continue;
            }
            // We now have the best schedule for these option; put it into the schedule.
            // Any changes made need to be reflected in the mutable/immutable lists as well
            // as the EncoderSchedules
            /*
             * To maintain the sync between the ES scheds & the mut/immut scheds, first create
             * a Set of all of the Airings that are in this conflict web, make 2 sets, one will
             * be the muts and the other the immuts. Then remove all of those
             * airings from all of the EncoderSchedules. Then remove all of those airings from
             * the mut/imuts schedules.  Then force all of the elements of this
             * permutation into the ES schedules. At the same time, put them back into
             * the appropriate mut/imut schedule. Then we're done!
             */
            Set<Airing> immutAirSet = new HashSet<Airing>(immutOverlaps);
            Set<Airing> mutAirSet = new HashSet<Airing>();
            for (int immNum = 0; immNum < immutOverlaps.size(); immNum++)
            {
              Airing immAir = immutOverlaps.get(immNum);
              for (int remEnc = 0; remEnc < sortedEncs.length; remEnc++)
              {
                if (sortedEncs[remEnc].removeMustSee(immAir))
                  break;
              }
            }
            for (int remEnc = 0; remEnc < immutableSchedule.size(); remEnc++)
            {
              EncAir fooEnc = immutableSchedule.get(remEnc);
              if (immutAirSet.contains(fooEnc.air))
              {
                immutableSchedule.remove(remEnc);
                remEnc--;
              }
            }
            for (int conListNum = 0; conListNum < conflictAirList.size(); conListNum++)
            {
              Vector<Airing> airList = conflictAirList.get(conListNum);
              mutAirSet.addAll(airList);
              air_list_breaker:
                for (int airListNum = 0; airListNum < airList.size(); airListNum++)
                {
                  Airing fooAir = airList.get(airListNum);
                  for (int remEnc = 0; remEnc < sortedEncs.length; remEnc++)
                  {
                    if (sortedEncs[remEnc].removeMustSee(fooAir))
                      break air_list_breaker;
                  }
                }
            }
            for (int remEnc = 0; remEnc < mutableSchedule.size(); remEnc++)
            {
              EncAir fooEnc = mutableSchedule.get(remEnc);
              if (mutAirSet.contains(fooEnc.air))
              {
                mutableSchedule.remove(remEnc);
                remEnc--;
              }
            }

            if (currAirOptions.size() == 1)
            {
              // If there's only one schedule, then put us in the immutable list
              mutAirSet.remove(currAirOpt);
              immutAirSet.add(currAirOpt);
            }

            rearrangeSucceeded = true;

            // We now need to sort out the schedule for any coalesced multi tuning we setup
            for (Vector<CaptureDevice> currVal : stationSetVecMap.values())
            {
              //Vector currVal = (Vector) walker.next();
              if (currVal.size() <= 1)
              {
                // Only one encoder for these scheduling options, no need to update
                continue;
              }
              CaptureDevice masterEnc = currVal.firstElement();
              Vector<EncoderSchedule> appliedES = new Vector<EncoderSchedule>();
              for (int currValIdx = 0; currValIdx < currVal.size(); currValIdx++)
              {
                EncoderSchedule currES = encoderScheduleMap.get(currVal.get(currValIdx));
                appliedES.add(currES);
              }

              Vector<Airing> variableTunerAirList = new Vector<Airing>();
              for (int a = 0; a < bestPermute.size(); a++)
              {
                EncAir encAir = bestPermute.elementAt(a);
                if (encAir.capDev.equals(masterEnc))
                {
                  // 5/8/06 - BUG FIX: Check to make sure that what we're adding to the variableTunerAirList is NOT
                  // already in the forcedEncodings.
                  boolean alreadyForced = false;
                  for (int b = 0; b < forcedEncodings.size(); b++)
                  {
                    EncAir eafe = forcedEncodings.get(b);
                    if (eafe.air == encAir.air)
                    {
                      alreadyForced = true;
                      break;
                    }
                  }
                  if (!alreadyForced)
                    variableTunerAirList.add(encAir.air);
                  bestPermute.remove(a);
                  a--;
                }
              }
              // this won't include forcedEncodings in the returned array
              Vector<EncAir> tunerBoundSched = generateSingleMultiTunerSchedulingPermutation(variableTunerAirList,
                  appliedES.toArray(new EncoderSchedule[0]), forcedEncodings);
              bestPermute.addAll(tunerBoundSched);
            }

            for (int a = 0; a < bestPermute.size(); a++)
            {
              EncAir encAir = bestPermute.elementAt(a);
              EncoderSchedule es = encoderScheduleMap.get(encAir.capDev);
              if (es != null)
              {
                es.forceIntoMustSee(encAir.air);
                if (immutAirSet.contains(encAir.air))
                  immutableSchedule.add(encAir);
                else if (mutAirSet.contains(encAir.air))
                  mutableSchedule.add(encAir);
              }
            }
            break;
          }

          if (!rearrangeSucceeded)
          {
            // Check for what caused it to NOT get scheduled and if there's any ambiguous conflicts
            // in there, then add them to our pubUnresolvedConflicts list
            Vector<Airing> unwantedAirs = showSetMap.get(showSetMapKey);
            // alwaysKicked means that everything that's scheduled in place of the unwantedShow
            // for all of its airings has a defined higher priority, set it to false
            // if a situation is encountered where a case for the conflict doesn't
            // have a priority set
            boolean alwaysKicked = true;
            boolean mrExcluded = false;
            Vector<EncAir> currPermute = new Vector<EncAir>();
            currPermute.addAll(forcedEncodings);
            currPermute.addAll(immutableSchedule);
            currPermute.addAll(mutableSchedule);
            for (int j = 0; j < unwantedAirs.size(); j++)
            {
              // The mustBeWeakAir will NOT be an MR
              Airing mustBeWeakAir = unwantedAirs.elementAt(j);
              long mustBeWeakStart = getSchedulingStart(mustBeWeakAir);
              long mustBeWeakEnd = getSchedulingEnd(mustBeWeakAir);
              boolean hasOverlaps = false;
              for (int k = 0; k < currPermute.size(); k++)
              {
                EncAir encAirData = currPermute.elementAt(k);
                // testPermAir is what is in the schedule for this permute, and mustBeWeakAir is what is not
                Airing testPermAir = encAirData.air;
                ManualRecord testPermMR = wiz.getManualRecord(testPermAir);
                if (doesSchedulingOverlap(testPermAir, mustBeWeakStart, mustBeWeakEnd) &&
                    stationDeviceOverlapExists(mustBeWeakAir, testPermAir))
                {
                  hasOverlaps = true;
                  Airing godRes = god.doBattle(testPermAir, mustBeWeakAir);
                  if (testPermMR != null)
                  {
                    if (!testPermMR.isStronger(mustBeWeakAir) && godRes != testPermAir)
                      alwaysKicked = false;
                    if (testPermMR.isStronger(mustBeWeakAir))
                      mrExcluded = true;
                  }
                  else if (godRes == mustBeWeakAir)
                  {
                    // This is a violation of the conflict preference so
                    // abandon this permutation
                    if (SDBG) System.out.println("ILLEGAL PERMUTATION BECAUSE OF PRIORITY:" + currPermute);
                  }
                  else if (godRes == testPermAir)
                    mrExcluded = true; // Added on 6/25/03, a single prioritized overlap gives us resolution!
                  else if (godRes == null)
                    alwaysKicked = false;
                }
              }
            }
            if (SDBG) System.out.println("CASE 2 REARRANGE FAILED unresolvedConflicts=" + (!alwaysKicked && !mrExcluded));
            pendingConflicts.put(showSetMapKey, showSetMap.get(showSetMapKey));
            if (!alwaysKicked && !mrExcluded)
              pendingUnresolvedConflicts.put(showSetMapKey, showSetMap.get(showSetMapKey));
          }
          else
          {
            if (currAirOptions.size() == 1)
            {
              // There's only one way to do this thang so we're not up for mutation
              showSetMap.remove(showSetMapKey);
            }
            else
            {
              showSetMap.put(showSetMapKey, currAirOptions);
            }
          }
        }
        else
        {
          if (SDBG) System.out.println("CASE 3 FOUND IT EASILY");
          // Woo-hoo! We figured out a preferred schedule.
          if (currAirOptions.size() == 1)
          {
            // There's only one way to do this thang so we're not up for mutation
            showSetMap.remove(showSetMapKey);
            immutableSchedule.add(appendage);
          }
          else
          {
            showSetMap.put(showSetMapKey, currAirOptions);
            mutableSchedule.add(appendage);
          }
          EncoderSchedule es = encoderScheduleMap.get(appendage.capDev);
          if (es != null)
          {
            es.forceIntoMustSee(appendage.air);
          }
          for (int k = 0; k < swappedOut.size(); k++)
          {
            // Anything in swapped out has been removed from it encoder schedule, but now
            // we need to put it into the new encoder schedule
            EncAir swappedEnc = swappedOut.get(k);
            es = encoderScheduleMap.get(swappedEnc.capDev);
            if (es != null)
              es.forceIntoMustSee(swappedEnc.air);
          }
        }
      }
    }


    /*
     * NOTE WE STILL NEED TO MANIPULATE THE SCHEDULE BY SWAPPING ANY ENCODERS THAT
     * GIVE US AN ADVANTAGE. This only effects what we're recording now more or less.
     * The problems we can address here are unnecessary promptings for channel changes.
     * The procedure we want to follow is that any encoders with desired encodings
     * should try to make 2 optimizations. No optimization can be done if the nextRecord
     * is immediately after the currRecord and is on the same station. (The first optimization
     * is if there's another encoder scheduled to record the show that's on immediately after the currRecord
     * and that show is a must see or MR, then swap that schedule entry to the forced encoder's schedule.
     * --this first optimization is unnecessary because the Seeker will enforce that by default and then
     * the scheduler will rerun and fix itself to accommodate for that)
     * The next optimization is if there's a must see scheduled immediately after the currRecord (on a different station)
     * then try and swap that next record to another encoder so we don't have to prompt the user
     * unnecessarily about a channel change.
     */
    if (sortedEncs.length > 1)
    {
      for (int i = 0; i < sortedEncs.length; i++)
      {
        if (sortedEncs[i].isDesired && sortedEncs[i].currRecord != null && sortedEncs[i].mustSee.size() > 0)
        {
          Airing nextMustRecord = sortedEncs[i].mustSee.firstElement();
          int mySchedCountOffset = 1;
          if (nextMustRecord == sortedEncs[i].currRecord)
          {
            if (sortedEncs[i].mustSee.size() == 1)
              continue;
            nextMustRecord = sortedEncs[i].mustSee.get(1);
            mySchedCountOffset = 2;
          }
          if (nextMustRecord.stationID == sortedEncs[i].currRecord.stationID)
            continue;
          // If we don't add the ask advance in here then it will only switch them when they're back to back,
          // but we want it switched if it'll prompt us at all
          long askAdvance = Sage.getLong("seeker/" + Seeker.CHANNEL_CHANGE_ASK_ADVANCE, 5*Sage.MILLIS_PER_MIN);
          if (getSchedulingStart(nextMustRecord) <= getSchedulingEnd(sortedEncs[i].currRecord) + askAdvance)
          {
            // This is going to cause the user to be prompted about a channel
            // change, lets see if we can avoid it
            swap_encoder_loop:
              for (int j = 0; j < sortedEncs.length; j++)
              {
                if (j == i) continue;
                if (!sortedEncs[j].stationSet.contains(nextMustRecord.stationID) ||
                    !sortedEncs[j].supportsAirQuality(nextMustRecord))
                  continue;

                // We can swap with another encoder if that other encoder meets these rules:
                // 1. It can't have a forced encoding that overlaps with our nextRecord
                // 2. If its desired, our nextRecord can't cause a prompt on it
                // 3. If any must sees overlap with the nextRecord, they must be able to
                //    be moved to our schedule without us still being prompted
                if (sortedEncs[j].isForced && sortedEncs[j].currRecord != null &&
                    doesSchedulingOverlap(nextMustRecord, sortedEncs[j].currRecord))
                  continue;
                if (sortedEncs[j].isDesired && sortedEncs[j].currRecord != null &&
                    (doesSchedulingOverlap(nextMustRecord, sortedEncs[j].currRecord) ||
                        ((getSchedulingEnd(sortedEncs[j].currRecord) == getSchedulingStart(nextMustRecord) &&
                        sortedEncs[j].currRecord.stationID != nextMustRecord.stationID))))
                  continue;
                Vector<Airing> swapTheseGuys = new Vector<Airing>();
                for (int mustNum = 0; mustNum < sortedEncs[j].mustSee.size(); mustNum++)
                {
                  Airing otherMustAir = sortedEncs[j].mustSee.get(mustNum);
                  if (doesSchedulingOverlap(otherMustAir, nextMustRecord))
                  {
                    // They overlap, so this must see will have to be transferred back
                    // to us. Ensure it doesn't screw up our schedule or cause a prompt on us
                    if (doesSchedulingOverlap(sortedEncs[i].currRecord, otherMustAir) ||
                        (sortedEncs[i].mustSee.size() > mySchedCountOffset &&
                            doesSchedulingOverlap((sortedEncs[i].mustSee.get(mySchedCountOffset)), otherMustAir)))
                    {
                      continue swap_encoder_loop;
                    }
                    // We can't just use straight alignment or if it's off by a minute we will still get prompted.
                    // A good example is a program that starts one minute later.
                    // The old code was a straight ==, now we make sure the gap is bigger than 2 * the prompt advance.
                    if ((getSchedulingEnd(sortedEncs[i].currRecord) >= getSchedulingStart(otherMustAir) - 2*askAdvance) &&
                        sortedEncs[i].currRecord.stationID != otherMustAir.stationID)
                    {
                      continue swap_encoder_loop;
                    }
                    // Make sure our encoder can actually receive & encode this station as well
                    if (!sortedEncs[i].stationSet.contains(otherMustAir.stationID) ||
                        !sortedEncs[i].supportsAirQuality(otherMustAir))
                      continue swap_encoder_loop;
                    swapTheseGuys.add(otherMustAir);
                  }
                }

                // If we've made it here then the swap is permitted.
                if (Sage.DBG) System.out.println("Encoder Scheduling Swap to avoid unnecessary prompting. Taking " +
                    nextMustRecord + " from " + sortedEncs[i].capDev + " and swapping it with " +
                    swapTheseGuys + " on " + sortedEncs[j]);
                sortedEncs[i].removeMustSee(nextMustRecord);
                for (int removeNum = 0; removeNum < swapTheseGuys.size(); removeNum++)
                {
                  Airing fooAir = swapTheseGuys.get(removeNum);
                  sortedEncs[j].removeMustSee(fooAir);
                  sortedEncs[i].forceIntoMustSee(fooAir);
                }
                sortedEncs[j].forceIntoMustSee(nextMustRecord);
                break;
              }
          }
        }
      }
    }

    Set<Airing> allCurrRecords = new HashSet<Airing>();
    for (EncoderSchedule schedule : encoderScheduleMap.values())
      allCurrRecords.add(schedule.currRecord);

    for (EncoderSchedule es : encoderScheduleMap.values())
    {
      if (Sage.DBG) System.out.println("MUST SEE FINAL-" + es.capDev + "-" + es.mustSee.toString());

      // SCHEDULE CLEANUP 7/22/03
      // Removal of anything from the schedule that's not in the must see & is not
      // a current record is TOTALLY SAFE. We want to clear out anything
      // that's watched (i.e. wp==0)
      for (int i = 0; i < es.schedule.size(); i++)
      {
        Airing sair = es.schedule.get(i);
        if (god.getWP(sair) == 0 && !es.mustSee.contains(sair))
        {
          if (Sage.DBG) System.out.println("Scheduler cleanup - Removing " + sair + " from schedule because " +
              "it has no WP.");
          es.schedule.removeElementAt(i);
          removeFromDontSchedule(sair);
          i--;
        }
      }

      if (!es.schedule.isEmpty() && es.schedule.firstElement() != es.currRecord &&
          allCurrRecords.contains(es.schedule.firstElement()) && !es.mustSee.contains(es.schedule.firstElement()))
      {
        // The first thing in the schedule is being recorded by another encoder.
        // The Seeker's smart enough not to do it twice, but we also want to clean up the
        // UI so people don't claim its recording the same thing on both tuners at once.
        removeFromDontSchedule(es.schedule.firstElement());
        es.schedule.removeElementAt(0);
      }

      // Curr record insertion 7/22/03
      // Insert the curr record into the schedule if its not already there. If it conflicts
      // with the must see, do NOT insert it. We only do this based off client requests, isDesired.
      // That way they can get cleared out if they're of no interest (i.e. have a WP of 0) after
      // the user stops watching it (isDesired will go false)
      if (es.currRecord != null && es.isDesired && !es.schedule.contains(es.currRecord))
      {
        boolean dontInsert = false;
        for (int i = 0; i < es.mustSee.size(); i++)
        {
          if (doesSchedulingOverlap((es.mustSee.get(i)), es.currRecord))
          {
            dontInsert = true;
            break;
          }
        }
        if (!dontInsert)
          es.forceIntoSchedule(es.currRecord);
      }
    }
    if (irEnabled)
    {
      potentials.sort();

      // Build the redundancy map. This saves us a LOT of CPU time once they have any kind of IR built up because of its n^2 performance
      agentFileRedMap = new HashMap<Agent, List<Airing>>();
      agentSchedRedMap = new HashMap<Agent, List<Airing>>();
      for (int i = 0; i < wizFileCache.length; i++)
      {
        if (wizFileCache[i] == null) continue;
        //if (!wizFileCache[i].isCompleteRecording()) continue;
        //if (wizFileCache[i].archive) continue;
        Airing fileAir = wizFileCache[i].getContentAiring();
        if (fileAir == null) continue;
        if (!BigBrother.isWatched(fileAir))
        {
          Agent currAgent = god.getCauseAgent(fileAir);
          if (currAgent != null)
          {
            List<Airing> list = agentFileRedMap.get(currAgent);
            if (list == null)
            {
              list = new LinkedList<Airing>();
              agentFileRedMap.put(currAgent, list);
            }
            list.add(fileAir);
          }
        }
      }
      for (EncoderSchedule es : encoderScheduleMap.values())
      {
        if (es.currRecord != null)
        {
          Agent currAgent = god.getCauseAgent(es.currRecord);
          if (currAgent != null)
          {
            List<Airing> list = agentSchedRedMap.get(currAgent);
            if (list == null)
            {
              list = new LinkedList<Airing>();
              agentSchedRedMap.put(currAgent, list);
            }
            list.add(es.currRecord);
          }
        }
        int i = 0;
        if (!es.schedule.isEmpty() && es.schedule.firstElement() == es.currRecord) i++;
        for (; i < es.schedule.size(); i++)
        {
          Airing schedAir = es.schedule.elementAt(i);
          Agent currAgent = god.getCauseAgent(schedAir);
          if (currAgent != null)
          {
            List<Airing> list = agentSchedRedMap.get(currAgent);
            if (list == null)
            {
              list = new LinkedList<Airing>();
              agentSchedRedMap.put(currAgent, list);
            }
            list.add(schedAir);
          }
        }
      }

      if (Sage.DBG) System.out.println("Evaluating Potentials");
      int maxEvalPots = Sage.getInt("scheduler/max_potentials_to_evaluate", 10000);
      for (int i = 0; i < potentials.size() && i < maxEvalPots; i++)
      {
        Airing currAir = potentials.elementAt(i);
        // It's more optimal to do this later
        //      if (!conflictsWithMustSee(currAir) && okToSchedule(currAir, currTime))
        {
          long currAirStart = getSchedulingStart(currAir);
          long currAirEnd = getSchedulingEnd(currAir);
          float calcWatch = god.getWP(currAir);
          float orgWatch = calcWatch;

          float currRandy;
          Float rp = scheduleRandoms.get(currAir.id);
          if (rp != null)
          {
            currRandy = rp.floatValue();
          }
          else
          {
            currRandy = (float) Math.random();
            scheduleRandoms.put(currAir.id, currRandy);
          }

          // Calculate the redundancy of this showing
          calcWatch = redScaler(calcWatch, currAir);
          calcWatch *= 1 - currRandy*FUZZIFIER;

          //System.out.println("Examining potential like of " + currAir + " w/ calcWatch=" + calcWatch +
          //  " orgWatch=" + orgWatch + " " + rp);
          float minMaxTakeWP = Float.MAX_VALUE;
          CaptureDevice minMaxTakeEncoder = null;
          for (Map.Entry<CaptureDevice, EncoderSchedule> ent : encoderScheduleMap.entrySet())
          {
            EncoderSchedule es = ent.getValue();
            if (es.stationSet.contains(currAir.stationID) &&
                es.supportsAirQuality(currAir) &&
                currAirStart > desiredStartLookTime &&
                (es.currRecord == null || getSchedulingEnd(es.currRecord) <= currAirStart))
            {
              // This device can handle this airing
              float currMax = 0;
              boolean takeIt = true;
              for (int j = 0; takeIt && (j < es.schedule.size()); j++)
              {
                Airing testAir = es.schedule.elementAt(j);
                if (doesSchedulingOverlap(testAir, currAirStart, currAirEnd))
                {
                  if (testAir.isMustSee() || wiz.getManualRecord(testAir) != null)
                  {
                    takeIt = false;
                    break;
                  }
                  Float randy = scheduleRandoms.get(testAir.id);
                  float testW = god.getWP(testAir);
                  testW = redScaler(testW, testAir);
                  if (randy != null)
                    testW *= 1.0f - FUZZIFIER*randy.floatValue();
                  else // Shouldn't be in here, but just in case...
                    testW *= 1.0f - Math.random()*FUZZIFIER;
                  currMax = Math.max(currMax, testW);
                  if (testW > calcWatch)
                  {
                    takeIt = false;
                    break;
                  }
                }
              }
              // We already checked again MRs & Favs above, so we can skip that here
              if (takeIt && currMax < minMaxTakeWP /*&& !conflictsWithMustSee(currAir)*/ && okToSchedule(currAir, currTime))
              {
                minMaxTakeWP = currMax;
                minMaxTakeEncoder = ent.getKey();
                if (minMaxTakeWP == 0)
                  break; // we know we won't get lower than this
              }
            }
          }

          if (minMaxTakeEncoder != null)
          {
            // No reason to remove it from the list, that just affects performance
            //potentials.remove(i);
            EncoderSchedule es = encoderScheduleMap.get(minMaxTakeEncoder);
            if (es != null)
            {
              es.forceIntoSchedule(currAir);
            }
          }
        }
      }
    }
    // The schedule should now contain the best full airings coming on in the future.
    // Hoorahhh for Duff Gardens!!!!!!!!
    if (Sage.DBG)
    {
      System.out.println("COMPLETE SCHEDULE-----**&^%&*-------COMPLETE SCHEDULE");
      for (EncoderSchedule es : encoderScheduleMap.values())
      {
        System.out.println(es.capDev);
        System.out.println(es.schedule.toString());
      }
    }

    agentFileRedMap = null;
    agentSchedRedMap = null;
    cachedSchedStarts.clear();
    cachedSchedEnds.clear();
    if (Sage.DBG) System.out.println("Total Schedule eval time=" + (Sage.eventTime() - schedUpdateStartTime) + " msec");
  }

  private boolean stationDeviceOverlapExists(Airing a1, Airing a2)
  {
    if (a1.stationID == a2.stationID) return true;
    // Find out if there's an encoder who has both these stations in its set
    Integer i1 = a1.stationID;
    Integer i2 = a2.stationID;
    for (EncoderSchedule es : encoderScheduleMap.values())
    {
      if (es.stationSet.contains(i1) && es.stationSet.contains(i2) &&
          es.supportsAirQuality(a1) && es.supportsAirQuality(a2))
        return true;
    }
    return false;
  }

  // This builds a new preferred schedule from adding just the set of options in the addAirList.
  // The addAirList will have any elements that were impossible to schedule removed from it.
  // The addAirList should be sorted with the most desired air at the front of the list (i.e. the earliest)
  // forcedSchedule is encoder locked airings
  // immutableSchedule is airings that have no other time slots open, but could be encoder swapped
  // mutableSchedule is airings that have other time slots open and could also be encoder swapped
  private EncAir appendToMultiTunerSchedule(Vector<Airing> addAirList,
      Vector<EncAir> mutableSchedule,
      Vector<EncAir> immutableSchedule,
      Vector<EncAir> forcedSchedule,
      Vector<EncAir> swappedOut,
      EncoderSchedule[] encs)
  {
    Vector<EncAir> totalSchedule = new Vector<EncAir>();
    EncAir appendage = null;
    totalSchedule.addAll(forcedSchedule);
    totalSchedule.addAll(immutableSchedule);
    totalSchedule.addAll(mutableSchedule);
    boolean foundSchedule = false;
    for (int i = 0; i < addAirList.size(); i++)
    {
      Airing currAir = addAirList.elementAt(i);
      Integer currStatID = currAir.stationID;
      int j = 0;
      Vector<Vector<EncAir>> overlapSets = new Vector<Vector<EncAir>>();
      for (; j < encs.length; j++)
      {
        if (encs[j].stationSet.contains(currStatID) && encs[j].supportsAirQuality(currAir))
        {
          // Check for overlaps on this encoder
          Vector<EncAir> currLaps = null;
          for (int k = 0; k < totalSchedule.size(); k++)
          {
            EncAir currEnc = totalSchedule.get(k);
            if (currEnc.capDev.equals(encs[j].capDev) && doesSchedulingOverlap(currEnc.air, currAir))
            {
              if (currLaps == null)
                currLaps = new Vector<EncAir>();
              if (!forcedSchedule.contains(currEnc))
                currLaps.add(currEnc);
            }
          }
          if (currLaps == null)
          {
            if (!foundSchedule)
            {
              appendage = new EncAir(currAir, encs[j].capDev, false);
              totalSchedule.add(appendage);
            }
            foundSchedule = true;
            break;
          }
          else if (!currLaps.isEmpty())
            overlapSets.add(currLaps);
        }
      }
      if (j == encs.length && !foundSchedule)
      {
        if (overlapSets.isEmpty())
        {
          // couldn't find any way to do it
          addAirList.remove(i);
          i--;
          continue;
        }
        // See if there's any way for encoder swapping due to lineup differences
        // we don't check multiple swaps, this would require such a bizarre lineup setup
        // that I don't need to concern myself with it at this point
        boolean completedSwap = false;
        for (int k = 0; k < overlapSets.size() && !completedSwap; k++)
        {
          Vector<EncAir> currLaps = overlapSets.get(k);
          // There has to be an encoder that does NOT receive the station we're trying
          // to schedule but DOES receive the stations that the overlaps are on AND
          // doesn't have overlaps with what we're trying to move
          for (int a = 0; a < encs.length && !completedSwap; a++)
          {
            // Check to see if this Encoder is potential for swap
            boolean noSwap = false;
            if (!encs[a].stationSet.contains(currStatID) ||
                !encs[a].supportsAirQuality(currAir))
            {
              for (int b = 0; b < currLaps.size() && !noSwap; b++)
              {
                EncAir tempEnc = currLaps.get(b);
                if (encs[a].capDev.equals(tempEnc.capDev))
                {
                  // We can't switch it to ourselves, it'd conflict below also
                  noSwap = true;
                  break;
                }
                Airing currB = tempEnc.air;
                if (!encs[a].stationSet.contains(currB.stationID) ||
                    !encs[a].supportsAirQuality(currB))
                  noSwap = true;
                else
                {
                  for (int c = 0; c < totalSchedule.size() && !noSwap; c++)
                  {
                    EncAir currEnc = totalSchedule.get(c);
                    if (currEnc.capDev.equals(encs[a].capDev) && doesSchedulingOverlap(currEnc.air, currB))
                      noSwap = true;
                  }
                }
              }
            }
            else
              noSwap = true;
            if (!noSwap)
            {
              // We found an encoder that's good to swap with, do it!
              CaptureDevice takenFrom = null;
              for (int b = 0; b < currLaps.size(); b++)
              {
                EncAir tempEnc = currLaps.get(b);
                swappedOut.add(tempEnc);
                takenFrom = tempEnc.capDev;
                tempEnc.capDev = encs[a].capDev;
              }
              if (!foundSchedule)
              {
                appendage = new EncAir(currAir, takenFrom, false);
                totalSchedule.add(appendage);
              }
              foundSchedule = true;
              completedSwap = true;
              break;
            }
          }
        }

        if (!completedSwap)
        {
          // There's no encoder swapping, but this is still possible
          // if there's a set of overlaps that are all mutable. Impossible ones get removed from
          // the addAirList
          boolean killIt = true;
          for (int k = 0; k < overlapSets.size(); k++)
          {
            Vector<EncAir> currLaps = overlapSets.get(k);
            boolean nomutate = false;
            for (int l = 0; l < currLaps.size(); l++)
            {
              // Changed from immutableSchedule on 4/15/04
              if (forcedSchedule.contains(currLaps.get(l)))
              {
                nomutate = true;
                break;
              }
            }
            if (!nomutate)
            {
              killIt = false;
              break;
            }
          }
          if (killIt)
          {
            addAirList.remove(i);
            i--;
          }
        }
      }
    }
    return appendage;
  }

  private String getQualityForAiring(Airing air)
  {
    String newQual = null;
    ManualRecord mr = wiz.getManualRecord(air);
    if (mr != null)
      newQual = mr.getRecordingQuality();
    if (newQual != null && newQual.length() == 0)
      newQual = null;
    Agent causeHead = null;
    if (newQual == null && (causeHead = god.getCauseAgent(air)) != null && causeHead.quality.length() > 0)
      newQual = causeHead.quality;
    return newQual;
  }

  /**
   * Attempt the insert the element into the set so long as it doesn't overlap any other element
   * @param set of elements
   * @param element to insert into set
   * @return true if inserted, false if it overlaps and cannot be inserted.
   */
  static <T extends LongInterval> boolean insertWithNoOverlap(TreeSet<T> set, T element) {
    if (hasOverlapInSet(set, element)) return false;
    set.add(element);
    return true;
  }

  /**
   * Test if the given interval overlaps any element in the set
   * @param set of elements
   * @param interval to test for overlap
   * @return true if overlaps, false otherwise
   */
  static <T extends LongInterval> boolean hasOverlapInSet(TreeSet<T> set, T interval) {
    SortedSet<T> subSet = set.headSet(interval);
    if (!subSet.isEmpty() && subSet.last().getUpper() > interval.getLower()) return true;
    subSet = set.tailSet(interval); // Java 1.5: inclusive of interval
    if (!subSet.isEmpty() && subSet.first().getLower() < interval.getUpper()) return true;
    return false;
  }

  /**
   * Coalate elements that overlap the given interval and return as a linked list.
   */
  static <T extends LongInterval> LinkedList<T> getOverlaps(TreeSet<T> set, T interval) {
    LinkedList<T> list = new LinkedList<T>();
    SortedSet<T> subSet = set.headSet(interval);

    // There is only ever one element below the window that can overlap.
    if (!subSet.isEmpty()) {
      T last = subSet.last();
      if (last.getUpper() > interval.getLower()) {
        list.addFirst(last);
      }
    }

    // There are multiple elements that can be above the window start since the set is sorted by
    // just the lower value.
    final long upperLimit = interval.getUpper();
    for (T tmp : set.tailSet(interval)) { // Java 1.5: inclusive of interval
      if (tmp.getLower() < upperLimit) {
        list.add(tmp);
      } else {
        break;
      }
    }
    return list;
  }

  /**
   * Get the element that preceeds the given interval
   * @param set
   * @param interval
   */
  static <T extends LongInterval> T getPrevious(TreeSet<T> set, T interval) {
    SortedSet<T> subSet = set.headSet(interval);
    if (!subSet.isEmpty()) {
      return subSet.last();
    }
    return null;
  }

  /**
   * Represent encoder-schedule pairings
   */
  static class EncSchedulable implements Cloneable {
    final int enc;
    final Schedulable scheduable;

    EncSchedulable(int number, Schedulable scheduable) {
      enc = number;
      this.scheduable = scheduable;
    }

    @Override
    public String toString() {
      return "ES[" + enc + "]: " + scheduable;
    }

    static final Comparator<EncSchedulable> TIME_END_COMPARATOR =
        new Comparator<EncSchedulable>() {
          public int compare(EncSchedulable o1, EncSchedulable o2) {
            if (o1 == o2)
              return 0;
            else if (o1 == null)
              return 1;
            else if (o2 == null) return -1;

            EncSchedulable a1 = o1;
            EncSchedulable a2 = o2;
            if (a1.scheduable == null) {
              if (a1.scheduable == a2.scheduable) {
                // in the case that they are both null, return lowest encoder number
                return a1.enc - a2.enc;
              }
              return 1;
            }
            if (a2.scheduable == null) return -1;
            long timeDiff = a1.scheduable.getSchedulingEnd() - a2.scheduable.getSchedulingEnd();
            if (timeDiff == 0) {
              // NOTE: Adding extra distinction to always load up the lowest encoder first.
              return a1.enc - a2.enc;
            } else {
              return (timeDiff < 0) ? -1 : 1;
            }
          }
        };
  }

  /**
   * As generateSchedule() permutes over a list of manual recordings, ScheduleCollector receives
   * updates for each valid and conflicting airing. Its up to the implementor to record the output
   * of a given permutation.
   *
   * See {@link EncScheduleCollector}, {@link FastScheduleTestCollector}, and the test
   * implementation {@link sage.SchedulerTests.SimpleScheduleCollector} for examples.
   */
  public static interface ScheduleCollector {

    /**
     * The airing should be scheduled to record with the given encoder.
     *
     * @param airing
     * @param encoder
     */
    void schedule(Schedulable airing, int encoder);

    /**
     * The airing is in conflict with the max number of encoders. Further permuting can be halted
     * early by way of the return value.
     *
     * @param airing that is inconflict.
     * @return true to continue, false to halt scheduling.
     */
    boolean conflictSchedule(Schedulable airing);
  }

  /**
   * Generates a schedule using a greedy algorithm. If there exists an element that has N overlaps
   * at any moment its scheduled, the schedule is invalid (and can't be made with the current
   * schedulables, period).
   *
   * @param mrList sorted list of manual recordings
   * @param numEncs the number of equal encoders available
   * @param collector to notify of new schedules and conflicts
   */
  public static void generateSchedule(
      Schedulable[] mrList, int numEncs, ScheduleCollector collector) {
    generateSchedule(mrList, numEncs, collector, null);
  }

  /**
   * Generates a schedule using a greedy algorithm. If there exists an element that has N overlaps
   * at any moment its scheduled, the schedule is invalid (and can't be made with the current
   * schedulables, period).
   *
   * @param mrList sorted list of manual recordings
   * @param numEncs the number of equal encoders available
   * @param collector to notify of new schedules and conflicts
   * @param alreadyScheduled airings that are bound to an encoder
   */
  public static void generateSchedule(
      Schedulable[] mrList, int numEncs, ScheduleCollector collector,
      List<EncSchedulable> alreadyScheduled) {
    if(GLOB_DEBUG) printTestSet(mrList);

    int conflicts = 0;
    // The set of free encoders
    TreeSet<Integer> freeEncoders = new TreeSet<Integer>();
    // Create the set of encoders to work with
    for (int i = 0; i < numEncs; i++) {
      freeEncoders.add(i);
    }

    // This is the set of encoders that have jobs.
    TreeSet<EncSchedulable> workingEncoders =
        new TreeSet<EncSchedulable>(EncSchedulable.TIME_END_COMPARATOR);
    // There may already be jobs recording, in which case we need to pre-load the working encoders
    if (alreadyScheduled != null) {
      for (EncSchedulable prework : alreadyScheduled) {
        if (GLOB_DEBUG) System.out.println("forced: " + prework);
        freeEncoders.remove(prework.enc);
        workingEncoders.add(prework);
      }
    }

    for (Schedulable sched : mrList) {
      // NOTE: We're simulating work done by the encoders recording over time; update scheduleNow so
      // we can first clear up the working queue of any encoders that have become free.
      long scheduleNow = sched.getSchedulingStart();
      while (!workingEncoders.isEmpty()) {
        EncSchedulable enc = workingEncoders.first();
        if (enc.scheduable.getSchedulingEnd() <= scheduleNow) {
          workingEncoders.remove(enc);
          freeEncoders.add(enc.enc);
        } else {
          break;
        }
      }

      // Get the first free encoder.
      if (freeEncoders.isEmpty()) {
        if (GLOB_DEBUG) System.out.println("conflicts[" + conflicts++ + "]: " + sched);
        if (!collector.conflictSchedule(sched)) return;
      } else {
        Integer first = freeEncoders.first();
        freeEncoders.remove(first);
        EncSchedulable enc = new EncSchedulable(first, sched);
        workingEncoders.add(enc);
        if (GLOB_DEBUG) System.out.println("scheduled: " + enc);
        collector.schedule(enc.scheduable, enc.enc);
      }
    }
  }

  /**
   * Collect the generated schedule and map to an EncAir list. Fail fast.
   */
  static class EncScheduleCollector implements ScheduleCollector {

    Vector<EncAir> schedule;
    EncoderSchedule[] encs;
    boolean conflicts;

    EncScheduleCollector(EncoderSchedule[] encs) {
      schedule = new Vector<EncAir>();
      this.encs = encs;
      conflicts = false;
    }

    public void schedule(Schedulable airing, int encoder) {
      schedule.add(new EncAir((Airing) airing, encs[encoder].capDev, true));
    }

    public boolean conflictSchedule(Schedulable airing) {
      conflicts = true;
      return false;
    }

    @Override
    public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append("conflicts: " + conflicts + ", ");
      for (EncAir sched : schedule) {
        sb.append(sched + ",");
      }
      return sb.toString();
    }
  }

  /**
   * Do not worry about figuring out the schedule, just test if its possible.
   * Fail Fast
   */
  static class FastScheduleTestCollector implements ScheduleCollector {

    boolean conflicts;

    FastScheduleTestCollector() {
      conflicts = false;
    }

    public void schedule(Schedulable airing, int encoder) {}

    public boolean conflictSchedule(Schedulable airing) {
      conflicts = true;
      return false;
    }

    @Override
    public String toString() {
      return "FastScheduleTestCollector; conflicts: " + conflicts;
    }
  }

  // This does NOT include the alreadyScheduled items in the returned Vector but it does account for them
  Vector<EncAir> generateSingleMultiTunerSchedulingPermutation(Vector<Airing> airList,
      EncoderSchedule[] encs, Vector<EncAir> alreadyScheduled)
  {
    if (airList.isEmpty())
    {
      return new Vector<EncAir>();
    }

    airList = new Vector<Airing>(airList);  // make a local clone.

    // the new iterative way
    int numMRs = airList.size();
    int[] encoderPermutationLUT = new int[numMRs]; // each entry indexes into encs[]; starts as numMRs wide
    boolean countNeedsInc = false; // Unclean pass; MR @ forceRollBit failed encoder matching or overlaps
    int forceRollBit = numMRs - 1; // Last MR to fail encoder matching / overlapped current permutation
    int maxViolatorNum = 0; // Max MR to fail tests
    long maxTimeForSMTSP = Sage.getLong("scheduler/max_time_for_smtsp", 30000); // Clamp down on failures after oh so much time.
    long startTime= Sage.eventTime();
    while (!airList.isEmpty())
    {
      if (countNeedsInc)
      {
        // Increment/roll the permute counter, and if we're done then break out
        boolean rollThisOne = true;

        // Walk backwards through encoder permutations (for all MRs), starting for the first
        // conflict and increment the first one that hasn't overflowed the encoder list. If there's
        // an overflow, reset LUT back to zero for that one and keep searching
        // Note: basically this is just a shell game, bumping LUT[forceRollBit] up and trying again
        // or hitting the ceiling and incrementing LUT[forceRollBit-1*] up and trying again.
        // until we fall all the way back to the first MR and kill the max failing MR (rollThisOne)
        for (int i = forceRollBit; i >= 0; i--)
        {
          if (encoderPermutationLUT[i] < encs.length - 1)
          {
            encoderPermutationLUT[i]++;
            rollThisOne = false;
            break;
          }
          else
            encoderPermutationLUT[i] = 0;
        }
        if (rollThisOne || (maxTimeForSMTSP > 0 && Sage.eventTime() - startTime > maxTimeForSMTSP))
        {
          if (!rollThisOne) System.out.println("Scheduler timed out trying to generate simple permutation");
          if (Sage.DBG) System.out.println("SCHEDULER CANNOT GENERATE SIMPLE PERMUTATION!!" + " airList=" + airList + " forced=" + alreadyScheduled);
          if (Sage.DBG) System.out.println("Scheduler is dropping: " + airList.get(maxViolatorNum));
          Airing lostAir = airList.remove(maxViolatorNum);
          Vector<Airing> lostAirVec = new Vector<Airing>();
          lostAirVec.add(lostAir);
          pendingUnresolvedConflicts.put(lostAir, lostAirVec);
          pendingConflicts.put(lostAir, lostAirVec);
          maxViolatorNum = 0;
          countNeedsInc = false;
          numMRs = airList.size();
          encoderPermutationLUT = new int[numMRs];
          forceRollBit = numMRs - 1;
          startTime = Sage.eventTime();
          continue;
        }
        countNeedsInc = false;
      }
      forceRollBit = numMRs - 1;

      // allocate a new vector and add force tunes; again?
      Vector<EncAir> currPerm = new Vector<EncAir>();
      currPerm.addAll(alreadyScheduled);

      // Every time we search, start off clean and allow inserting.
      boolean clean = true;
      // Go through all of the MRs and put in the permutation for the current
      // encoder that its on
      for (int permIdx = 0; permIdx < numMRs && clean; permIdx++)
      {
        Airing currMR = airList.get(permIdx);
        int currPermCount = encoderPermutationLUT[permIdx]; // which one are we currently trying for?
        // Wah-wah; you're station is invalid or doesn't support your quality.
        // Code that calls us will check for station availablilty.
        // Quality should always be the same.
        if (!encs[currPermCount].stationSet.contains(currMR.stationID) ||
            !encs[currPermCount].supportsAirQuality(currMR))
        {
          clean = false;
          forceRollBit = permIdx;
          maxViolatorNum = Math.max(maxViolatorNum, permIdx);
          break;
        }

        // O(N) walk the current, successul schedules and check for overlap if they are on
        // the same encoder as our current permutation.
        // If we hit an overlap; record our index and break out so we can try somethign else
        // else - record 0
        for (int i = 0; i < currPerm.size(); i++)
        {
          EncAir alreadyEnced = currPerm.get(i);
          if (alreadyEnced.capDev.equals(encs[currPermCount].capDev) &&
              doesSchedulingOverlap(alreadyEnced.air, currMR))
          {
            clean = false;
            forceRollBit = permIdx;
            maxViolatorNum = Math.max(maxViolatorNum, permIdx);
            break;
          }
        }
        if (clean)
          currPerm.add(new EncAir(currMR, encs[currPermCount].capDev, true));
      }
      if (!clean)
      {
        // We can't schedule all of the MRs on this permute, it's no good!
        countNeedsInc = true;
        continue;
      }

      // We managed to walk through the whole list
      currPerm.removeAll(alreadyScheduled);
      return currPerm;
    }

    if (Sage.DBG) System.out.println("SCHEDULER CANNOT GENERATE SIMPLE PERMUTATION!!" + " airList=" + airList + " forced=" + alreadyScheduled);
    Vector<EncAir> currPerm = new Vector<EncAir>();
    currPerm.addAll(alreadyScheduled);
    return currPerm;
  }

  /*
   * NOTE: 6/21/05 - JK - This was added to replace generateMultiTunerSchedulingPermutations. That function was
   * dying if you passed it in 8 new MRs while it already had 8 scheduled and you were dual tuner. It died because
   * it parsed far too many iterations. Something this other version of the function was designed to avoid. So we
   * cloned and adapted generateSingleMultiTunerSchedulingPermutation to work for this case too.
   */
  public boolean testMultiTunerSchedulingPermutation(Vector<Airing> airList)
  {
    if (airList.isEmpty())
    {
      return true;
    }

    EncoderSchedule[] encs = encoderScheduleMap.values().toArray(new EncoderSchedule[0]);

    // the new iterative way
    airList = new Vector<Airing>(airList);
    int numMRs = airList.size();
    int[] permuteCounts = new int[numMRs];
    int currRollBit = permuteCounts.length - 1;
    boolean countNeedsInc = false;
    int forceRollBit = permuteCounts.length - 1;
    int maxViolatorNum = 0;
    int loopCount = 0;
    Vector<EncAir> currPerm = new Vector<EncAir>();
    while (!airList.isEmpty())
    {
      if(SDBG && (++loopCount % 1000) == 0) {
        System.out.println("testMultiTunerSchedulingPermutation: loop: " + loopCount);
      }
      if (countNeedsInc)
      {
        // Increment/roll the permute counter, and if we're done then break out
        boolean rollThisOne = true;
        for (int i = forceRollBit; i >= 0; i--)
        {
          if (permuteCounts[i] < encs.length - 1)
          {
            permuteCounts[i]++;
            rollThisOne = false;
            break;
          }
          else
            permuteCounts[i] = 0;
        }
        if (rollThisOne)
        {
          if (SDBG) System.out.println("(test sched) SCHEDULER CANNOT GENERATE SIMPLE PERMUTATION!!" + " airList=" + airList);
          if (SDBG) System.out.println("(test sched) Scheduler is dropping: " + airList.get(maxViolatorNum));
          return false;
        }
        countNeedsInc = false;
      }
      forceRollBit = permuteCounts.length - 1;
      currPerm.clear();
      int permIdx = 0;

      boolean clean = true;
      // Go through all of the MRs and put in the permutation for the current
      // encoder that its on
      for (permIdx = 0; permIdx < numMRs && clean; permIdx++)
      {
        Airing currMR = airList.get(permIdx);
        int currPermCount = permuteCounts[permIdx];
        if (!encs[currPermCount].stationSet.contains(currMR.stationID) ||
            !encs[currPermCount].supportsAirQuality(currMR))
        {
          clean = false;
          forceRollBit = permIdx;
          maxViolatorNum = Math.max(maxViolatorNum, permIdx);
          break;
        }
        for (int i = 0; i < currPerm.size(); i++)
        {
          EncAir alreadyEnced = currPerm.get(i);
          if (alreadyEnced.capDev.equals(encs[currPermCount].capDev) &&
              alreadyEnced.air.doesSchedulingOverlap(currMR))
          {
            clean = false;
            forceRollBit = permIdx;
            maxViolatorNum = Math.max(maxViolatorNum, permIdx);
            break;
          }
        }
        if (clean)
          currPerm.add(new EncAir(currMR, encs[currPermCount].capDev, true));
      }
      if (!clean)
      {
        // We can't schedule all of the MRs on this permute, it's no good!
        countNeedsInc = true;
        continue;
      }
      return true;
    }

    if (SDBG) System.out.println("(test sched) SCHEDULER CANNOT GENERATE SIMPLE PERMUTATION!!" + " airList=" + airList);
    return false;
  }

  private static void printTestSet(Schedulable[] testSet) {
    for(int i = 0; i < testSet.length; i++)
      System.out.println("[" + i + "] " + testSet[i].toString());
  }

  private void exportSchedule(File exportFile)
  {
    java.io.PrintWriter outStream = null;
    try
    {
      outStream = new java.io.PrintWriter(new BufferedWriter(new FileWriter(exportFile)));
      outStream.println("Encoder\tStart Time\tStop Time\tDuration\tChannelName\tChannelNum\tTitle\tManualRecord\tFavorite");
      for (Map.Entry<CaptureDevice, EncoderSchedule> ent : encoderScheduleMap.entrySet())
      {
        String currEncoder = ent.getKey().toString();
        EncoderSchedule es = ent.getValue();
        for (int i = 0; i < es.schedule.size(); i++)
        {
          Airing currAir = es.schedule.get(i);
          Channel c = currAir.getChannel();
          outStream.println(currEncoder + '\t' + Sage.dfClean(currAir.getSchedulingStart()) + '\t' +
              Sage.dfClean(currAir.getSchedulingEnd()) + '\t' + Sage.durFormat(currAir.duration) + '\t' +
              (c == null ? "NA" : c.getName()) + '\t' + currAir.getChannelNum(0) + '\t' +
              currAir.getTitle() + '\t' + (wiz.getManualRecord(currAir) != null) + '\t' +
              god.isLoveAir(currAir));
        }
      }
      outStream.close();
    }
    catch (Exception e)
    {
      if (outStream != null)
      {
        try{outStream.close();}catch (Exception e1){}
      }
    }
  }

  private long getSchedulingStart(Airing a)
  {
    Long x = cachedSchedStarts.get(a);
    if (x != null)
      return x.longValue();
    x = new Long(a.getSchedulingStart());
    cachedSchedStarts.put(a, x);
    return x.longValue();
  }
  private long getSchedulingEnd(Airing a)
  {
    Long x = cachedSchedEnds.get(a);
    if (x != null)
      return x.longValue();
    x = new Long(a.getSchedulingEnd());
    cachedSchedEnds.put(a, x);
    return x.longValue();
  }
  private boolean doesSchedulingOverlap(Airing a1, Airing a2)
  {
    return ((getSchedulingEnd(a1) > getSchedulingStart(a2)) && (getSchedulingStart(a1) < getSchedulingEnd(a2)));
  }
  private boolean doesSchedulingOverlap(Airing a1, long start, long end)
  {
    return ((getSchedulingEnd(a1) > start) && (getSchedulingStart(a1) < end));
  }

  private Wizard wiz;
  private Carny god;

  private boolean alive;

  private Map<Show, Set<Airing>> dontScheduleMap;

  private HashMap<DBObject, Vector<Airing>> pubConflicts;
  private HashMap<DBObject, Vector<Airing>> pubUnresolvedConflicts;
  //private Set dontKnowConflicts;
  private boolean clientDontKnowFlag;

  private Thread schedulerThread;
  private boolean kicked;
  private boolean prepped;

  private Airing[] lookaheadAirs;

  private HashMap<DBObject, Vector<Airing>> pendingConflicts;
  private HashMap<DBObject, Vector<Airing>> pendingUnresolvedConflicts;

  private Map<Integer, Float> scheduleRandoms;

  private Set<String> allQualsSet;

  private Map<CaptureDevice, EncoderSchedule> encoderScheduleMap;

  private Map<Airing, Long> cachedSchedStarts = new HashMap<Airing, Long>();
  private Map<Airing, Long> cachedSchedEnds = new HashMap<Airing, Long>();

  private MediaFile[] wizFileCache; // so we only call it once per update

  private Map<Agent, List<Airing>> agentFileRedMap;
  private Map<Agent, List<Airing>> agentSchedRedMap;

  private long lastNextMustSeeTime; // for tracking this when we go into standby so we don't lose it

  private Map<Integer, Integer> chanTunerQualMap;

  private static class EncAir
  {
    public EncAir(Airing air, CaptureDevice capDev, boolean req)
    {
      this.air = air;
      this.capDev = capDev;
      this.req = req;
    }
    public int hashCode()
    {
      return (air != null ? air.hashCode() : 0) + (capDev != null ? capDev.hashCode() : 0) + (req ? 1 : 0);
    }
    public boolean equals(Object o)
    {
      return (o instanceof EncAir) && ((EncAir) o).air == air && ((EncAir) o).capDev == capDev && ((EncAir) o).req == req;
    }
    public String toString()
    {
      return "EncAir[" + air + " " + capDev + " " + req + ']';
    }
    Airing air;
    CaptureDevice capDev;
    boolean req;
  }

  private class EncoderSchedule
  {
    public EncoderSchedule(CaptureDevice inCapDev)
    {
      capDev = inCapDev;
      schedule = new Vector<Airing>();
      mustSee = new Vector<Airing>();
      pubSchedule = new Vector<Airing>();
      pubMustSee = new Vector<Airing>();
      stationSet = new HashSet<Integer>();
      qualitySet = new HashSet<String>();
    }
    boolean removeMustSee(Airing air)
    {
      if (mustSee.remove(air))
      {
        schedule.remove(air);
        removeFromDontSchedule(air);
        return true;
      }
      return false;
    }
    Set<Airing> forceIntoMustSee(Airing insertMe)
    {
      Set<Airing> rv = new HashSet<Airing>();
      rv.addAll(forceIntoSchedule(insertMe));
      rv.addAll(forceIntoSchedule(mustSee, insertMe));
      return rv;
    }
    Set<Airing> forceIntoSchedule(Airing insertMe)
    {
      return forceIntoSchedule(schedule, insertMe);
    }

    private Set<Airing> forceIntoSchedule(Vector<Airing> schedVec, Airing insertMe)
    {
      Set<Airing> rv = new HashSet<Airing>();
      if (schedVec.contains(insertMe)) return rv;

      long insertMeStart = getSchedulingStart(insertMe);
      long insertMeEnd = getSchedulingEnd(insertMe);
      for (int i = 0; i< schedVec.size(); i++)
      {
        Airing testAir = schedVec.elementAt(i);
        if (doesSchedulingOverlap(testAir, insertMeStart, insertMeEnd))
        {
          Airing deadAir = schedVec.remove(i--);
          removeFromDontSchedule(deadAir);
          rv.add(deadAir);
        }
        else if (getSchedulingStart(testAir) > insertMeStart)
        {
          schedVec.insertElementAt(insertMe, i);
          addToDontSchedule(insertMe);
          return rv;
        }
      }
      schedVec.addElement(insertMe);
      addToDontSchedule(insertMe);
      return rv;
    }

    public boolean supportsAirQuality(Airing air)
    {
      if (!Sage.getBoolean("scheduler/enforce_qualities_in_schedule2", false))
        return true;
      String q = getQualityForAiring(air);
      if (q == null || q.length() == 0 || !allQualsSet.contains(q)) return true;
      return qualitySet.contains(q);
    }

    public String toString()
    {
      return "[Sched=" + schedule + " MustSee=" + mustSee + ']';
    }

    private CaptureDevice capDev;
    private Vector<Airing> schedule;
    private Vector<Airing> mustSee;
    private Vector<Airing> pubSchedule;
    private Vector<Airing> pubMustSee;
    private Set<Integer> stationSet;
    private Set<String> qualitySet;
    private Airing currRecord;
    private boolean isForced;
    private boolean isDesired;
  }

  private static class PotVec extends Vector<Airing> implements Comparator<Object>
  {
    public PotVec()
    {
      super();
    }
    public void sort()
    {
      Arrays.sort(elementData, this);
    }
    // Unsynchronized for performance
    public Airing elementAt(int index) {
      if (index >= elementCount) {
        throw new ArrayIndexOutOfBoundsException(index + " >= " + elementCount);
      }

      return (Airing)elementData[index];
    }
    public int compare(Object o1, Object o2)
    {
      Airing a1 = (Airing) o1;
      Airing a2 = (Airing) o2;
      if ((a1 == null) && (a2 == null)) return 0;
      else if (a1 == null) return 1;
      else if (a2 == null) return -1;

      float w1 = Carny.getInstance().getWP(a1);
      float w2 = Carny.getInstance().getWP(a2);

      if (w1 < w2) return 1;
      else if (w1 == w2) return 0;
      else return -1;
    }
  }

  final boolean preferHD = Sage.getBoolean("prefer_hdtv_recordings_over_sdtv", true);
  private Comparator<DBObject> airingHDSDComparator = new Comparator<DBObject>()
  {
    public int compare(DBObject o1, DBObject o2)
    {
      Airing a1 = (Airing) o1;
      Airing a2 = (Airing) o2;
      if (a1 == a2)
        return 0;
      else if (a1 == null)
        return 1;
      else if (a2 == null)
        return -1;
      if (preferHD && a1.isHDTV() != a2.isHDTV())
      {
        // We care about doing HD over SD and one of these is HD while the other is not. In this case we pick the HD
        // one first.
        return a1.isHDTV() ? -1 : 1;
      }
      if (a1.getStartTime() < a2.getStartTime())
        return -1;
      else if (a2.getStartTime() < a1.getStartTime())
        return 1;
      else
      {
        // So neither of them is marked HD; but still check to see if there's a tuner quality difference
        // (they can't be on the same channel since they're on at the same time)
        Integer a1Qual = chanTunerQualMap.get(a1.stationID);
        Integer a2Qual = chanTunerQualMap.get(a2.stationID);
        int res = (a2Qual == null ? 0 : a2Qual.intValue()) -
          (a1Qual == null ? 0 : a1Qual.intValue());
        if (res != 0)
          return res;

        // As a last resort in the rare case where this is different we can prefer the first run over the rerun
        boolean f1 = a1.isFirstRun();
        boolean f2 = a2.isFirstRun();
        if (f1 != f2)
          return f1 ? -1 : 1;
        return a1.getID() - a2.getID(); // just to keep it consistent
      }
    }
  };

  /**
   * Simple interface to get lower and upper bounds as a long
   */
  static interface LongInterval {
    long getLower();
    long getUpper();
  }

  /**
   * Collect temporally local events and treat as one larger interval
   */
  static class GlobAirings implements LongInterval {
    GlobAirings() {}

    GlobAirings(Airing air) {
      airings.add(air);
    }

    GlobAirings(Airing air, boolean forced) {
      airings.add(air);
      this.forced = forced;
    }

    public long getUpper() {
      if (airings.size() == 0) {
        return Long.MAX_VALUE;
      }
      return airings.getLast().getSchedulingEnd();
    }

    public long getLower() {
      if (airings.size() == 0) {
        return Long.MIN_VALUE;
      }
      return airings.getFirst().getSchedulingStart();
    }

    @Override
    public String toString() {
      StringBuffer buf = new StringBuffer();
      buf.append(" Glob airings[" + airings.size() + "] = ");
      for (Airing air : airings) {
        buf.append(air + ", ");
      }
      return buf.toString();
    }

    LinkedList<Airing> airings = new LinkedList<Airing>();
    boolean forced;

    static final Comparator<GlobAirings> comparator = new Comparator<GlobAirings>() {
      public int compare(GlobAirings o1, GlobAirings o2) {

        final long o1Start = o1.airings.getFirst().getSchedulingStart();
        final long o2Start = o2.airings.getFirst().getSchedulingStart();
        if (o1Start < o2Start) return -1;
        if (o1Start > o2Start) return 1;
        return 0;
      }
    };
  }
}
