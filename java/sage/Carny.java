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

import sage.util.MutableFloat;
import sage.util.MutableInteger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Carny implements Runnable
{
  private static final String GLOBAL_WATCH_COUNT = "global_watch_count";
  private static final String LIMITED_CARNY_INIT = "limited_carny_init";
  // The user can define exactly how many threads will be used. This is not checked against the
  // total core count, so the user could put in a number that's more if they want to experiment.
  // There is no evidence that that would have a positive effect.
  private static final String LIMITED_CARNY_THREADS = "limited_carny_threads";
  // This disables the mapping optimizations in favor of lower memory usage. In my own measurements
  // with about 64k airings and 12k agents, the maps consumed about 2MB. (JS)
  private static final String DISABLE_CARNY_MAPS = "disable_carny_maps";
  // This performs a bit shift of the provided value to the hashes in the map. The goal is to
  // increase collisions which will cause more airings to be grouped together at the expense of a
  // very moderate performance loss.
  private static final String MAP_DENSITY = "carny_map_density";
  static final String CARNY_KEY = "carny";

  private static final long LOOKAHEAD = 14*24*60*60*1000L;
  /*
   * NOTE: I CHANGED THIS ON 7/22/03. I THINK IT'S WHY ITS RECORDING
   * EXTRA STUFF, BECAUSE ANYTHING WILL COME THROUGH WITH AT LEAST THIS PROBABILITY
   */
  private static final float MIN_WP = 0;//1e-6f;
  public static final long SLEEP_PERIOD = 30;
  // The processor count is used to limit the number of threads we will spin up for agent processing.
  public static final int PROCESSOR_COUNT = Math.max(Runtime.getRuntime().availableProcessors(), 1);

  public static final Object WATCH_MARK_JOB = new Object();
  public static final Object WATCH_REAL_JOB = new Object();
  public static final Object WATCH_CLEAR_JOB = new Object();
  public static final Object STD_JOB = new Object();
  public static final Object REQUIRED_JOB = new Object();
  public static final Object LOVE_JOB = new Object();
  public static final Object LOVE_CLEAR_JOB = new Object();
  public static final Object WASTED_JOB = new Object();

  private static String getJobName(Object jobby)
  {
    if (jobby == WATCH_MARK_JOB)
      return "WatchMark";
    else if (jobby == WATCH_REAL_JOB)
      return "WatchReal";
    else if (jobby == WATCH_CLEAR_JOB)
      return "WatchClear";
    else if (jobby == LOVE_JOB)
      return "Love";
    else if (jobby == LOVE_CLEAR_JOB)
      return "LoveClear";
    else if (jobby == STD_JOB)
      return "Std";
    else if (jobby == REQUIRED_JOB)
      return "Req";
    else if (jobby == WASTED_JOB)
      return "Wasted";
    else return "Unknown";
  }

  public static Carny prime()
  {
    return getInstance();
  }

  private static class CarnyHolder
  {
    public static final Carny instance = new Carny();
  }

  public static Carny getInstance()
  {
    return CarnyHolder.instance;
  }

  private Carny()
  {
    prefs = CARNY_KEY + '/';
    globalWatchCount = Sage.getInt(prefs + GLOBAL_WATCH_COUNT, 0);
    jobs = new Vector<Object[]>();
    alive = true;
    wiz = Wizard.getInstance();
    trends = new int[] { Agent.TITLE_MASK | Agent.FIRSTRUN_MASK, Agent.TITLE_MASK | Agent.RERUN_MASK,
        Agent.CHANNEL_MASK | Agent.CATEGORY_MASK,
        Agent.NETWORK_MASK | Agent.CATEGORY_MASK, Agent.ACTOR_MASK,
        Agent.CHANNEL_MASK | Agent.PR_MASK, Agent.CATEGORY_MASK | Agent.PR_MASK };
    wpMap = Collections.synchronizedMap(new HashMap<Airing, Float>());
    causeMap = Collections.synchronizedMap(new HashMap<Airing, Agent>());
    pots = Pooler.EMPTY_AIRING_ARRAY;
    mustSeeSet = Collections.synchronizedSet(new HashSet<Airing>());
    loveAirSet = Collections.synchronizedSet(new HashSet<Airing>());
    swapMap = new HashMap<Airing, Airing>();
    agentWorkQueue = new ConcurrentLinkedQueue<>();
    agentWorkers = Executors.newCachedThreadPool(new ThreadFactory()
    {
      @Override
      public Thread newThread(Runnable r)
      {
        Thread newThread = new Thread(r);
        newThread.setName("CarnyAgentWorker");
        return newThread;
      }
    });
    prepped = false;
  }

  void lengthyInit(boolean updateStatus)
  {
    if (Sage.client)
    {
      alive = false;
      return;
    }
    if (!updateStatus) doneInit = true;
    stdProcessing();
    doneInit = true;
    if (Sage.getBoolean(LIMITED_CARNY_INIT, true))
      kick();
  }

  public void run()
  {
    if (!doneInit)
    {
      lengthyInit(false);
      SchedulerSelector.getInstance().kick(false);
    }
    while (alive)
    {
      try{

        synchronized (jobs)
        {
          if (jobs.isEmpty())
          {
            if (Sage.DBG) System.out.println("Carny waiting for awhile...");
            try{jobs.wait(60*60000L);}catch(InterruptedException e){}
            continue;
          }
        }
        while (!jobs.isEmpty())
        {
          Object[] currJob = jobs.remove(0);
          if (Sage.DBG) System.out.println("Carny got a " + getJobName(currJob[0]) + " job of " + currJob[1]);
          if (currJob[0] == WATCH_MARK_JOB ||
              currJob[0] == WATCH_REAL_JOB)
          {
            applyWatchData((Airing) currJob[1]);
          }
          else if (currJob[0] == WASTED_JOB)
            applyWasteData((Airing) currJob[1]);
          synchronized (jobs) { jobs.notifyAll(); }
        }
        stdProcessing();

      }catch(Throwable t)
      {
        System.out.println("CARNY ERROR:" + t);
        Sage.printStackTrace(t);
      }
    }
  }

  void goodbye()
  {
    alive = false;
    synchronized (jobs)
    {
      jobs.notifyAll();
    }
    // alive being changed to false will be enough to cause a graceful termination of the agent
    // worker threads.
    agentWorkers.shutdown();
  }

  // Any non-standard processing we should do immediately, profiling calculations
  // can wait, it may also affect them and they'd need to be repeated anyways....
  private boolean shouldContinueStdProcessing(boolean underTimeLimit)
  {
    if (!alive) return false;
    synchronized (jobs)
    {
      for (int i = 0; i < jobs.size(); i++)
        if (jobs.elementAt(i)[0] != STD_JOB)
        {
          if (underTimeLimit || jobs.elementAt(i)[0] == LOVE_JOB)
            return false;
        }
    }
    return true;
  }

  void incWatchCount()
  {
    synchronized (GLOBAL_WATCH_COUNT)
    {
      Sage.putInt(prefs + GLOBAL_WATCH_COUNT, ++globalWatchCount);
    }
  }

  /*
   * This ENSURES we don't have problems with negative watch counts
   */
  protected void notifyOfWatchCount(int wCount)
  {
    if (wCount >= globalWatchCount)
    {
      globalWatchCount = wCount + 1;
      Sage.putInt(prefs + GLOBAL_WATCH_COUNT, globalWatchCount);
    }
  }


  public Agent addFavorite(int agentMask, String title, String category, String subCategory,
      Person person, int role, String rated, String year, String pr, String network,
      String chanName, int slotType, int[] timeslots, String keyword)
  {
    Agent rv = null;
    try {
      wiz.acquireWriteLock(Wizard.AGENT_CODE);
      DBObject[] allAgents = wiz.getRawAccess(Wizard.AGENT_CODE, Wizard.AGENTS_BY_CARNY_CODE);
      Agent bond = new Agent(0);
      bond.agentMask = agentMask;
      bond.title = wiz.getTitleForName(title);
      bond.category = wiz.getCategoryForName(category);
      bond.subCategory = wiz.getSubCategoryForName(subCategory);
      bond.person = person;
      bond.role = role;
      bond.rated = wiz.getRatedForName(rated);
      bond.year = wiz.getYearForName(year);
      bond.pr = wiz.getPRForName(pr);
      bond.network = wiz.getNetworkForName(network);
      bond.setChannelName((chanName == null) ? "" : chanName);
      bond.slotType = slotType;
      bond.timeslots = timeslots;
      bond.keyword = (keyword == null) ? "" : keyword;
      int srchIdx = Arrays.binarySearch(allAgents, bond, AGENT_SORTER);
      if (srchIdx < 0)
      {
        rv = wiz.addAgent(agentMask, title, category, subCategory,
            person, role, rated, year, pr, network, chanName, slotType, timeslots, keyword);
      }
      else
      {
        return (Agent)allAgents[srchIdx];
      }
    } finally {
      wiz.releaseWriteLock(Wizard.AGENT_CODE);
    }

    long defaultFavoriteStartPadding = Sage.getLong("default_favorite_start_padding", 0);
    long defaultFavoriteStopPadding = Sage.getLong("default_favorite_stop_padding", 0);
    if (defaultFavoriteStartPadding != 0)
    {
      rv.setStartPadding(defaultFavoriteStartPadding);
    }
    if (defaultFavoriteStopPadding != 0)
    {
      rv.setStopPadding(defaultFavoriteStopPadding);
    }

    handleAddedFavorite(rv);

    submitJob(new Object[] { LOVE_JOB, null });
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.FAVORITE_ADDED,
        new Object[] { sage.plugin.PluginEventManager.VAR_FAVORITE, rv });
    return rv;
  }

  /**
   * Update the internal Carny state after a favorite has been added (or enabled)
   * @param rv The Favorite that was updated
   */
  private void handleAddedFavorite(Agent rv)
  {
    // LOVESET UPDATE Make the updates to the loveSet that need to be & sync the clients
    // For add, we just add all of the Airings that match this Favorite to the loveAirSet
    List<Airing> airsToAdd = new ArrayList<Airing>();
    long start = Sage.time();
    boolean keywordTest = (rv.agentMask&(Agent.KEYWORD_MASK)) == (Agent.KEYWORD_MASK) &&
        !Sage.getBoolean("use_legacy_keyword_favorites", true);
    if(keywordTest) {
      Show[] shows = wiz.searchShowsByKeyword(rv.getKeyword());
      for (Show show : shows) {
        Airing[] airings = wiz.getAirings(show, 0);
        for(Airing air : airings) {
          if(rv.followsTrend(air,true, null, true)) {
            airsToAdd.add(air);
          }
        }
      }
      start = Sage.time() - start;
      if (Sage.DBG) System.out.println("addFavorite: Time to process lucene search(" + rv.getKeyword() + "): "
          + start + ". added " + airsToAdd.size() + " shows.");
    } else {
      DBObject[] rawAirs = wiz.getRawAccess(Wizard.AIRING_CODE, Wizard.AIRINGS_BY_CT_CODE);
      StringBuilder sbCache = new StringBuilder();
      for (int i = 0; i < rawAirs.length; i++)
      {
        Airing a = (Airing) rawAirs[i];
        if (a != null && rv.followsTrend(a, true, sbCache))
          airsToAdd.add(a);
      }
      start = Sage.time() - start;
      if (Sage.DBG) System.out.println("addFavorite: Time to process rawAirs[" + rawAirs.length + "]: " + start);
    }
    boolean dontScheduleThisAgent = false;
    if (rv.testAgentFlag(Agent.DONT_AUTODELETE_FLAG) &&
        rv.getAgentFlag(Agent.KEEP_AT_MOST_MASK) > 0)
    {
      int fileCount = 0;
      for (int i = 0; i < airsToAdd.size(); i++)
      {
        MediaFile mf = wiz.getFileForAiring(airsToAdd.get(i));
        if (mf != null && mf.isCompleteRecording())
          fileCount++;
      }
      int keepAtMost = rv.getAgentFlag(Agent.KEEP_AT_MOST_MASK);
      if (fileCount >= keepAtMost)
      {
        dontScheduleThisAgent = true;
      }
    }

    if (!airsToAdd.isEmpty())
    {
      synchronized (this)
      {
        loveAirSet.addAll(airsToAdd);
        for (int i = 0; i < airsToAdd.size(); i++)
        {
          // Make these 1.0 in the WP map
          Airing a = airsToAdd.get(i);
          if (!a.isWatchedForSchedulingPurpose() && a.time < Sage.time() + Sage.getLong("scheduling_lookahead", LOOKAHEAD) &&
              a.time >= Sage.time() - Scheduler.SCHEDULING_LOOKBEHIND &&
              a.stationID != 0)
          {
            wpMap.put(a, new Float(1.0f));
            if (!dontScheduleThisAgent)
            {
              mustSeeSet.add(a);
            }
          }
          causeMap.put(a, rv);
        }
        Set<Airing> tempSet = new HashSet<Airing>(Arrays.asList(pots));
        tempSet.addAll(airsToAdd);
        pots = tempSet.toArray(Pooler.EMPTY_AIRING_ARRAY);
      }
      clientSyncAll();
      SchedulerSelector.getInstance().kick(true);
    }
  }

  public Agent updateFavorite(Agent fav, int agentMask, String title, String category, String subCategory,
      Person person, int role, String rated, String year, String pr, String network,
      String chanName, int slotType, int[] timeslots, String keyword)
  {
    if (fav == null) return null;
    List<Agent> allFavs = new ArrayList<Agent>();
    Agent oldFav = (Agent) fav.clone();
    Agent bond = (Agent) fav.clone();
    bond.agentMask = agentMask;
    bond.title = wiz.getTitleForName(title);
    bond.category = wiz.getCategoryForName(category);
    bond.subCategory = wiz.getSubCategoryForName(subCategory);
    bond.person = person;
    bond.role = role;
    bond.rated = wiz.getRatedForName(rated);
    bond.year = wiz.getYearForName(year);
    bond.pr = wiz.getPRForName(pr);
    bond.network = wiz.getNetworkForName(network);
    bond.chanName = (chanName == null) ? "" : chanName;
    bond.slotType = slotType;
    bond.timeslots = timeslots;
    bond.keyword = (keyword == null) ? "" : keyword;
    if (AGENT_SORTER.compare(fav, bond) == 0)
      return fav; // no changes
    try {
      wiz.acquireWriteLock(Wizard.AGENT_CODE);
      DBObject[] allAgents = wiz.getRawAccess(Wizard.AGENT_CODE, Wizard.AGENTS_BY_CARNY_CODE);
      int srchIdx = Arrays.binarySearch(allAgents, bond, AGENT_SORTER);
      if (srchIdx >= 0)
      {
        // Attempt to modify a Favorite to match one that already exists. Do NOT allow this
        // because it violates uniqueness in sorting of AGENTS_BY_CARNY_CODE.
        return (Agent)allAgents[srchIdx];
      }
      if (Sage.DBG) System.out.println("Updating Favorite: " + fav + " to " + bond);
      fav.update(bond);
      wiz.logUpdate(fav, Wizard.AGENT_CODE);
      // IMPORTANT: We also have to resort the index because its dependent on the agentMask
      // for sorting and that's what we may have just changed here!
      wiz.check(Wizard.AGENT_CODE);
      allAgents = wiz.getRawAccess(Wizard.AGENT_CODE, Wizard.AGENTS_BY_CARNY_CODE);
      for (int i = 0; i < allAgents.length; i++)
      {
        Agent currAgent = (Agent) allAgents[i];
        if (currAgent != null && ((currAgent.agentMask & Agent.LOVE_MASK) == Agent.LOVE_MASK))
          allFavs.add(currAgent);
      }
    } finally {
      wiz.releaseWriteLock(Wizard.AGENT_CODE);
    }
    // LOVESET UPDATE Make the updates to the loveSet that need to be & sync the clients
    ArrayList<Airing> airsThatMayDie = new ArrayList<Airing>();
    ArrayList<Airing> airsThatWillSurvive = new ArrayList<Airing>();
    DBObject[] airs;
    boolean keywordTest = ((oldFav.agentMask&fav.agentMask)&(Agent.KEYWORD_MASK)) == (Agent.KEYWORD_MASK) &&
        !Sage.getBoolean("use_legacy_keyword_favorites", true);
    if(keywordTest) {
      // Slim the haystack for finding needles faster.
      Set<Airing> airingsHaystack = new HashSet<Airing>();
      Show[] shows = wiz.searchShowsByKeyword(fav.getKeyword());
      for (Show show : shows) {
        Airing[] airings = wiz.getAirings(show, 0);
        Collections.addAll(airingsHaystack, airings);
      }
      shows = wiz.searchShowsByKeyword(oldFav.getKeyword());
      for (Show show : shows) {
        Airing[] airings = wiz.getAirings(show, 0);
        Collections.addAll(airingsHaystack, airings);
      }
      airs = airingsHaystack.toArray(Pooler.EMPTY_AIRING_ARRAY);
    } else {
      airs = wiz.getRawAccess(Wizard.AIRING_CODE, Wizard.AIRINGS_BY_CT_CODE);
    }

    StringBuilder sbCache = new StringBuilder();
    for (int i = 0; i < airs.length; i++)
    {
      Airing a = (Airing) airs[i];
      if (a != null)
      {
        if (fav.followsTrend(a, true, sbCache, keywordTest))
          airsThatWillSurvive.add(a);
        else if (oldFav.followsTrend(a, true, sbCache, keywordTest))
        {
          // If there's another favorite that matches this, then we need to hold onto it
          boolean airWasSaved = false;
          for (int j = 0; j < allFavs.size(); j++)
          {
            Agent otherFav = allFavs.get(j);
            if (otherFav.followsTrend(a, false, sbCache))
            {
              airWasSaved = true;
              break;
            }
          }
          if (!airWasSaved)
            airsThatMayDie.add(a);
        }
      }
    }
    boolean dontScheduleThisAgent = false;
    if (fav.testAgentFlag(Agent.DONT_AUTODELETE_FLAG) &&
        fav.getAgentFlag(Agent.KEEP_AT_MOST_MASK) > 0)
    {
      int fileCount = 0;
      for (int i = 0; i < airsThatWillSurvive.size(); i++)
      {
        MediaFile mf = wiz.getFileForAiring(airsThatWillSurvive.get(i));
        if (mf != null && mf.isCompleteRecording())
          fileCount++;
      }
      int keepAtMost = fav.getAgentFlag(Agent.KEEP_AT_MOST_MASK);
      if (fileCount >= keepAtMost)
      {
        dontScheduleThisAgent = true;
      }
    }
    if (!airsThatMayDie.isEmpty() || !airsThatWillSurvive.isEmpty())
    {
      airsThatMayDie.removeAll(airsThatWillSurvive);
      synchronized (this)
      {
        synchronized (loveAirSet)
        {
          loveAirSet.removeAll(airsThatMayDie);
          loveAirSet.addAll(airsThatWillSurvive);
        }
        for (int i = 0; i < airsThatWillSurvive.size(); i++)
        {
          // Make these 1.0 in the WP map
          Airing a = airsThatWillSurvive.get(i);
          if (!a.isWatchedForSchedulingPurpose() && a.time < Sage.time() + Sage.getLong("scheduling_lookahead", LOOKAHEAD) &&
              a.time >= Sage.time() - Scheduler.SCHEDULING_LOOKBEHIND &&
              a.stationID != 0)
          {
            wpMap.put(a, 1.0f);
            if (!dontScheduleThisAgent)
            {
              mustSeeSet.add(a);
            }
          }
          causeMap.put(a, fav);
        }
        mustSeeSet.removeAll(airsThatMayDie);
        /*
         * NOTE: We're clearing the WP and the cause for any Airing that was removed from Favorite status.
         * This is a little more harsh than we need to be, but this'll get fixed up on the next Carny round.
         * The alternative involves a fair amount of extra processing here that we don't really want to do
         * UPDATE: Narflex - 9/18/08 - I had this delete a Favorite of mine that I wanted and realized a better
         * way to do this. The WP should be set to 0.9 for these guys since they were just a Fav and should likely be
         * retained. Then we'll clean that up on the next Carny round. Better than letting them drop off into
         * oblivion temporarily and risk a delete cycle kicking in that destroys the content.
         */
        Float oldFavWP = 0.9f;
        synchronized (wpMap)
        {
          for (Airing air : airsThatMayDie) {
            wpMap.put(air, oldFavWP);
          }
        }

        //causeMap.keySet().removeAll(airsThatMayDie);
        //wpMap.keySet().removeAll(airsThatMayDie);
        Set<Airing> tempSet = new HashSet<Airing>(Arrays.asList(pots));
        tempSet.addAll(airsThatWillSurvive);
        //tempSet.removeAll(airsThatMayDie);
        pots = tempSet.toArray(Pooler.EMPTY_AIRING_ARRAY);
      }
      clientSyncAll();
      SchedulerSelector.getInstance().kick(true);
    }
    submitJob(new Object[] { LOVE_JOB, null });
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.FAVORITE_MODIFIED,
        new Object[] { sage.plugin.PluginEventManager.VAR_FAVORITE, fav });
    return fav;
  }
  public void removeFavorite(Agent fav)
  {
    if (Sage.DBG) System.out.println("Removing Favorite: " + fav);
    List<Agent> allFavs = new ArrayList<Agent>();
    try {
      wiz.acquireWriteLock(Wizard.AGENT_CODE);
      wiz.removeAgent(fav);
      DBObject[] allAgents = wiz.getRawAccess(Wizard.AGENT_CODE, Wizard.AGENTS_BY_CARNY_CODE);
      for (int i = 0; i < allAgents.length; i++)
      {
        Agent currAgent = (Agent) allAgents[i];
        if (currAgent != null && ((currAgent.agentMask & Agent.LOVE_MASK) == Agent.LOVE_MASK))
          allFavs.add(currAgent);
      }
    } finally {
      wiz.releaseWriteLock(Wizard.AGENT_CODE);
    }

    handleRemovedFavorite(fav, allFavs);

    submitJob(new Object[] { LOVE_JOB, null });
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.FAVORITE_REMOVED,
        new Object[] { sage.plugin.PluginEventManager.VAR_FAVORITE, fav });
  }

  /**
   * Update the internal Carny state after a favorite has been removed (or disabled)
   * @param fav The Favorite that was removed
   * @param allFavs The collection of all Favorites
   */
  private void handleRemovedFavorite(Agent fav, List<Agent> allFavs)
  {
    // LOVESET UPDATE Make the updates to the loveSet that need to be & sync the clients
    List<Airing> airsThatMayDie = new ArrayList<Airing>();
    DBObject[] airs;
    boolean keywordTest = (fav.agentMask & (Agent.KEYWORD_MASK)) == (Agent.KEYWORD_MASK) &&
        !Sage.getBoolean("use_legacy_keyword_favorites", true);
    if(keywordTest) {
      // Slim the haystack for finding needles faster.
      ArrayList<Airing> airingsHaystack = new ArrayList<Airing>();
      Show[] shows = wiz.searchShowsByKeyword(fav.getKeyword());
      for (Show show : shows) {
        Airing[] airings = wiz.getAirings(show, 0);
        Collections.addAll(airingsHaystack, airings);
      }
      airs = airingsHaystack.toArray(Pooler.EMPTY_AIRING_ARRAY);
    } else {
      airs = wiz.getRawAccess(Wizard.AIRING_CODE, Wizard.AIRINGS_BY_CT_CODE);
    }
    StringBuilder sbCache = new StringBuilder();
    boolean resyncAll = false;
    for (int i = 0; i < airs.length; i++)
    {
      Airing a = (Airing)airs[i];
      if (a != null && fav.followsTrend(a, true, sbCache, keywordTest))
      {
        // If there's another favorite that matches this, then we need to hold onto it
        boolean airWasSaved = false;
        for (int j = 0; j < allFavs.size(); j++)
        {
          Agent otherFav = allFavs.get(j);
          if (otherFav.followsTrend(a, false, sbCache))
          {
            // Also swap out the cause agent
            resyncAll = true;
            synchronized (this)
            {
              causeMap.put(a, otherFav);
            }
            airWasSaved = true;
            break;
          }
        }
        if (!airWasSaved)
          airsThatMayDie.add(a);
      }
    }
    if (!airsThatMayDie.isEmpty())
    {
      synchronized (this)
      {
        loveAirSet.removeAll(airsThatMayDie);
        mustSeeSet.removeAll(airsThatMayDie);
        /*
         * NOTE: We're clearing the WP and the cause for any Airing that was removed from Favorite status.
         * This is a little more harsh than we need to be, but this'll get fixed up on the next Carny round.
         * The alternative involves a fair amount of extra processing here that we don't really want to do
         */
        synchronized (causeMap)
        {
          causeMap.keySet().removeAll(airsThatMayDie);
        }
        synchronized (wpMap)
        {
          wpMap.keySet().removeAll(airsThatMayDie);
        }
        Set<Airing> tempSet = new HashSet<Airing>(Arrays.asList(pots));
        tempSet.removeAll(airsThatMayDie);
        pots = tempSet.toArray(Pooler.EMPTY_AIRING_ARRAY);
      }
      clientSyncAll();
      SchedulerSelector.getInstance().kick(true);
    }
    else if (resyncAll)
    {
      clientSyncAll();
      SchedulerSelector.getInstance().kick(true);
    }

    clientSyncLoves();
  }

  /**
   * Set the enabled/disabled state of a favorite
   * @param fav The Favorite to enable/disable
   * @param enabled true if the given favorite should be enabled, false if it should be disabled
   */
  public void enableFavorite(Agent fav, boolean enabled)
  {
      //If the DISabled flag is equal to the ENabled parameter, then the call to this function should
      //  change the state of the given Agent, otherwise nothing changes
      if(fav.testAgentFlag(Agent.DISABLED_FLAG) == enabled)
      {
          //first update the agent flag
          setAgentFlags(fav, Agent.DISABLED_FLAG, enabled?0:Agent.DISABLED_FLAG);

          //Next update the Carny internal state
          if(enabled) {
              handleAddedFavorite(fav);
          } else {
              handleRemovedFavorite(fav, Arrays.asList(Wizard.getInstance().getFavorites()));
          }

          submitJob(new Object[] { LOVE_JOB, null });
          sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.FAVORITE_MODIFIED,
              new Object[] { sage.plugin.PluginEventManager.VAR_FAVORITE, fav });
      }
  }

  private void clientSyncAll()
  {
    synchronized (listeners)
    {
      SageTV.incrementQuanta();
      // In case any get removed along the way
      ProfilingListener[] listData = listeners.toArray(new ProfilingListener[0]);
      for (int i = 0; i < listData.length; i++)
      {
        if (!listData[i].updateLoves(loveAirSet)) // we used to not do this, but it does need to be synced 2/4/2004
          continue;
        if (!listData[i].updateWPMap(wpMap))
          continue;
        if (!listData[i].updateCauseMap(causeMap))
          continue;
        if (!listData[i].updateMustSees(mustSeeSet))
          continue;
      }
    }
  }

  private void clientSyncLoves()
  {
    synchronized (listeners)
    {
      SageTV.incrementQuanta();
      // In case any get removed along the way
      ProfilingListener[] listData = listeners.toArray(new ProfilingListener[0]);
      for (int i = 0; i < listData.length; i++)
      {
        listData[i].updateLoves(loveAirSet);
      }
    }
  }

  synchronized boolean fullClientUpdate(ProfilingListener updateMe)
  {
    if (!updateMe.updateCauseMap(causeMap))
      return false;
    if (!updateMe.updateWPMap(wpMap))
      return false;
    if (!updateMe.updateMustSees(mustSeeSet))
      return false;
    if (!updateMe.updateLoves(loveAirSet))
      return false;
    return true;
  }

  public void addDontLike(Airing air, boolean manual)
  {
    submitWasteJob(air, true, manual);
    SchedulerSelector.getInstance().kick(true);
  }
  public void removeDontLike(Airing air)
  {
    submitWasteJob(air, false, true);
    SchedulerSelector.getInstance().kick(true);
  }
  private void submitWasteJob(Airing air, boolean doWaste, boolean manual)
  {
    // Don't track Wasted for non-TV content
    if (!air.isTV()) return;
    if (doWaste)
    {
      wiz.addWasted(air, manual);
      submitJob(new Object[] { WASTED_JOB, air });
    }
    else if (wiz.getWastedForAiring(air) != null)
      wiz.removeWasted(wiz.getWastedForAiring(air));
    else {
      // Just clear it for the Show
      Show s = air.getShow();
      if (s != null) {
        s.setDontLike(false);
      }
    }
  }

  void submitJob(Object[] jobData)
  {
    synchronized (jobs)
    {
      jobs.addElement(jobData);
      jobs.notifyAll();
    }
  }

  public void kick()
  {
    synchronized (jobs)
    {
      jobs.addElement(new Object[] { STD_JOB, null });
      jobs.notifyAll();
    }
  }
  void kickHard()
  {
    synchronized (jobs)
    {
      jobs.addElement(new Object[] { REQUIRED_JOB, null });
      jobs.notifyAll();
    }
  }

  private void applyWatchData(Airing watchAir)
  {
    // First we try to find any Agents that apply to this already.
    // This is how we can know if there's one with a matching template already,
    // and be able to modify the information the agent is based on.
    //DBObject[] allAgents = wiz.getRawAccess(Wizard.AGENT_CODE, Wizard.AGENTS_BY_CARNY_CODE);
    //System.out.println("AGENTS=" + Arrays.asList(allAgents));
    Show s = watchAir.getShow();
    // Don't do profiling on non-TV watches
    if (s == null || !watchAir.isTV()) return;

    // Look at each type, and then see if each trend for that
    // type is satisfied.
    int personIdx = 0;
    Agent bond = new Agent(0);
    for (int trendPos = 0; trendPos < trends.length; trendPos++)
    {
      int trend = trends[trendPos];
      bond.agentMask = trend;
      bond.title = bond.network = bond.category = bond.subCategory =
          bond.rated = bond.pr = bond.year = null;
      bond.person = null;
      bond.chanName = "";
      if ((trend & Agent.TITLE_MASK) == Agent.TITLE_MASK)
      {
        // Don't do title tracking for movies
        if (s.title == null || s.getExternalID().startsWith("MV"))
          continue;
        bond.title = s.title;
      }
      if ((trend & Agent.FIRSTRUN_MASK) == Agent.FIRSTRUN_MASK && !watchAir.isFirstRun())
        continue;
      if ((trend & Agent.RERUN_MASK) == Agent.RERUN_MASK && watchAir.isFirstRun())
        continue;
      if ((trend & Agent.CHANNEL_MASK) == Agent.CHANNEL_MASK)
        bond.chanName = watchAir.getChannelName();
      if ((trend & Agent.CATEGORY_MASK) == Agent.CATEGORY_MASK)
      {
        if (s.categories.length == 0)
          continue;
        bond.category = s.categories[0];
        bond.subCategory = (s.categories.length > 1 ? s.categories[1] : null);
      }
      if ((trend & Agent.NETWORK_MASK) == Agent.NETWORK_MASK)
      {
        if (watchAir.getChannel() == null || watchAir.getChannel().network == null)
          continue;
        bond.network = watchAir.getChannel().network;
      }
      if ((trend & Agent.ACTOR_MASK) == Agent.ACTOR_MASK)
      {
        if (s.people.length == 0)
          continue;
        bond.person = s.people[personIdx];
        personIdx++;
        if (personIdx < s.people.length)
          trendPos--;
        else
          personIdx = 0;
      }
      if ((trend & Agent.YEAR_MASK) == Agent.YEAR_MASK)
      {
        if (s.year == null)
          continue;
        bond.year = s.year;
      }
      if ((trend & Agent.PR_MASK) == Agent.PR_MASK)
      {
        if (s.pr == null)
          continue;
        bond.pr = s.pr;
      }
      if ((trend & Agent.RATED_MASK) == Agent.RATED_MASK)
      {
        if (s.rated == null)
          continue;
        bond.rated = s.rated;
      }
      if ((trend & Agent.FULLSLOT_MASK) == Agent.FULLSLOT_MASK)
      {
        bond.slotType = BigBrother.FULL_ALIGN;
        bond.timeslots = new int[] { Profiler.determineSlots(watchAir)[0] };
      }
      else if ((trend & Agent.DAYSLOT_MASK) == Agent.DAYSLOT_MASK)
      {
        bond.slotType = BigBrother.DAY_ALIGN;
        bond.timeslots = new int[] { Profiler.determineSlots(watchAir)[0] };
      }
      else if ((trend & Agent.TIMESLOT_MASK) == Agent.TIMESLOT_MASK)
      {
        bond.slotType = BigBrother.TIME_ALIGN;
        bond.timeslots = new int[] { Profiler.determineSlots(watchAir)[0] };
      }

      try {
        wiz.acquireWriteLock(Wizard.AGENT_CODE);
        int srchIdx = Arrays.binarySearch(
            wiz.getRawAccess(Wizard.AGENT_CODE, Wizard.AGENTS_BY_CARNY_CODE),
            bond, AGENT_SORTER);
        if (srchIdx < 0)
        {
          wiz.addAgent(trend, (bond.title == null) ? null : bond.title.name,
              (bond.category == null) ? null : bond.category.name,
              (bond.subCategory == null) ? null : bond.subCategory.name,
              bond.person,
              (bond.person == null) ? 0 : Show.ALL_ROLES,
              (bond.rated == null) ? null : bond.rated.name,
              (bond.year == null) ? null : bond.year.name,
              (bond.pr == null) ? null : bond.pr.name,
              (bond.network == null) ? null : bond.network.name,
              bond.chanName, bond.slotType, bond.timeslots, bond.keyword);
        }
      } finally {
        wiz.releaseWriteLock(Wizard.AGENT_CODE);
      }
    }
  }

  private void applyWasteData(Airing wasteAir)
  {
    // First we try to find any Agents that apply to this already.
    // This is how we can know if there's one with a matching template already,
    // and be able to modify the information the agent is based on.
    Show s = wasteAir.getShow();
    // We don't track 'wasted' objects on embedded so disable that here
    if (s == null || !wasteAir.isTV()) return;
    Stringer stit = s.title;
    Agent[] allAgents = wiz.getAgents();
    StringBuilder sbCache = new StringBuilder();
    for (int i = 0; i < allAgents.length; i++)
    {
      if (allAgents[i] == null) continue;

      Agent bond = allAgents[i];
      if ((bond.agentMask & Agent.TITLE_MASK) == Agent.TITLE_MASK &&
          bond.title == stit && bond.followsTrend(wasteAir, false, sbCache))
      {
        // There's already a tracker on this title, we're done
        return;
      }
    }

    wiz.addAgent(Agent.TITLE_MASK | (wasteAir.isFirstRun() ? Agent.FIRSTRUN_MASK : Agent.RERUN_MASK), stit.name,
        null, null, null, 0, null, null, null, null, null,
        0, null, null);
  }

  private void addToMaps(Integer hash, Airing airing, Map<Integer, List<Airing>> buildMap)
  {
    // We did try to limit the number of hashes we will actually map based on what the agents say
    // they will use, but what ends up happening is a race condition leading to an agent that can't
    // find an airing that it should have been able to find if we had not already filtered
    // everything, so we map everything regardless of if we know it will be used.
    List<Airing> mappedAirings = buildMap.get(hash);
    // We are using an array list to keep things in order so that we can do a binary search later.
    // Most of the data with the exception of airings sorted by channel/time should already be
    // sorted, and we will do a final sort on that one specifically.
    if (mappedAirings == null)
    {
      // 20 is selected based on random data samples. Often we only have one to two airings per
      // array which can make this a really inefficient selection, but more often, we are close to
      // 16 airings. This is admittedly a super small optimization, but we know we are going to put
      // at least one airing in here, so creating the array is always going to happen at least once
      // and this might keep us from needing to create a new one to expand.
      mappedAirings = new ArrayList<>(20);
      buildMap.put(hash, mappedAirings);
      mappedAirings.add(airing);
    }
    else
    {
      // If we might be adding a duplicate, it will be the last entry because we call this multiple
      // times for the same airing and it might have redundant hashes.
      if (mappedAirings.get(mappedAirings.size() - 1) != airing)
      {
        mappedAirings.add(airing);
      }
    }
  }

  // Re-build the airing map for wasted so we don't need to keep re-getting wasted from the wizard.
  // Wasted will cache it's airing on the first get which also keeps the requests at a minimum.
  // This also keeps the airing binary sort which is desirable.
  private Map<Integer, Wasted[]> buildWastedMap(Airing[] airings, int bitShift)
  {
    Map<Integer, Airing[]> airsMap = buildMap(airings, bitShift);
    Map<Integer, Wasted[]> wastedMap = new HashMap<>((int)(airsMap.size() / 0.75) + 1);

    for (Map.Entry<Integer, Airing[]>  entry : airsMap.entrySet())
    {
      Airing airs[] = entry.getValue();
      Wasted wasted[] = new Wasted[airs.length];
      int w = 0;
      for (int i = 0; i < airs.length; i++)
      {
        Wasted currWasted = wiz.getWastedForAiring(airs[i]);
        if (currWasted != null)
          wasted[w++] = currWasted;
      }
      // Somehow we lost one while re-building.
      if (w != airs.length)
        wasted = Arrays.copyOf(wasted, w);
      wastedMap.put(entry.getKey(), wasted);
    }
    return wastedMap;
  }

  private Map<Integer, Airing[]> buildMap(Airing airings[], int bitShift)
  {
    Map<Integer, List<Airing>> buildMap = new HashMap<>();
    for (int i = 0, airingsSize = airings.length; i < airingsSize; i++)
    {
      Airing airing = airings[i];
      Show show = airing.getShow();
      if (show == null)
        continue;

      int currentHash = show.title == null ? 0 : show.title.ignoreCaseHash;
      addToMaps((currentHash >>> bitShift), airing, buildMap);

      Person[] people = show.people;
      for (int j = 0, peopleLength = people.length; j < peopleLength; j++)
      {
        Person person = people[j];
        currentHash = person.ignoreCaseHash;
        addToMaps((currentHash >>> bitShift), airing, buildMap);
      }

      if (show.categories != null)
      {
        Stringer[] categories = show.categories;
        // We don't do anything with more than the first and second category entries.
        for (int j = 0, categoriesLength = Math.min(2, categories.length); j < categoriesLength; j++)
        {
          Stringer category = categories[j];
          currentHash = category.ignoreCaseHash;
          addToMaps((currentHash >>> bitShift), airing, buildMap);
        }
      }

      Channel c = airing.getChannel();
      if (c != null)
      {
        // Sometimes there's a channel, but it doesn't have a name. We never match for channels
        // that do not have a name and this ends up being a zero forcing all of the agents to
        // process it even though there isn't a reason to do so.
        if (c.name != null && c.name.length() > 0)
        {
          currentHash = c.name.hashCode();
          addToMaps((currentHash >>> bitShift), airing, buildMap);
        }

        // Same situation with network (even more so).
        if (c.network != null && c.network.name.length() > 0)
        {
          currentHash = c.network.ignoreCaseHash;
          addToMaps((currentHash >>> bitShift), airing, buildMap);
        }
      }

      if (show.rated != null)
      {
        currentHash = show.rated.ignoreCaseHash;
        addToMaps((currentHash >>> bitShift), airing, buildMap);
      }

      if (show.year != null)
      {
        currentHash = show.year.ignoreCaseHash;
        addToMaps((currentHash >>> bitShift), airing, buildMap);
      }

      if (show.pr != null)
      {
        currentHash = show.pr.ignoreCaseHash;
        addToMaps((currentHash >>> bitShift), airing, buildMap);
      }

      // Agents that use the slotType and timeslots without any other criteria need to process all
      // airs because there isn't currently an efficient way to map that data for quick lookups.

      // Agents that use keywords without any other criteria need to process all airs because we
      // have not come up with and efficient way to look them up.
    }

    // We could have this array around for a long time, so let's make sure we have the smallest
    // possible footprint. Also arrays are just faster than lists to iterate.
    Map<Integer, Airing[]> returnMap = new HashMap<>(Math.max (16, (int)(buildMap.size() / 0.75f) + 1));

    for (Map.Entry<Integer, List<Airing>> entry : buildMap.entrySet())
    {
      Airing[] finalAirings = entry.getValue().toArray(Pooler.EMPTY_AIRING_ARRAY);
      returnMap.put(entry.getKey(), finalAirings);
    }
    return returnMap;
  }

  private void stdProcessing()
  {
    String lastMessage = null;
    if (!doneInit)
    {
      lastMessage = Sage.rez("Module_Init_Progress", new Object[] { Sage.rez("Profiler"), new Double(0)});
      Sage.setSplashText(lastMessage);
    }

    boolean disableCarnyMaps = Sage.getBoolean(DISABLE_CARNY_MAPS, false);
    // Windows is still 32-bit, so we use this to keep memory usage down by what can be a
    // substantial amount.
    // 05/20/2017 JS: I did have 1 bit shifted for Linux, but I felt might not work well for
    // everyone, so I changed it to 0.
    int mapBitShift = Sage.getInt(MAP_DENSITY, Sage.WINDOWS_OS ? 16 : 0);
    // Don't let users push this beyond what even makes sense. Always leave at least 9 bits or we
    // should just turn the maps off.
    if (mapBitShift >= 28)
      mapBitShift = 27;
    else if (mapBitShift < 0)
      mapBitShift = 0;
    // This tracks when we started trying to compile the profiler info so we don't keep aborting
    // attempts forever if it takes a long time to figure out
    if (lastCycleCompleteTime >= cycleStartTime)
      cycleStartTime = Sage.eventTime();
    // Go through each Agent and run their think() process.
    DBObject[] allAgents = wiz.getRawAccess(Wizard.AGENT_CODE, Wizard.AGENTS_BY_CARNY_CODE);

    // We don't track music at all (that used to be in Agent.followsTrend)

    synchronized (this)
    {
      // Clear this out since we're getting fresh airings from the DB now
      swapMap.clear();
    }
    // We are getting binary sorted by CT code because these will not include old airings that have
    // been replaced with new ones on the same station and timeslot.
    DBObject[] rawAirs = wiz.getRawAccess(Wizard.AIRING_CODE, Wizard.AIRINGS_BY_CT_CODE);
    // This seems to split the airings in half, but not perfectly, so we allocate 65% of the total
    // to each array. Also unless we are incorrectly populating the database, we have no reason to
    // use a set for either of these. All airings will be unique objects and using a set would
    // create thousands of new objects for no gain and slower overall insertion performance due to
    // the objects created for every new entry.
    List<Airing> airset = new ArrayList<>(Math.max(10, (int)(rawAirs.length * 0.65f)));
    List<Airing> remAirSet = new ArrayList<>(Math.max(10, (int)(rawAirs.length * 0.65f)));

    long currLookahead = Sage.getLong("scheduling_lookahead", LOOKAHEAD);
    int testMask = DBObject.MEDIA_MASK_TV;
    long currTime = Sage.time();
    for (int i = 0, rawAirsLen = rawAirs.length; i < rawAirsLen; i++)
    {
      Airing a = (Airing) rawAirs[i];
      // Only use TV Airings in this calculation
      if (a == null || !a.hasMediaMaskAny(testMask)) continue;
      if (a.getStartTime() < currTime + currLookahead &&
          a.getStartTime() >= currTime - Scheduler.SCHEDULING_LOOKBEHIND && a.isTV())
      {
        airset.add(a);
      }
      else
      {
        remAirSet.add(a);
      }
    }
    // We need to sort for the media file insertion step so that we can do a binary search.
    Collections.sort(airset, DBObject.ID_COMPARATOR);
    // We need to sort this one for later so we don't need to sort 1000's of smaller arrays.
    Collections.sort(remAirSet, DBObject.ID_COMPARATOR);

    // We also need to be sure we analyze all of the files
    MediaFile[] mfs = wiz.getFiles();
    for (int i = 0, mfsLen = mfs.length; i < mfsLen; i++)
    {
      MediaFile mf = mfs[i];
      if (mf != null && !mf.isArchiveFile() && mf.isTV())
      {
        Airing contentAir = mf.getContentAiring();
        // Ensure we are inserting unique airings since we are not using a set.
        int index = Collections.binarySearch(airset, contentAir, DBObject.ID_COMPARATOR);
        if (index < 0)
        {
          index = -(index + 1);
          airset.add(index, contentAir);
        }
      }
    }

    // Add all of the work into a shared queue so we don't end up giving one thread a lot more work
    // over another.
    int submittedAgents;
    if (!doneInit && Sage.getBoolean(LIMITED_CARNY_INIT, true))
    {
      for (int i = 0, allAgentsLen = allAgents.length; i < allAgentsLen; i++)
      {
        Agent currAgent = (Agent) allAgents[i];
        // This is an inexpensive check against two flags so we aren't adding agents to the queue
        // that we already know shouldn't be there.
        if (currAgent != null && !currAgent.testAgentFlag(Agent.DISABLED_FLAG) && currAgent.isFavorite())
        {
          agentWorkQueue.add(currAgent);
        }
      }
      submittedAgents = agentWorkQueue.size();
    }
    else
    {
      for (int i = 0, allAgentsLen = allAgents.length; i < allAgentsLen; i++)
      {
        Agent currAgent = (Agent) allAgents[i];
        // This is an inexpensive check against a flag so we aren't adding agents to the queue that
        // we already know shouldn't be there.
        if (currAgent != null && !currAgent.testAgentFlag(Agent.DISABLED_FLAG))
        {
          agentWorkQueue.add(currAgent);
        }
      }
      submittedAgents = agentWorkQueue.size();
    }

    Map<Integer, Airing[]> allAirsMap;
    Map<Integer, Airing[]> remAirsMap;
    Map<Integer, Airing[]> watchAirsMap;
    Map<Integer, Wasted[]> wastedAirsMap;

    Airing[] allAirs;
    Airing[] remAirs;
    Airing[] watchAirs;
    Airing[] wastedAirs;

    if (disableCarnyMaps)
    {
      allAirsMap = null;
      remAirsMap = null;
      watchAirsMap = null;
      wastedAirsMap = null;

      allAirs = airset.toArray(Pooler.EMPTY_AIRING_ARRAY);
      remAirs = remAirSet.toArray(Pooler.EMPTY_AIRING_ARRAY);
      watchAirs = null;
      wastedAirs = null;
    }
    else
    {
      System.out.println("CARNY building airing maps...");

      DBObject[] watches = wiz.getRawAccess(Wizard.WATCH_CODE, (byte) 0);
      List<Airing> fullWatchAirsList = new ArrayList<>(watches.length);
      for (int i = 0, watchesLen = watches.length; i < watchesLen; i++)
      {
        Watched currWatch = (Watched) watches[i];
        if (currWatch != null && BigBrother.isFullWatch(currWatch))
        {
          fullWatchAirsList.add(currWatch.getAiring());
        }
      }

      DBObject[] waste = wiz.getRawAccess(Wizard.WASTED_CODE, (byte) 0);
      List<Airing> wastedAirsList = new ArrayList<>(waste.length);
      for (int i = 0, wasteLen = waste.length; i < wasteLen; i++)
      {
        Wasted currWaste = (Wasted) waste[i];
        if (currWaste != null)
        {
          wastedAirsList.add(currWaste.getAiring());
        }
      }

      // We need to sort these two now so we don't need to sort 1000's of smaller arrays.
      Collections.sort(fullWatchAirsList, DBObject.ID_COMPARATOR);
      Collections.sort(wastedAirsList, DBObject.ID_COMPARATOR);

      allAirs = airset.toArray(Pooler.EMPTY_AIRING_ARRAY);
      remAirs = remAirSet.toArray(Pooler.EMPTY_AIRING_ARRAY);
      watchAirs = fullWatchAirsList.toArray(Pooler.EMPTY_AIRING_ARRAY);
      wastedAirs = wastedAirsList.toArray(Pooler.EMPTY_AIRING_ARRAY);

      allAirsMap = buildMap(allAirs, mapBitShift);
      remAirsMap = buildMap(remAirs, mapBitShift);
      watchAirsMap = buildMap(watchAirs, mapBitShift);
      wastedAirsMap = buildWastedMap(wastedAirs, mapBitShift);
    }

    // Free these fairly huge arrays up for GC.
    airset = null;
    remAirSet = null;
    CarnyCache seedCache = new CarnyCache(allAirsMap, remAirsMap, watchAirsMap, wastedAirsMap);

    if (Sage.DBG) System.out.println("CARNY Processing " + submittedAgents + " Agents & " + allAirs.length + " Airs");

    boolean controlCPUUsage = doneInit && submittedAgents > 50 && Sage.getBoolean("control_profiler_cpu_usage", true);
    boolean useLegacyKeyword = Sage.getBoolean("use_legacy_keyword_favorites", true);
    boolean aggressiveNegativeProfiling = Sage.getBoolean("aggressive_negative_profiling", false);

    // If we don't have any agents, there's no point in spinning up worker threads. Also some users
    // might have servers with a lot of cores (> 8), so if we don't have a lot of agents, we will
    // not try to spread the load across every core because it's just a waste of resources. We are
    // arbitrarily selecting 75 agents per core. The number could be higher, but on lower powered
    // machines this will make a more meaningful difference on startup.
    int totalThreads = Sage.getInt(LIMITED_CARNY_THREADS, 0);
    if (totalThreads <= 0)
    {
      totalThreads = submittedAgents == 0 ? 0 :
        Math.min(PROCESSOR_COUNT, Math.max(1, submittedAgents / 75));
    }
    // This is an unlikely number that will not break things like some crazy number someone might
    // set or an bad value returned for the processor count. There is known contention between the
    // threads since they all want to modify several common objects, so at some point there will be
    // diminishing returns. This could be an argument for concurrent objects, but nothing is
    // iterating the collections/maps while they are being modified and in real world testing the
    // contention does not cause any measurable performance loss.
    if (totalThreads > 64)
      totalThreads = 64;
    Future<CarnyWorkerCallback> agentWorkerFutures[] = new Future[totalThreads];
    // Start/resume all of the agent worker threads.
    for (int i = 0; i < totalThreads; i++)
    {
      Callable<CarnyWorkerCallback> newJob = new CarnyAgentWorker(controlCPUUsage, totalThreads,
        doneInit, useLegacyKeyword, aggressiveNegativeProfiling, mapBitShift, allAirs, remAirs,
        watchAirs, wastedAirs, i != 0 ? new CarnyCache(seedCache) : seedCache);

      agentWorkerFutures[i] = agentWorkers.submit(newJob);
    }

    int traitorsExpand = 0;
    int newLoveAirSetExpand = 0;
    int blackBalledExpand = 0;
    int airSetExpand = 0;
    int watchedPotsToClearExpand = 0;
    int newMustSeeSetExpand = 0;
    int newWPCausesExpand = 0;

    boolean workCanceled = false;
    CarnyWorkerCallback firstCallback = null;
    int agentWorkerFuturesSize = agentWorkerFutures.length;
    int lastIndex = agentWorkerFuturesSize - 1;
    CarnyWorkerCallback callbacks[] = new CarnyWorkerCallback[agentWorkerFuturesSize];
    for (int i = 0;  i < agentWorkerFuturesSize; i++)
    {
      Future<CarnyWorkerCallback> future = agentWorkerFutures[i];
      try
      {
        CarnyWorkerCallback callback;
        boolean keepRunning;
        try
        {
          callback = doneInit ? future.get(5, TimeUnit.MINUTES) : future.get(250, TimeUnit.MILLISECONDS);
          keepRunning = callback.complete;

          if (keepRunning)
          {
            // Retain the callbacks for iteration later. We can optimize this further by not
            // actually merging everything here.
            callbacks[i] = callback;
            if (firstCallback == null)
            {
              firstCallback = callback;
            }

            traitorsExpand += callback.traitors.size();
            newLoveAirSetExpand += callback.newLoveAirSet.size();
            blackBalledExpand += callback.blackBalled.size();
            airSetExpand += callback.airSet.size();
            watchedPotsToClearExpand += callback.watchedPotsToClear.size();
            newMustSeeSetExpand += callback.newMustSeeSet.size();
            newWPCausesExpand += callback.newWPCauses.size();

            // On the last index, we expand the chosen callback arrays to accommodate the highest
            // number of agents/airings possible to be added.
            if (i == lastIndex)
            {
              List<Agent> destAgents = firstCallback.traitors;
              if (destAgents instanceof ArrayList)
                ((ArrayList) destAgents).ensureCapacity(traitorsExpand);

              List<Airing> destAirings = firstCallback.newLoveAirSet;
              if (destAirings instanceof ArrayList)
                ((ArrayList) destAirings).ensureCapacity(newLoveAirSetExpand);

              destAirings = firstCallback.blackBalled;
              if (destAirings instanceof ArrayList)
                ((ArrayList) destAirings).ensureCapacity(blackBalledExpand);

              destAirings = firstCallback.airSet;
              if (destAirings instanceof ArrayList)
                ((ArrayList) destAirings).ensureCapacity(airSetExpand);

              destAirings = firstCallback.watchedPotsToClear;
              if (destAirings instanceof ArrayList)
                ((ArrayList) destAirings).ensureCapacity(watchedPotsToClearExpand);

              destAirings = firstCallback.newMustSeeSet;
              if (destAirings instanceof ArrayList)
                ((ArrayList) destAirings).ensureCapacity(newMustSeeSetExpand);

              List<WPCauseValue> destCauses = firstCallback.newWPCauses;
              if (destCauses instanceof ArrayList)
                ((ArrayList) destCauses).ensureCapacity(newWPCausesExpand);

              for (int j = 0; j < agentWorkerFuturesSize; j++)
              {
                callback = callbacks[j];
                if (callback == firstCallback)
                  continue;

                // Since we allowed the workers to build their own idea of watch potential and
                // causes, we need to consolidate those maps here. This is still faster because
                // the threads are not fighting each other over the same map and it removes a
                // synchronization requirement.
                List<WPCauseValue> srcCauses = callback.newWPCauses;
                for (int k = 0, size = srcCauses.size(); k < size; k++)
                {
                  WPCauseValue currCauseValue = srcCauses.get(k);
                  WPCauseValue mappedCauseValue = firstCallback.addOrReturnWPCauseValue(currCauseValue);
                  Airing agentPot = currCauseValue.airing;

                  // We just added this agent.
                  if (mappedCauseValue == null)
                  {
                    if (agentPot.isWatchedForSchedulingPurpose())
                      firstCallback.addWatchedPotsToClear(agentPot);
                  }
                  // This must be run on all agents being merged or the sort order will be
                  // inconsistent between runs of Carny or completely incorrect.
                  else if (mappedCauseValue.compareAndReplace(currCauseValue.agent, true))
                  {
                    // When we passed true into the compare and replace method, it also replaced
                    // the agent if it has the same WP, but is logically not the agent we would have
                    // selected if this was all done on a single thread.
                    if (agentPot.isWatchedForSchedulingPurpose())
                      firstCallback.addWatchedPotsToClear(agentPot);
                  }
                }

                List<Agent> srcAgents = callback.traitors;
                for (int k = 0, size = srcAgents.size(); k < size; k++)
                  firstCallback.addTraitor(srcAgents.get(k));

                List<Airing> srcAirings = callback.newLoveAirSet;
                for (int k = 0, size = srcAirings.size(); k < size; k++)
                  firstCallback.addNewLoveAirSet(srcAirings.get(k));

                srcAirings = callback.blackBalled;
                for (int k = 0, size = srcAirings.size(); k < size; k++)
                  firstCallback.addBlackBalled(srcAirings.get(k));

                srcAirings = callback.airSet;
                for (int k = 0, size = srcAirings.size(); k < size; k++)
                  firstCallback.addAirSet(srcAirings.get(k));

                srcAirings = callback.watchedPotsToClear;
                for (int k = 0, size = srcAirings.size(); k < size; k++)
                  firstCallback.addWatchedPotsToClear(srcAirings.get(k));

                srcAirings = callback.newMustSeeSet;
                for (int k = 0, size = srcAirings.size(); k < size; k++)
                  firstCallback.addNewMustSeeSet(srcAirings.get(k));
              }
            }
          }
        }
        catch (TimeoutException e)
        {
          // Do not proceed to the next future since we are only breaking out of waiting for it to
          // complete to update the logged progress. Otherwise we do not expect to ever find
          // ourselves in this block.
          i--;
          keepRunning = true;
        }

        int jobsRemoved = submittedAgents - agentWorkQueue.size();

        if (!doneInit)
        {
          // Wait until we have actually finished the last worker thread before reporting 100%.
          if (jobsRemoved == submittedAgents && lastIndex != i)
            continue;
          // An empty database without any airings should go straight to 100%.
          String newSplashMsg = Sage.rez("Module_Init_Progress",
            new Object[]{Sage.rez("Profiler"), submittedAgents != 0 && allAirs.length != 0 ?
              new Double((jobsRemoved * 1.0) / submittedAgents) : new Double(1.0)});
          if (lastMessage == null || !newSplashMsg.equals(lastMessage))
          {
            Sage.setSplashText(newSplashMsg);
          }
          lastMessage = newSplashMsg;
        }
        else if (keepRunning && !workCanceled)
        {
          // Wait until we have actually finished the last worker thread before reporting 100%.
          if (jobsRemoved == submittedAgents && lastIndex != i)
            continue;
          // This isn't a completely accurate statement because removing an agent from the queue
          // doesn't mean we also finished processing the agent at the same time, but it is close
          // enough.
          if (Sage.DBG) System.out.println(
            "CARNY agent workers processed " + jobsRemoved + " of " + submittedAgents + " active agents.");
        }

        // If a future returns false, that means we have a new job and need to stop all processing.
        // That means we will likely have excess jobs left in the queue that need to be cleared out.
        // It is ok to do this regardless of if other agent workers are still running because they
        // all need to stop processing anyway.
        if (!keepRunning)
        {
          System.out.println("CARNY agent worker returned canceled.");
          agentWorkQueue.clear();
          workCanceled = true;
        }
      }
      catch (Exception e)
      {
        // If we create any exceptions, everything is now invalid, so we need to remove the rest of
        // the agents. We still need to wait for all of the threads to return or we will create a
        // race condition and in this case that would be very bad.
        agentWorkQueue.clear();
        workCanceled = true;
        if (!(e instanceof InterruptedException) && Sage.DBG)
        {
          System.out.println("CARNY created an exception while processing: " + e.getMessage());
          e.printStackTrace(System.out);
          Throwable innerException = e.getCause();
          if (innerException != null)
            innerException.printStackTrace(System.out);
        }
      }
    }


    if (workCanceled)
      return;

    // If we have no agents to process, we will also get a null firstCallback without also getting
    // workCanceled.
    if (firstCallback == null)
    {
      if (Sage.DBG) System.out.println("CARNY has no agents to process.");

      // This is here purely for aesthetic reasons. Otherwise all we see is 0%.
      if (!doneInit)
      {
        lastMessage = Sage.rez("Module_Init_Progress", new Object[] { Sage.rez("Profiler"), new Double(1)});
        Sage.setSplashText(lastMessage);
      }

      // We would skip this step otherwise. We could get here with an actual schedule by not having
      // any automatically created agents and removing our last favorite.
      if (doneInit)
        SchedulerSelector.getInstance().kick(false);
      // This must be set or Scheduler and Seeker will not function.
      prepped = true;
      return;
    }

    final List<Agent> traitors = firstCallback.traitors;
    final Set<Airing> newLoveAirSet = new HashSet<>(firstCallback.newLoveAirSet);
    final List<Airing> blackBalled = firstCallback.blackBalled;
    final List<Airing> airSet = firstCallback.airSet;
    final List<Airing> watchedPotsToClear = firstCallback.watchedPotsToClear;
    final Set<Airing> newMustSeeSet = new HashSet<>(firstCallback.newMustSeeSet);
    final List<WPCauseValue> newWPCauses = firstCallback.newWPCauses;

    int newWPCausesSize = newWPCauses.size();
    final Map<Airing, Float> newWPMap = new HashMap<>(Math.max(16, (int)(newWPCausesSize / 0.75f) + 1));
    final Map<Airing, Agent> newCauseMap = new HashMap<>(Math.max(16, (int)(newWPCausesSize / 0.75f) + 1));

    for (int i = 0; i < newWPCausesSize; i++)
    {
      WPCauseValue value = newWPCauses.get(i);
      // Modifications to values are all synchronized on their own entry as they are changed by
      // multiple threads, so we need to do that here too.
      newWPMap.put(value.airing, value.wp);
      newCauseMap.put(value.airing, value.agent);
    }

    synchronized (this)
    {
      for (Airing oldAir : swapMap.keySet())
      {
        if (newLoveAirSet.contains(oldAir))
        {
          newLoveAirSet.remove(oldAir);
          newLoveAirSet.add(swapMap.get(oldAir));
        }
      }
      loveAirSet = newLoveAirSet;
    }

    // Remove any blackballs that are favorites now; unless they're marked don't like
    blackBalled.removeAll(newMustSeeSet);

    // Clear out all of the negative energy, this includes marked waste
    Wasted[] stoners = wiz.getWasted();

    newWPMap.keySet().removeAll(watchedPotsToClear);

    if (Sage.DBG) System.out.println("CARNY Negative Energy Size: " + (blackBalled.size() + stoners.length));
    for (int i = 0, blackBalledSize = blackBalled.size(); i < blackBalledSize; i++)
    {
      Airing badAir = blackBalled.get(i);
      newWPMap.remove(badAir);
      newCauseMap.remove(badAir);
      // Replaced with binary removals.
      //newMustSeeSet.remove(badAir);
      firstCallback.removeNewMustSeeSet(badAir);
      //airSet.remove(badAir);
      firstCallback.removeAirSet(badAir);
    }

    for (int i = 0, stonersLen = stoners.length; i < stonersLen; i++)
    {
      Airing badAir = stoners[i].getAiring();
      newWPMap.remove(badAir);
      newCauseMap.remove(badAir);
      // Replaced with binary removals.
      //newMustSeeSet.remove(badAir);
      firstCallback.removeNewMustSeeSet(badAir);
      //airSet.remove(badAir);
      firstCallback.removeAirSet(badAir);
    }

    if (Sage.DBG) System.out.println("CARNY Traitors:" + traitors);
    for (int i = 0, traitorsSize = traitors.size(); i < traitorsSize; i++)
    {
      wiz.removeAgent(traitors.get(i));
    }

    synchronized (this)
    {
      Agent thisAgent;
      Float thisFloat;
      for (Airing oldAir : swapMap.keySet())
      {
        Airing newAir = swapMap.get(oldAir);
        if ((thisFloat = newWPMap.remove(oldAir)) != null)
        {
          newWPMap.put(newAir, thisFloat);
        }
        if (airSet.remove(oldAir))
        {
          airSet.add(newAir);
        }
        if (newMustSeeSet.remove(oldAir))
        {
          newMustSeeSet.add(newAir);
        }
        if ((thisAgent = newCauseMap.remove(oldAir)) != null)
        {
          newCauseMap.put(newAir, thisAgent);
        }
      }
      wpMap = newWPMap;
      causeMap = newCauseMap;
      pots = airSet.toArray(Pooler.EMPTY_AIRING_ARRAY);
      mustSeeSet = newMustSeeSet;
    }
    // This is when we need to propagate the change to all of our
    // clients
    clientSyncAll();

    prepped = true;
    lastCycleCompleteTime = Sage.eventTime();

    long timeSpan = lastCycleCompleteTime - cycleStartTime;
    if (timeSpan < Sage.MILLIS_PER_MIN * 2)
      System.out.println("CARNY finished in " + timeSpan + "ms");
    else if (timeSpan < Sage.MILLIS_PER_HR * 2)
      System.out.println("CARNY finished in " + (timeSpan / 60f / 1000f) + "m");
    else
      System.out.println("CARNY finished in " + (timeSpan / 60f / 60f / 1000f) + "h");

    if (doneInit)
      SchedulerSelector.getInstance().kick(false);

    /*dumpResults();
    try
    {
      System.out.println("Press any key to exit...");
      System.in.read();
    } catch (IOException e)
    {
      e.printStackTrace();
    }
    System.exit(0);*/
  }

  public int getWatchCount() { return globalWatchCount; }

  public synchronized float getWP(Airing air)
  {
    Float f = wpMap.get(air);
    if (f == null)
      return MIN_WP;
    else
      return Math.max(f, MIN_WP);
  }

  public boolean areSameFavorite(Airing a1, Airing a2)
  {
    return areSameFavorite(a1, a2, null);
  }

  // Returns false iff one is a Favorite and the other isn't, or both are favorites with different causes
  public boolean areSameFavorite(Airing a1, Airing a2, StringBuilder sbCache)
  {
    Agent b1;
    Agent b2;
    // After we get the values from the map, this isn't any different than how the we background
    // process data. We accept that things might change a little while we are using them. Otherwise
    // we end up snagging agent workers because the followsTrend() can run a little long. The Carny
    // workers are also butting heads with each other and Scheduler on the Carny monitor when it is
    // doing the potentials sort which could make this an opportunity for a read/write lock, but we
    // would need to convert the entire class, so we might do that at a later time. For reference,
    // the contention is around 10-200ms per blocked thread which is measurable impact-wise.
    synchronized (this)
    {
      b1 = causeMap.get(a1);
      b2 = causeMap.get(a2);
    }
    if (b1 == b2) return true;
    if (b1 != null && b1.isFavorite())
    {
      // The favorite applies for both. One of them may not be in the cause map because
      // the cause map is limited by the scheduling lookahead. But then we looking further out
      // in the schedule to conflict resolution we can end up calling this test so we need to check this way too.
      return b1.followsTrend(a2, false, sbCache);
    }
    if (b2 != null && b2.isFavorite())
      return b2.followsTrend(a1, false, sbCache);

    // Neither agent is a favorite; so they are both from the null favorite which is true
    return true;
  }

  public synchronized String getCause(Airing air)
  {
    Agent bond = causeMap.get(air);
    if (bond == null) return null;
    else return bond.getCause();
  }

  public synchronized Agent getCauseAgent(Airing air)
  {
    return causeMap.get(air);
  }

  synchronized Airing doBattle(Airing air1, Airing air2)
  {
    Agent agent1 = getCauseAgent(air1);
    Agent agent2 = getCauseAgent(air2);
    if (agent1 == null && agent2 == null) return null;
    if (agent2 == null && agent1 != null)
      return air1;
    if (agent1 == null && agent2 != null)
      return air2;
    Agent secretWinner = familyFeud(agent1, agent2, new HashSet<Agent>());
    if (secretWinner != null)
      return (secretWinner == agent1) ? air1 : air2;
    return null;
  }

  synchronized Agent doBattle(Agent a1, Agent a2)
  {
    return familyFeud(a1, a2, new HashSet<Agent>());
  }

  private Agent familyFeud(Agent agent1, Agent agent2, Set<Agent> fightHistory)
  {
    if (!fightHistory.add(agent1)) return null;
    for (int i = 0; i < agent1.weakAgents.length; i++)
    {
      if (agent1.weakAgents[i] == agent2.id)
        return agent1;
      Agent babyAgent = wiz.getAgentForID(agent1.weakAgents[i]);
      if (babyAgent != null)
      {
        Agent babyFight = familyFeud(babyAgent, agent2, fightHistory);
        if (babyFight == babyAgent)
          return agent1;
      }
    }
    if (!fightHistory.add(agent2)) return null;
    for (int i = 0; i < agent2.weakAgents.length; i++)
    {
      if (agent2.weakAgents[i] == agent1.id)
        return agent2;
      Agent babyAgent = wiz.getAgentForID(agent2.weakAgents[i]);
      if (babyAgent != null)
      {
        Agent babyFight = familyFeud(babyAgent, agent1, fightHistory);
        if (babyFight == babyAgent)
          return agent2;
      }
    }
    return null;
  }

  private static final Comparator<Agent> agentCreateTimeSorter = new Comparator<Agent>()
  {
    public int compare(Agent a1, Agent a2)
    {
      long createDiff = (a1 == null ? Long.MAX_VALUE : a1.createTime) -
          (a2 == null ? Long.MAX_VALUE : a2.createTime);
      if (createDiff < 0)
        return -1;
      else if (createDiff > 0)
        return 1;
      else
        return 0;
    }
  };

  // NOTE: This may be something that's needed to correct errors we've been letting build up in the Favorites
  // priority system.
  public void fixAgentPriorities()
  {
    Agent[] allFavs = wiz.getFavorites();
    Agent[] orgSort = sortAgentsByPriority(allFavs);
    System.out.println("Original Favorite Priorities:");
    for (int i = 0; i < orgSort.length; i++)
      System.out.println(i + "-" + orgSort[i].getCause());

    System.out.println("Redoing favorite priorities");
    // Redo every priority we have set already which will now clear out any inconsistencies in the list...and likely change
    // its order in the process...
    for (int i = 0; i < allFavs.length; i++)
    {
      Agent testMe = allFavs[i];
      for (int j = 0; j < testMe.weakAgents.length; j++)
      {
        Agent weaker = wiz.getAgentForID(testMe.weakAgents[j]);
        if (weaker == null) // remove invalid weaker IDs
        {
          testMe.unbully(testMe.weakAgents[j]);
          j--;
        }
        else
          testMe.bully(weaker);
      }
    }
    Agent[] newSort = sortAgentsByPriority(allFavs);
    System.out.println("New Favorite Priorities:");
    for (int i = 0; i < newSort.length; i++)
      System.out.println(i + "-" + newSort[i].getCause());
  }

  public Agent[] sortAgentsByPriority(Object[] sortingAgents)
  {
    if (sortingAgents == null) return null;
    // Only do the Favorites, the other ones don't matter
    Agent[] sortedAgents = wiz.getFavorites();
    Arrays.sort(sortedAgents, agentCreateTimeSorter);
    //if (Sage.DBG) System.out.println("SORTING AGENTS=" + Arrays.asList(sortedAgents));
    List<Agent> finalAgentSort = new ArrayList<Agent>();
    for (int i = sortedAgents.length - 1; i >= 0; i--)
    {
      Agent insertMe = sortedAgents[i];
      if (insertMe == null) continue;
      // We put it directly after the lowest Agent that this Agent is specifically weaker
      // than.  If this Agent isn't weaker than any Agents already in the Vec, we put it at the top.
      int insertedAt = -1;
      for (int j = finalAgentSort.size() - 1; j >= 0; j--)
      {
        Agent battleMe = finalAgentSort.get(j);
        for (int k = 0; k < battleMe.weakAgents.length; k++)
        {
          if (battleMe.weakAgents[k] == insertMe.id)
          {
            insertedAt = j+1;
            //System.out.println("Inserted " + insertMe.getCause() + " behind " + battleMe.getCause());
            finalAgentSort.add(j + 1, insertMe);
            break;
          }
        }
        if (insertedAt != -1)
          break;
      }
      if (insertedAt == -1)
      {
        //System.out.println("Inserted " + insertMe.getCause() + " at front");
        finalAgentSort.add(0, insertMe);
      }
      else
      {
        // Check all of the Agents above it in the list to see if any are weaker than
        // us. If they are, then just move them below us....we need to do this again
        // for anything that we move down in the list....BUT be careful about infinite recursion
        List<Object[]> movesToCheck = new ArrayList<Object[]>();
        movesToCheck.add(new Object[] { insertMe, new Integer(insertedAt) });
        while (!movesToCheck.isEmpty())
        {
          Object[] currMove = movesToCheck.remove(0);
          insertedAt = ((Integer) currMove[1]).intValue();
          insertMe = (Agent) currMove[0];
          for (int j = insertedAt - 1; j >= 0; j--)
          {
            Agent battleMe = finalAgentSort.get(j);
            for (int k = 0; k < insertMe.weakAgents.length; k++)
            {
              if (insertMe.weakAgents[k] == battleMe.id)
              {
                finalAgentSort.add(insertedAt, finalAgentSort.remove(j));
                movesToCheck.add(new Object[] { battleMe, new Integer(insertedAt) });
                //System.out.println("MOVED " + battleMe.getCause() + " behind " + insertMe.getCause());
                insertedAt--;
                break;
              }
            }
          }
        }
      }
      //			if (Sage.DBG) System.out.println("BUILDING SORTED AGENTS=" + finalAgentSort);
    }
    if (finalAgentSort.size() == sortingAgents.length)
      return finalAgentSort.toArray(new Agent[0]);

    Set<Object> sortingSet = new HashSet<Object>(Arrays.asList(sortingAgents));
    List<Agent> rv = new ArrayList<Agent>();
    for (int i = 0; i < finalAgentSort.size(); i++)
    {
      Agent bud = finalAgentSort.get(i);
      if (sortingSet.contains(bud))
        rv.add(bud);
    }
    return rv.toArray(new Agent[0]);
  }

  public void setRecordingQuality(Agent bond, String newQual)
  {
    bond.setRecordingQuality(newQual);
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.FAVORITE_MODIFIED,
        new Object[] { sage.plugin.PluginEventManager.VAR_FAVORITE, bond });
  }

  public void setAutoConvertFormat(Agent bond, String newQual)
  {
    bond.setAutoConvertFormat(newQual);
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.FAVORITE_MODIFIED,
        new Object[] { sage.plugin.PluginEventManager.VAR_FAVORITE, bond });
  }

  public void createPriority(Agent top, Agent bottom)
  {
    //if (Sage.DBG) System.out.println("IN Carny createPriority(top=" + top + " bottom=" + bottom + ")");
    top.bully(bottom);
    //if (Sage.DBG) System.out.println("OUT Carny createPriority(top=" + top + " bottom=" + bottom + ")");
    SchedulerSelector.getInstance().kick(false);
  }

  public void setStartPadding(Agent bond, long padAmount)
  {
    bond.setStartPadding(padAmount);
    SchedulerSelector.getInstance().kick(false);
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.FAVORITE_MODIFIED,
        new Object[] { sage.plugin.PluginEventManager.VAR_FAVORITE, bond });
  }

  public void setStopPadding(Agent bond, long padAmount)
  {
    bond.setStopPadding(padAmount);
    SchedulerSelector.getInstance().kick(false);
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.FAVORITE_MODIFIED,
        new Object[] { sage.plugin.PluginEventManager.VAR_FAVORITE, bond });
  }

  public void setAgentFlags(Agent bond, int flagMask, int flagValue)
  {
    bond.setAgentFlags(flagMask, flagValue);
    SchedulerSelector.getInstance().kick(false);
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.FAVORITE_MODIFIED,
        new Object[] { sage.plugin.PluginEventManager.VAR_FAVORITE, bond });
  }

  public boolean isDoNotDestroy(MediaFile mf)
  {
    Agent cause = getCauseAgent(mf.getContentAiring());
    if (cause != null)
    {
      if (cause.testAgentFlag(Agent.DONT_AUTODELETE_FLAG))
        return true;
    }
    return false;
  }

  public boolean isDeleteAfterConversion(MediaFile mf)
  {
    Agent cause = getCauseAgent(mf.getContentAiring());
    if (cause != null)
    {
      if (cause.testAgentFlag(Agent.DELETE_AFTER_CONVERT_FLAG))
        return true;
    }
    return false;
  }

  public String getAutoConvertFormat(MediaFile mf)
  {
    Agent cause = getCauseAgent(mf.getContentAiring());
    if (cause != null)
    {
      return cause.getAutoConvertFormat();
    }
    return null;
  }

  public File getAutoConvertDest(MediaFile mf)
  {
    Agent cause = getCauseAgent(mf.getContentAiring());
    if (cause != null)
    {
      return cause.getAutoConvertDest();
    }
    return null;
  }

  synchronized Airing[] getPots()
  {
    return pots;
  }

  synchronized boolean isMustSee(Airing air)
  {
    return mustSeeSet.contains(air);
  }

  boolean isBaseTrend(int trendTest)
  {
    for (int i = 0; i < trends.length; i++)
      if (trends[i] == trendTest) return true;
    return false;
  }

  public boolean isLoveAir(Airing air)
  {
    return loveAirSet.contains(air);
  }

  boolean isPrepped() { return prepped; }

  void addCarnyListener(ProfilingListener x) { listeners.add(x); }
  void removeCarnyListener(ProfilingListener x) { listeners.remove(x); }

  public synchronized void updateLoves(Set<Airing> s)
  {
    loveAirSet = s;
  }
  public synchronized void updateMustSees(Set<Airing> s)
  {
    mustSeeSet = s;
  }
  public synchronized void updateCauseMap(Map<Airing, Agent> m)
  {
    causeMap = m;
  }
  public synchronized void updateWPMap(Map<Airing, Float> m)
  {
    wpMap = m;
  }
  // When airings get swapped in the DB we update them in here to still be Favorites in order
  // to be careful and ensure we don't lose any favorites due to an EPG update
  public synchronized void notifyAiringSwap(Airing oldAir, Airing newAir)
  {
    if (mustSeeSet.contains(oldAir))
    {
      // To protect against a ConcurrentModificationException in SageTVConnection
      synchronized (mustSeeSet)
      {
        mustSeeSet.remove(oldAir);
        mustSeeSet.add(newAir);
      }
      // Also check through the potentials so that gets switched too
      for (int i = 0; i < pots.length; i++)
      {
        if (pots[i] == oldAir)
          pots[i] = newAir;
      }
    }
    if (loveAirSet.contains(oldAir))
    {
      // To protect against a ConcurrentModificationException in SageTVConnection
      synchronized (loveAirSet)
      {
        loveAirSet.remove(oldAir);
        loveAirSet.add(newAir);
      }
    }
    if (wpMap.containsKey(oldAir))
    {
      // To protect against a ConcurrentModificationException in SageTVConnection
      synchronized (wpMap)
      {
        wpMap.put(newAir, wpMap.remove(oldAir));
      }
    }
    if (causeMap.containsKey(oldAir))
    {
      // To protect against a ConcurrentModificationException in SageTVConnection
      synchronized (causeMap)
      {
        causeMap.put(newAir, causeMap.remove(oldAir));
      }
    }
    swapMap.put(oldAir, newAir);
  }

  private String prefs;
  private int globalWatchCount;
  private Vector<Object[]> jobs;
  private boolean alive;
  private boolean prepped;
  private Wizard wiz;
  private int[] trends;
  private Set<Airing> loveAirSet;
  private boolean doneInit;

  private long lastCycleCompleteTime;
  private long cycleStartTime;

  // Airing -> Float
  private Map<Airing, Float> wpMap;
  // Airing -> Agent
  private Map<Airing, Agent> causeMap;
  // Airings
  private Set<Airing> mustSeeSet;
  private Airing[] pots;

  private Vector<ProfilingListener> listeners = new Vector<ProfilingListener>();

  private Map<Airing, Airing> swapMap;

  // This is the queue that all of the worker threads get their tasks from. If we ever need to stop
  // everything, all we need to do is clear this queue and all of the threads will stop working
  // fairly quickly.
  private ConcurrentLinkedQueue<Agent> agentWorkQueue;
  private ExecutorService agentWorkers;

  private static final int strComp(DBObject s1, DBObject s2)
  {
    if (s1 == s2) return 0;
    else if (s1 == null) return 1;
    else if (s2 == null) return -1;
    else return (s1.id - s2.id);
  }

  public static final Comparator<DBObject> AGENT_SORTER = new Comparator<DBObject>()
  {
    public int compare(DBObject o1, DBObject o2)
    {
      if (o1 == o2)
        return 0;
      if (o1 == null)
        return 1;
      if (o2 == null)
        return -1;
      Agent a1 = (Agent) o1;
      Agent a2 = (Agent) o2;
      int i = a1.agentMask - a2.agentMask;
      if (i != 0)
        return i;
      if (a1.title != a2.title)
        return strComp(a1.title, a2.title);
      if (a1.category != a2.category)
        return strComp(a1.category, a2.category);
      if (a1.subCategory != a2.subCategory)
        return strComp(a1.subCategory, a2.subCategory);
      if (a1.person != a2.person)
        return strComp(a1.person, a2.person);
      if (a1.role != a2.role)
        return a1.role - a2.role;
      if (a1.rated != a2.rated)
        return strComp(a1.rated, a2.rated);
      if (a1.pr != a2.pr)
        return strComp(a1.pr, a2.pr);
      if (a1.network != a2.network)
        return strComp(a1.network, a2.network);
      if (a1.year != a2.year)
        return strComp(a1.year, a2.year);
      if (a1.slotType != a2.slotType)
        return a1.slotType - a2.slotType;
      if (a1.timeslots != a2.timeslots)
      {
        if (a1.timeslots == null)
          return -1;
        else if (a2.timeslots == null)
          return 1;
        else if (a1.timeslots.length != a2.timeslots.length)
          return a1.timeslots.length - a2.timeslots.length;
        for (int j = 0; j < a1.timeslots.length; j++)
          if (a1.timeslots[j] != a2.timeslots[j])
            return a1.timeslots[j] - a2.timeslots[j];
      }
      i = a1.chanName.compareTo(a2.chanName);
      if (i != 0)
        return i;
      return a1.keyword.compareTo(a2.keyword);
    }
  };

/*	public void launchAgentBrowser()
	{
		final java.awt.Frame f = new java.awt.Frame("AgentBrowser");
		f.setLayout(new java.awt.GridBagLayout());
		java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
		final java.awt.Choice c = new java.awt.Choice();
		DBObject[] allAgents = wiz.getRawAccess(Wizard.AGENT_CODE, Wizard.AGENTS_BY_CARNY_CODE);
		java.util.Set trendSet = new java.util.HashSet();
		for (int j = 0; j < allAgents.length; j++)
		{
			Agent bond = (Agent) allAgents[j];
			if (bond != null && trendSet.add(bond.getNameForType()))
				c.add(bond.getNameForType());
		}
		c.add("WATCHED");
		c.add("WASTED");
		final java.awt.List lis = new java.awt.List();
		final java.awt.TextArea ta = new java.awt.TextArea();
		final java.util.Vector agentVec = new java.util.Vector();
		gbc.gridx = gbc.gridy = 0;
		gbc.weightx = 1;
		gbc.weighty = 0;
		gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
		f.add(c, gbc);
		gbc.gridy++;
		gbc.weighty = 1;
		gbc.fill = java.awt.GridBagConstraints.BOTH;
		f.add(lis, gbc);
		gbc.gridy++;
		f.add(ta, gbc);
		c.addItemListener(new java.awt.event.ItemListener()
		{
			public void itemStateChanged(java.awt.event.ItemEvent evt)
			{
				String str = c.getSelectedItem();
				if (str == null) return;
				lis.removeAll();
				agentVec.clear();
				if ("WATCHED".equals(str))
				{
					DBObject[] all = wiz.getRawAccess(Wizard.WATCH_CODE, (byte) 0);
					for (int j = 0; j < all.length; j++)
					{
						if (all[j] != null)
							lis.add(all[j].toString());
					}
				}
				else if ("WASTED".equals(str))
				{
					DBObject[] all = wiz.getRawAccess(Wizard.WASTED_CODE, (byte) 0);
					for (int j = 0; j < all.length; j++)
					{
						if (all[j] != null)
							lis.add(all[j].toString());
					}
				}
				else
				{
					DBObject[] allAgents = wiz.getRawAccess(Wizard.AGENT_CODE, Wizard.AGENTS_BY_CARNY_CODE);
					for (int j = 0; j < allAgents.length; j++)
					{
						if (allAgents[j] != null && ((Agent)allAgents[j]).getNameForType().equals(str))
						{
							agentVec.addElement(allAgents[j]);
							lis.add(allAgents[j].toString());
						}
					}
				}
			}
		});
		lis.addItemListener(new java.awt.event.ItemListener()
		{
			public void itemStateChanged(java.awt.event.ItemEvent evt)
			{
				int sel = lis.getSelectedIndex();
				if (sel < 0 || sel >= agentVec.size()) return;
				Agent bond = (Agent) agentVec.elementAt(sel);
				StringBuffer sb = new StringBuffer();
				sb.append(bond.toString());
				sb.append("\r\n");
				sb.append("Negator=");
				sb.append(bond.isNegativeNelly() + "\r\n");
				sb.append("WeakAgents=\r\n");
				for (int i = 0; i < bond.weakAgents.length; i++)
				{
					Agent tempAgent = wiz.getAgentForID(bond.weakAgents[i]);
					if (tempAgent != null)
					{
						sb.append(tempAgent.toString());
						sb.append("\r\n");
					}
				}
				Airing[] relAirs = bond.getRelatedAirings(null);
				sb.append(relAirs.length);
				sb.append(" Related Airings\r\n");
				for (int i = 0; i < relAirs.length; i++)
				{
					sb.append(relAirs[i]);
					sb.append("\r\n");
				}
				ta.setText(sb.toString());
			}
		});
		f.addWindowListener(new java.awt.event.WindowAdapter()
		{
			public void windowClosing(java.awt.event.WindowEvent evt)
			{
				f.dispose();
			}
		});
		f.setSize(500, 500);
		f.setVisible(true);
	}*/

  /*private void dumpResults()
  {
    System.out.println("Dumping CARNY results...");

    List<Airing> localPots;
    List<Airing> localLoves;
    List<Airing> localMustSee;
    List<Map.Entry<Airing, Agent>> localCauseMap;
    List<Map.Entry<Airing, Float>> localWpMap;
    synchronized (this)
    {
      localPots = new ArrayList<>(Arrays.asList(pots));
      localLoves = new ArrayList<>(loveAirSet);
      localMustSee = new ArrayList<>(mustSeeSet);
      localCauseMap = new ArrayList<>(causeMap.entrySet());
      localWpMap = new ArrayList<>(wpMap.entrySet());
    }

    FileOutputStream potsFile = null;
    FileOutputStream lovesFile = null;
    FileOutputStream mustSeeFile = null;
    FileOutputStream causeFile = null;
    FileOutputStream wpFile = null;

    try
    {
      potsFile = new FileOutputStream("carny_pots.txt", false);
      lovesFile = new FileOutputStream("carny_loves.txt", false);
      mustSeeFile = new FileOutputStream("carny_must_see.txt", false);
      causeFile = new FileOutputStream("carny_cause.txt", false);
      wpFile = new FileOutputStream("carny_wp.txt", false);

      Collections.sort(localPots, DBObject.ID_COMPARATOR);
      Collections.sort(localLoves, DBObject.ID_COMPARATOR);
      Collections.sort(localMustSee, DBObject.ID_COMPARATOR);
      Collections.sort(localCauseMap, new Comparator<Map.Entry<Airing, Agent>>()
      {
        @Override
        public int compare(Map.Entry<Airing, Agent> o1, Map.Entry<Airing, Agent> o2)
        {
          return DBObject.ID_COMPARATOR.compare(o1.getKey(), o1.getKey());
        }
      });
      Collections.sort(localWpMap, new Comparator<Map.Entry<Airing, Float>>()
      {
        @Override
        public int compare(Map.Entry<Airing, Float> o1, Map.Entry<Airing, Float> o2)
        {
          return DBObject.ID_COMPARATOR.compare(o1.getKey(), o1.getKey());
        }
      });

      for (int i = 0; i < 3; i++)
      {
        List<Airing> currList;
        PrintWriter currWriter;
        switch (i)
        {
          case 0:
            currList = localPots;
            currWriter = new PrintWriter(potsFile);
            break;
          case 1:
            currList = localLoves;
            currWriter = new PrintWriter(lovesFile);
            break;
          default:
            currList = localMustSee;
            currWriter = new PrintWriter(mustSeeFile);
            break;
        }

        currWriter.write("Airing");
        currWriter.write('\t');
        currWriter.write("Show");
        currWriter.write('\n');

        for (Airing airing : currList)
        {
          Show show = airing.getShow();
          if (show != null)
          {
            currWriter.write(airing.toString());
            currWriter.write('\t');
            currWriter.write(show.toString());
            currWriter.write('\n');
          }
          else
          {
            currWriter.write(airing.toString());
            currWriter.write('\t');
            currWriter.write("S[No Show Available]");
            currWriter.write('\n');
          }
        }

        currWriter.flush();
      }

      PrintWriter currWriter = new PrintWriter(causeFile);
      currWriter.write("Agent");
      currWriter.write('\t');
      currWriter.write("Airing");
      currWriter.write('\t');
      currWriter.write("Show");
      currWriter.write('\n');

      for (Map.Entry<Airing, Agent> entry : localCauseMap)
      {
        Agent agent = entry.getValue();
        Airing airing = entry.getKey();
        Show show = airing.getShow();
        if (show != null)
        {
          currWriter.write(agent.toString());
          currWriter.write('\t');
          currWriter.write(airing.toString());
          currWriter.write('\t');
          currWriter.write(show.toString());
          currWriter.write('\n');
        }
        else
        {
          currWriter.write(agent.toString());
          currWriter.write('\t');
          currWriter.write(airing.toString());
          currWriter.write('\t');
          currWriter.write("S[No Show Available]");
          currWriter.write('\n');
        }
      }

      currWriter.flush();
      currWriter = new PrintWriter(wpFile);
      currWriter.write("WP(Float)");
      currWriter.write('\t');
      currWriter.write("Airing");
      currWriter.write('\t');
      currWriter.write("Show");
      currWriter.write('\n');

      for (Map.Entry<Airing, Float> entry : localWpMap)
      {
        Float floater = entry.getValue();
        Airing airing = entry.getKey();
        Show show = airing.getShow();
        if (show != null)
        {
          currWriter.write(floater.toString());
          currWriter.write('\t');
          currWriter.write(airing.toString());
          currWriter.write('\t');
          currWriter.write(show.toString());
          currWriter.write('\n');
        }
        else
        {
          currWriter.write(floater.toString());
          currWriter.write('\t');
          currWriter.write(airing.toString());
          currWriter.write('\t');
          currWriter.write("S[No Show Available]");
          currWriter.write('\n');
        }
      }

      currWriter.flush();
    }
    catch (Exception e)
    {
      e.printStackTrace(System.out);
    }
    finally
    {
      try
      {
        if (potsFile != null)
          potsFile.close();
      } catch (Exception e) {}

      try
      {
        if (lovesFile != null)
          lovesFile.close();
      } catch (Exception e) {}

      try
      {
        if (mustSeeFile != null)
          mustSeeFile.close();
      } catch (Exception e) {}

      try
      {
        if (causeFile != null)
          causeFile.close();
      } catch (Exception e) {}

      try
      {
        if (wpFile != null)
          wpFile.close();
      } catch (Exception e) {}
    }

    System.out.println("Dumped CARNY results.");
  }*/

  public static interface ProfilingListener
  {
    public boolean updateLoves(Set<Airing> s);
    public boolean updateMustSees(Set<Airing> s);
    public boolean updateCauseMap(Map<Airing, Agent> m);
    public boolean updateWPMap(Map<Airing, Float> m);
  }

  public class CarnyAgentWorker implements Callable<CarnyWorkerCallback>
  {
    // These are used for reference, but the data will never change, so it is ok for all of the
    // threads to share these relatively constant variables.
    private final boolean controlCPUUsage;
    private final int totalThreads;
    private final boolean doneInit;
    private final boolean legacyKeyword;
    private final boolean aggressiveNegativeProfiling;
    private final int mapBitShift;
    private final CarnyCache cache;
    private final String paidProgRez;
    private final Airing allAirs[];
    private final Airing remAirs[];
    private final Airing watchAirs[];
    private final Airing wastedAirs[];

    public CarnyAgentWorker(boolean controlCPUUsage, int totalThreads, boolean doneInit,
                            boolean legacyKeyword, boolean aggressiveNegativeProfiling, int mapBitShift,
                            Airing[] allAirs, Airing[] remAirs, Airing[] watchAirs, Airing[] wastedAirs,
                            CarnyCache cache)
    {
      this.controlCPUUsage = controlCPUUsage;
      this.totalThreads = totalThreads;
      this.doneInit = doneInit;
      this.legacyKeyword = legacyKeyword;
      this.aggressiveNegativeProfiling = aggressiveNegativeProfiling;
      this.mapBitShift = mapBitShift;
      this.cache = cache;
      this.paidProgRez = cache.paidProgRez;
      this.allAirs = allAirs;
      this.remAirs = remAirs;
      this.watchAirs = watchAirs;
      this.wastedAirs = wastedAirs;
    }

    // Returns false if the whole queue needs to be stopped for a new job. The above shared
    // variables must be local to the method controlling the execution of these callables, so we
    // will see the stopping process early message at least once for each processing thread, but
    // there will be no concerns about old jobs breaking new jobs.
    @Override
    public CarnyWorkerCallback call() throws Exception
    {
      CarnyWorkerCallback callback = new CarnyWorkerCallback();

      // Logging is synchronized which in this case causes startup contention that does not need to
      // exist.
      //if (Sage.DBG) System.out.println("Carny agent worker is starting a new job...");

      // Create a few local variable for slightly faster access times.
      final CarnyCache cache = this.cache;
      final StringBuilder sbCache = cache.sbCache;
      // 1000 was selected because we usually grow beyond 500 on a typical pass. We sometimes get
      // as big as 15000 depending on what agent this worker ends up getting, but tests have shown
      // that allocating larger values can actually slow us down a bit partly because not all
      // workers end up dealing with large amounts of airings.
      cache.airWorkCache = new CacheList(1000);
      final Map<Integer, Airing[]> allAirsMap = cache.allAirsMap;
      final Map<Integer, Airing[]> remAirsMap = cache.remAirsMap;

      Agent currAgent;
      while (true)
      {
        currAgent = agentWorkQueue.poll();
        // The queue is empty.
        if (currAgent == null)
        {
          //if (Sage.DBG) System.out.println("Carny agent worker is out of work.");
          break;
        }

        if (controlCPUUsage)
          try {Thread.sleep(SLEEP_PERIOD * totalThreads);} catch (Exception e) {}
        if (doneInit)
        {
          // Check to see if something else is a higher priority to calculate.
          // NOTE: If we've been trying to build the profile for a half hour and haven't finished then it's
          // likely that stopping early could cause us to never finish since recordings end/start so often.
          // So in that case we do NOT stop processing early and just continue on our merry way!
          if (!shouldContinueStdProcessing(Sage.eventTime() - cycleStartTime < 30 * Sage.MILLIS_PER_MIN))
          {
            if (Sage.DBG)
              System.out.println("Carny agent worker is quitting early to start a new job...");
            // The callback is not complete by default.
            return callback; // just bail, it'll pick it all up afterwards
          }
        }

        // This clears the array and then adds all of the hashes for this agent.
        currAgent.getHashes(cache.currentHashes, mapBitShift);
        if (!currAgent.calcWatchProb(controlCPUUsage, watchAirs, wastedAirs, aggressiveNegativeProfiling, cache))
        {
          callback.addTraitor(currAgent);
          continue;
        }

        // We are passing true to ignoreDisabledFlag because we have already filtered out the
        // disabled agents making this additional check a waste of time.
        currAgent.getRelatedAirings(allAirs, controlCPUUsage, true, legacyKeyword, allAirsMap, cache, sbCache);
        boolean isFavorite = currAgent.isFavorite();

        if (isFavorite)
        {
          int potsWorkCacheSize = cache.airWorkCache.size;
          callback.appendCapacityNewLoveAirSet(potsWorkCacheSize);
          for (int i = 0; i < potsWorkCacheSize; i++)
            callback.addNewLoveAirSet(cache.airWorkCache.data[i]);

          // This only happens for active favorites which should number less than 500, so this isn't
          // going to amount to anything crazy for the GC. We are trying to take advantage of any
          // scratch space left in the currently allocated cache list.
          cache.airWorkCache.setOffset();
          // Also check the rem airs so we're sure we get EVERYTHING in the DB that applies to this
          // Favorite included in the group.
          //
          // We are passing true to ignoreDisabledFlag because we have already filtered out the
          // disabled agents making this additional check a waste of time.
          currAgent.getRelatedAirings(remAirs, controlCPUUsage, true, legacyKeyword, remAirsMap,
            cache, cache.sbCache);

          int remWorkCacheSize = cache.airWorkCache.size;
          callback.appendCapacityNewLoveAirSet(remWorkCacheSize);
          for (int i = cache.airWorkCache.offset; i < remWorkCacheSize; i++)
            callback.addNewLoveAirSet(cache.airWorkCache.data[i]);

          // Remove the entries we just appended and gets rid of the offset so they don't break
          // anything below.
          cache.airWorkCache.clearOffset();
        }

        // Check to see if this Agent is a Favorite who has a keep at most limit set with
        // manual deleting. In that case, we don't add any of its future airings to the
        // mustSeeSet or to the pots, but we do put them in the loveAirSet.
        boolean dontScheduleThisAgent = false;
        if (isFavorite && currAgent.testAgentFlag(Agent.DONT_AUTODELETE_FLAG) &&
          currAgent.getAgentFlag(Agent.KEEP_AT_MOST_MASK) > 0)
        {
          int fileCount = 0;
          for (int j = 0, potsWorkCacheSize = cache.airWorkCache.size; j < potsWorkCacheSize; j++)
          {
            MediaFile mf = wiz.getFileForAiring(cache.airWorkCache.data[j]);
            if (mf != null && mf.isCompleteRecording())
              fileCount++;
          }
          int keepAtMost = currAgent.getAgentFlag(Agent.KEEP_AT_MOST_MASK);
          if (fileCount >= keepAtMost)
          {
            dontScheduleThisAgent = true;
          }
        }

        int potsWorkCacheSize = cache.airWorkCache.size;
        boolean negator = currAgent.isNegativeNelly();
        if (negator)
          callback.appendCapacityBlackBalled(potsWorkCacheSize);
        else
          callback.appendCapacityAirSet(potsWorkCacheSize);

        for (int j = 0; j < potsWorkCacheSize; j++)
        {
          Airing agentPot = cache.airWorkCache.data[j];
          if (negator)
          {
            callback.addBlackBalled(agentPot);
            continue;
          }

          // Skip this stuff
          Show show = agentPot.getShow();
          // The show title can be null.
          if ((show != null && show.title != null && show.title.equalsIgnoreCase(paidProgRez)) ||
            wiz.isNoShow(agentPot.showID))
            continue;

          callback.addAirSet(agentPot);

          /*
           * NOTE: On 7/22/03 I moved the isWatched() down from the beginning of this loop.
           * We want to keep watched airings in the Agent lookup table so we can
           * use the agentFlags on them even after they're watched. So now watched
           * airings will have a cause agent, but their WP will be zero.
           */
          /*
           * NOTE: Narflex - 8/4/08 - Fixed bug where we wouldn't use the higher priority favorite
           * in the cause map since comparing watchProb would be equal; this would then cause scheduling
           * issues because the wrong agent was used for the scheduling that should have been higher priority sometimes
           */

          // This get evaluated at least once for all airings, so let's just doing it here and use
          // this value.
          boolean isWatchedForSchedulingPurposes = agentPot.isWatchedForSchedulingPurpose();

          // This is a more efficient object than what we had when we were maintaining two maps that
          // basically work with the same information presented different ways. This also cuts down
          // on the Float object creation until after we are done processing all of the agents.
          WPCauseValue causeValue = callback.addOrReturnWPCauseValue(agentPot, currAgent);

          // We just added this agent.
          if (causeValue == null)
          {
            if (isWatchedForSchedulingPurposes)
              callback.addWatchedPotsToClear(agentPot);
          }
          else if (causeValue.compareAndReplace(currAgent, false) && isWatchedForSchedulingPurposes)
          {
            callback.addWatchedPotsToClear(agentPot);
          }

          if (isWatchedForSchedulingPurposes)
            continue;
          if (dontScheduleThisAgent)
          {
            MediaFile mf = wiz.getFileForAiring(agentPot);
            if (mf == null || !mf.isCompleteRecording())
            {
              callback.addWatchedPotsToClear(agentPot);
              continue;
            }
          }
          if (isFavorite)
            callback.addNewMustSeeSet(agentPot);
        }
      }

      callback.setComplete();
      return callback;
    }
  }

  public class CarnyWorkerCallback
  {
    private final ArrayList<WPCauseValue> newWPCauses = new ArrayList<>();
    private final ArrayList<Agent> traitors = new ArrayList<>();
    // There are always a lot of these.
    private final ArrayList<Airing> newLoveAirSet = new ArrayList<>();
    private final ArrayList<Airing> blackBalled = new ArrayList<>();
    // This gets particularly large making it more ideal to use a HashSet instead of a list.
    private final ArrayList<Airing> airSet = new ArrayList<>();
    // There are always even more of these.
    private final ArrayList<Airing> watchedPotsToClear = new ArrayList<>();
    private final ArrayList<Airing> newMustSeeSet = new ArrayList<>();
    private boolean complete = false;

    public void setComplete()
    {
      complete = true;
    }

    public WPCauseValue replaceWPCauseValue(Airing airing, Agent agent)
    {
      return binaryReplace(newWPCauses, airing, agent);
    }

    public WPCauseValue addOrReturnWPCauseValue(WPCauseValue newEntry)
    {
      return binaryInsertOrReturn(newWPCauses, newEntry.airing, newEntry.agent, newEntry);
    }

    public WPCauseValue addOrReturnWPCauseValue(Airing airing, Agent agent)
    {
      return binaryInsertOrReturn(newWPCauses, airing, agent, null);
    }

    public void addTraitor(Agent traitor)
    {
      // We are iterating though these once, so this is the only thing that will always be unique.
      traitors.add(traitor);
    }

    public void appendCapacityNewLoveAirSet(int size)
    {
      newLoveAirSet.ensureCapacity(newLoveAirSet.size() + size);
    }

    public void addNewLoveAirSet(Airing airing)
    {
      binaryInsert(newLoveAirSet, airing);
    }

    public void appendCapacityBlackBalled(int size)
    {
      blackBalled.ensureCapacity(blackBalled.size() + size);
    }

    public void addBlackBalled(Airing airing)
    {
      binaryInsert(blackBalled, airing);
    }

    public void appendCapacityAirSet(int size)
    {
      airSet.ensureCapacity(airSet.size() + size);
    }

    public void addAirSet(Airing airing)
    {
      binaryInsert(airSet, airing);
    }

    public void removeAirSet(Airing airing)
    {
      binaryRemove(airSet, airing);
    }

    public void addWatchedPotsToClear(Airing airing)
    {
      binaryInsert(watchedPotsToClear, airing);
    }

    public void addNewMustSeeSet(Airing airing)
    {
      binaryInsert(newMustSeeSet, airing);
    }

    public void removeNewMustSeeSet(Airing airing)
    {
      binaryRemove(newMustSeeSet, airing);
    }

    private WPCauseValue binaryReplace(List<WPCauseValue> list, Airing airing, Agent agent)
    {
      int id = airing.getID();
      int low = 0;
      int high = list.size() - 1;

      while (low <= high)
      {
        int mid = (low + high) >>> 1;
        WPCauseValue causeValue = list.get(mid);
        int midVal = causeValue.airing.getID();

        if (midVal < id)
          low = mid + 1;
        else if (midVal > id)
          high = mid - 1;
        else
        {
          WPCauseValue newValue = new WPCauseValue(agent, airing);
          list.set(mid, newValue); // key found
          return newValue;
        }
      }
      WPCauseValue newValue = new WPCauseValue(agent, airing);
      list.add(low, newValue); // key not found.
      return newValue;
    }

    private WPCauseValue binaryInsertOrReturn(List<WPCauseValue> list, Airing airing, Agent agent, WPCauseValue newEntry)
    {
      int id = airing.getID();
      int low = 0;
      int high = list.size() - 1;

      while (low <= high)
      {
        int mid = (low + high) >>> 1;
        WPCauseValue causeValue = list.get(mid);
        int midVal = causeValue.airing.getID();

        if (midVal < id)
          low = mid + 1;
        else if (midVal > id)
          high = mid - 1;
        else
          return causeValue; // key found
      }
      list.add(low, newEntry != null ? newEntry : new WPCauseValue(agent, airing)); // key not found.
      // Return null if we added the agent since we have nothing further to do with it in that case.
      return null;
    }

    private void binaryInsert(List<Airing> list, Airing airing)
    {
      int id = airing.getID();
      int low = 0;
      int high = list.size() - 1;

      while (low <= high)
      {
        int mid = (low + high) >>> 1;
        int midVal = list.get(mid).getID();

        if (midVal < id)
          low = mid + 1;
        else if (midVal > id)
          high = mid - 1;
        else
          return; // key found
      }
      list.add(low, airing); // key not found.
    }

    private void binaryRemove(List<Airing> list, Airing airing)
    {
      int id = airing.getID();
      int low = 0;
      int high = list.size() - 1;

      while (low <= high)
      {
        int mid = (low + high) >>> 1;
        int midVal = list.get(mid).getID();

        if (midVal < id)
          low = mid + 1;
        else if (midVal > id)
          high = mid - 1;
        else
        {
          list.remove(mid);
          return;
        }; // key found
      }
      // key not found.
    }
  }

  public class WPCauseValue
  {
    // We are keying on airing.
    final private Airing airing;
    private Agent agent;
    private float wp;

    public WPCauseValue(Agent agent, Airing airing)
    {
      this.airing = airing;
      this.agent = agent;
      this.wp = agent != null ? agent.watchProb : MIN_WP;
    }

    /**
     * Compares the provided agent against the current agent and replaces it and the watch
     * probability of the compared agent is more desirable.
     *
     * @param compareAgent The agent to compare.
     * @param order Use {@link #AGENT_SORTER} to ensure agents are also prioritized by their sorted
     *             order when they have the same watch potential.
     * @return <code>true</code> if the agent was replaced.
     */
    public boolean compareAndReplace(Agent compareAgent, boolean order)
    {
      float compareAgentWP = compareAgent.watchProb;
      if (agent == null || compareAgentWP > wp)
      {
        agent = compareAgent;
        wp = compareAgentWP;
        return true;
      }

      if (compareAgentWP == wp && (compareAgent.isFavorite() || order))
      {
        // Check for agent priority. This returns the higher priority agent if there is one or null
        // if no explicit priority is defined. When this is null there is an implicit priority that
        // based on the AGENT_SORTER comparator. When order is true, we considering this sorting
        // order in the determination on if we need to replace the agent or not.
        Agent winner = doBattle(agent, compareAgent);
        if (winner == compareAgent ||
          order && winner == null && AGENT_SORTER.compare(agent, compareAgent) > 0)
        {
          agent = compareAgent;
          wp = compareAgentWP;
          return true;
        }
      }

      return false;
    }
  }

  public class CarnyCache
  {
    // Shared.
    final String paidProgRez;
    final Map<Integer, Airing[]> allAirsMap;
    final Map<Integer, Airing[]> remAirsMap;
    final Map<Integer, Airing[]> watchAirsMap;
    final Map<Integer, Wasted[]> wastedAirsMap;
    final int hashZero;
    final boolean useMaps;

    // Per thread.
    final StringBuilder sbCache = new StringBuilder();
    // This is the array that will actually be used and will return our results.
    CacheList airWorkCache = null;
    // This is the hashes we are working with for the current agent. This prevents us from getting
    // this list up to 4 times and the GC associated with it.
    final List<Integer> currentHashes = new ArrayList<>();

    // It's actually faster to cache and clear these between runs of watchProb().
    final Map<Stringer, MutableFloat> titleWatchMap = new HashMap<>();
    final Map<Stringer, MutableInteger> titleAllMap = new HashMap<>();

    // Clone the shared parts of the cache.
    public CarnyCache(CarnyCache cache)
    {
      this(cache.allAirsMap, cache.remAirsMap, cache.watchAirsMap, cache.wastedAirsMap,
        cache.paidProgRez, cache.hashZero, cache.useMaps);
    }

    // Create a new shared cache.
    public CarnyCache(Map<Integer, Airing[]> allAirsMap, Map<Integer, Airing[]> remAirsMap,
                      Map<Integer, Airing[]> watchAirsMap, Map<Integer, Wasted[]> wastedAirsMap)
    {
      this(allAirsMap, remAirsMap, watchAirsMap, wastedAirsMap,
        Sage.rez("Paid_Programming").toLowerCase(),
        (allAirsMap != null && remAirsMap != null && watchAirsMap != null && wastedAirsMap != null &&
          allAirsMap.get(0) == null && remAirsMap.get(0) == null &&
          watchAirsMap.get(0) == null && wastedAirsMap.get(0) == null) ? 0 : -1,
        (allAirsMap != null && remAirsMap != null && watchAirsMap != null && wastedAirsMap != null));
    }

    private CarnyCache(Map<Integer, Airing[]> allAirsMap, Map<Integer, Airing[]> remAirsMap,
                       Map<Integer, Airing[]> watchAirsMap, Map<Integer, Wasted[]> wastedAirsMap,
                       String paidProgRez, int hashZero, boolean useMaps)
    {
      this.paidProgRez = paidProgRez;
      this.allAirsMap = allAirsMap;
      this.remAirsMap = remAirsMap;
      this.watchAirsMap = watchAirsMap;
      this.wastedAirsMap = wastedAirsMap;

      // If we know none of the maps have anything mapped to zero, we don't need to check this for
      // anything and this gives us a small performance bump.
      this.hashZero = hashZero;
      this.useMaps = useMaps;
    }
  }

  /**
   * This is used for faster operations done by the agents that require array storage.
   */
  public static class CacheList
  {
    // All binary searches must use offset as their starting index.
    int offset = 0;
    // This number must not be adjusted to accommodate the apparent size by subtracting
    // offset.
    int size = 0;
    Airing data[];

    public CacheList(int size)
    {
      this.data = new Airing[size];
    }

    /**
     * Ensures that the requested capacity is free in the array.
     * <p/>
     * The provided capacity is added to the current size of the array and if the resulting number
     * is bigger than the currently allocated array, a bigger array will be allocated to accommodate
     * the anticipated data.
     *
     * @param totalNeeded Additional capacity required.
     */
    public void ensureAddCapacity(int totalNeeded)
    {
      totalNeeded += size;
      if (totalNeeded > data.length)
      {
        int newSize = data.length + 1000;
        if (newSize < 0 /*overflow*/ || newSize < totalNeeded)
          newSize = totalNeeded;
        if (newSize == Integer.MAX_VALUE && data.length == Integer.MAX_VALUE)
          throw new OutOfMemoryError();
        data = Arrays.copyOf(data, newSize);
      }
    }

    /**
     * Binary sort the array.
     */
    public void sort()
    {
      Arrays.sort(data, offset, size, DBObject.ID_COMPARATOR);
    }

    /**
     * Search for an airing based between the current offset and size.
     *
     * @param key The airing to look up.
     * @return <code>true</code> if the airing was found.
     */
    public int binarySearch(Airing key)
    {
      return binarySearch(data, offset, size, key);
    }

    /**
     * Search for an airing based between the current offset and specified end (exclusive).
     *
     * @param end The end of the range to search.
     * @param key The airing to look up.
     * @return <code>true</code> if the airing was found in the requested range.
     */
    public int binarySearch(int end, Airing key)
    {
      return binarySearch(data, offset, end, key);
    }

    /**
     * Search for an airing based between the specified start (inclusive) and specified end
     * (exclusive).
     *
     * @param start The start of the range to search.
     * @param end The end of the range to search.
     * @param key The airing to look up.
     * @return <code>true</code> if the airing was found in the requested range.
     */
    public int binarySearch(int start, int end, Airing key)
    {
      return binarySearch(data, start, end, key);
    }

    private static int binarySearch(Airing array[], int start, int end, Airing key)
    {
      int id = key.getID();
      int low = start;
      int high = end - 1;

      while (low <= high)
      {
        int mid = (low + high) >>> 1;
        int midVal = array[mid].getID();

        if (midVal < id)
          low = mid + 1;
        else if (midVal > id)
          high = mid - 1;
        else
          return mid;
      }

      if (Agent.VERIFY_AIRING_OPTIMIZATION)
      {
        // This is only a problem if the issue is within the range we asked for.
        int index = -1;
        for (int i = start; i < end; i++)
        {
          if (array[i].equals(key))
          {
            index = i;
            break;
          }
        }

        if (index != -1)
        {
          StringBuilder stringBuilder = new StringBuilder("FAILED: to match what should have matched: ");
          stringBuilder.append(key.getID()).append(" is in: ");
          for (int i = start; i < end; i++)
          {
            Airing object = array[i];
            if (object == key)
              stringBuilder.append("****");
            stringBuilder.append(object.getID()).append(',');
          }
          System.out.println(stringBuilder);
        }
      }

      return -(low + 1);
    }

    /**
     * Add new airing only if a binary search returns that the airing does not already exist.
     * <p/>
     * This will insert the airing binary sorted. This will also ensure the required capacity to add
     * if it needs to add the airing.
     *
     * @param airing The airing to add.
     * @return <code>true</code> if the airing was added.
     */
    public boolean binaryAdd(Airing airing)
    {
      int index = binarySearch(airing);
      if (index < 0)
      {
        index = -(index + 1);
        ensureAddCapacity(1);
        add(index, airing);
        return true;
      }
      return false;
    }

    /**
     * Add a new airing to the end of this array.
     * <p/>
     * This method has no implicit sorting.
     *
     * @param airing The airing to add.
     */
    public void add(Airing airing)
    {
      data[size++] = airing;
    }

    /**
     * Add a new airing at a specific index.
     *
     * @param index The index.
     * @param airing The airing to add.
     */
    public void add(int index, Airing airing)
    {
      // We do this when appropriate instead of on every add.
      //ensureCacheCapacity(useWorkCacheSize + index);
      System.arraycopy(data, index, data, index + 1, size - index);
      data[index] = airing;
      size++;
    }

    /**
     * Append a range of an existing array to this array.
     *
     * @param src The source array.
     * @param start The starting index to copy (inclusive).
     * @param end The ending index to copy (exclusive).
     */
    public void add(Airing src[], int start, int end)
    {
      int len = end - start;
      System.arraycopy(src, start, data, size, len);
      size += len;
    }

    /**
     * Defines the current cache offset to be the current size of the cache.
     */
    public void setOffset()
    {
      offset = size;
    }

    /**
     * Clears the cache offset defined by {@link #setOffset()} and resets the size to the last
     * cache offset.
     */
    public void clearOffset()
    {
      size = offset;
      offset = 0;
    }

    /**
     * Resets the cache to the last offset. (not necessarily 0)
     */
    public void clear()
    {
      size = offset;
    }

    /**
     * Get the apparent cache size.
     * <p/>
     * This is not to be used for to determine what the next index will be. The next index is always
     * based on zero regardless of the offset for performance reasons.
     *
     * @return The cache size adjusted for the current offset.
     */
    public int apparentSize()
    {
      return size - offset;
    }

    /**
     * Create and return a copy of this list.
     */
    public CacheList copy()
    {
      CacheList list = new CacheList(data.length);
      System.arraycopy(data, 0, list.data, 0, size);
      list.size = size;
      list.offset = offset;
      return list;
    }

    /**
     * Get a list representation of this array.
     */
    public List<Airing> getList()
    {
      return Arrays.asList(data).subList(offset, size);
    }
  }
}
