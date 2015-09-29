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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * New Trends need the following:
 * 1. Add to trend list in Carny
 */
public class Agent extends DBObject implements Favorite
{
  private static final int MIN_TOTAL = 2;
  private static final float MIN_WATCH_PROB = 0f;
  private static final float MAX_WATCH_PROB = 0.99f;

  private static final boolean ENABLE_AGENT_AIRING_CACHE = false;

  private static final int CPU_CONTROL_MOD_COUNT = Sage.getInt("profiler_cpu_mod_count", Sage.EMBEDDED ? 2500 : 25000);

  public static final int LOVE_MASK = 0x0001;
  public static final int TITLE_MASK = 0x0002;
  public static final int CATEGORY_MASK = 0x0004;
  public static final int ACTOR_MASK = 0x0008;
  public static final int NETWORK_MASK = 0x0010;
  public static final int CHANNEL_MASK = 0x0020;
  public static final int RATED_MASK = 0x0040;
  public static final int YEAR_MASK = 0x0080;
  public static final int PR_MASK = 0x0100;
  public static final int DAYSLOT_MASK = 0x0200;
  public static final int TIMESLOT_MASK = 0x0400;
  public static final int FULLSLOT_MASK = 0x0800;
  private static final int PRIME_MASK = 0x1000;
  // These are restrictive, i.e. we'll record reruns unless firstrun_mask is set
  public static final int FIRSTRUN_MASK = 0x2000;
  public static final int RERUN_MASK = 0x4000;
  public static final int KEYWORD_MASK = 0x8000;

  public static final int DONT_AUTODELETE_FLAG = 0x01;
  public static final int KEEP_AT_MOST_MASK = 0x7E; // 6 bits
  public static final int DELETE_AFTER_CONVERT_FLAG = 0x80;
  public static final int DISABLED_FLAG = 0x100;

  String getNameForType()
  {
    StringBuilder sb = new StringBuilder();
    if ((agentMask & LOVE_MASK) != 0) sb.append("Like");
    if ((agentMask & TITLE_MASK) != 0) sb.append("Title");
    if ((agentMask & CATEGORY_MASK) != 0) sb.append("Cat");
    if ((agentMask & ACTOR_MASK) != 0) sb.append("Person");
    if ((agentMask & RATED_MASK) != 0) sb.append("Rated");
    if ((agentMask & YEAR_MASK) != 0) sb.append("Year");
    if ((agentMask & PR_MASK) != 0) sb.append("PR");
    if ((agentMask & CHANNEL_MASK) != 0) sb.append("Chan");
    if ((agentMask & NETWORK_MASK) != 0) sb.append("Net");
    if ((agentMask & FIRSTRUN_MASK) != 0) sb.append("FirstRuns");
    if ((agentMask & RERUN_MASK) != 0) sb.append("ReRuns");
    if ((agentMask & KEYWORD_MASK) != 0) sb.append("Keyword");
    if (slotType == BigBrother.FULL_ALIGN) sb.append("FullSlot");
    if (slotType == BigBrother.DAY_ALIGN) sb.append("DaySlot");
    if (slotType == BigBrother.TIME_ALIGN) sb.append("TimeSlot");
    sb.append("Trend");
    return sb.toString();
  }

  Agent(int inID)
  {
    super(inID);
    agentID = id;
    wiz = Wizard.getInstance();
    createTime = Sage.time();
    weakAgents = new int[0];
    quality = "";
    autoConvertFormat = "";
  }
  Agent(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    wiz = Wizard.getInstance();
    agentID = id;
    agentMask = in.readInt();
    title = wiz.getTitleForID(readID(in, idMap));
    Stringer primetitle = null;
    if (ver < 0x2A)
      primetitle = wiz.getPrimeTitleForID(readID(in, idMap));
    category = wiz.getCategoryForID(readID(in, idMap));
    subCategory = wiz.getSubCategoryForID(readID(in, idMap));
    person = wiz.getPersonForID(readID(in, idMap));
    if (ver >= 0x2F)
      in.readBoolean(); // padding to help avoid reversing db encryption algorithms
    rated = wiz.getRatedForID(readID(in, idMap));
    year = wiz.getYearForID(readID(in, idMap));
    pr = wiz.getPRForID(readID(in, idMap));
    setChannelName(in.readUTF());
    network = wiz.getNetworkForID(readID(in, idMap));
    createTime = in.readLong();
    if (ver < 0x4B)
    {
      int slotty = in.readInt();
      if (slotty != 0)
      {
        timeslots = new int[] { slotty };
      }
    }
    else
    {
      int numSlots = in.readInt();
      if (numSlots > 0)
      {
        timeslots = new int[numSlots];
        for (int i = 0; i < numSlots; i++)
          timeslots[i] = in.readInt();
      }
    }
    slotType = in.readInt();
    weakAgents = new int[in.readInt()];
    for (int i = 0; i < weakAgents.length; i++)
      weakAgents[i] = readID(in, idMap);
    if (ver < 0x2D)
    {
      int numWeakAirings = in.readInt(); // legacy
      for (int i = 0; i < numWeakAirings; i++)
        readID(in, idMap);
    }
    quality = in.readUTF();
    if (quality != null && quality.length() > 0)
      quality = MMC.cleanQualityName(quality);
    if (ver > 0x24)
    {
      startPad = in.readLong();
      stopPad = in.readLong();
    }
    if (ver > 0x27)
      agentFlags = in.readInt();

    if (ver < 0x2A)
    {
      // Prime title conversion
      if (primetitle == null && title != null)
      {
        agentMask = agentMask | RERUN_MASK;
      }
      else if (primetitle != null)
      {
        title = wiz.getTitleForName(primetitle.name);
        agentMask = (agentMask & (~PRIME_MASK)) | FIRSTRUN_MASK | TITLE_MASK;
      }
    }
    if (ver > 0x2A)
      role = in.readByte();
    else if (person != null)
      role = Show.ALL_ROLES;
    if (ver > 0x33)
      keyword = in.readUTF();
    if (ver > 0x41)
      autoConvertFormat = in.readUTF();
    if (ver > 0x42)
    {
      String s = in.readUTF();
      if (s == null || s.length() == 0)
        autoConvertDest = null;
      else
        autoConvertDest = new File(IOUtils.convertPlatformPathChars(s));
    }
    if (ver > 0x43)
    {
      buildFavProps(in.readUTF());
    }
  }

  private void buildFavProps(String str)
  {
    if (str != null && str.length() > 0)
    {
      if (favProps == null)
        favProps = new Properties();
      else
        favProps.clear();
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
          favProps.setProperty(name, value);
        }
      }
    }
    else if (favProps != null)
      favProps.clear();
  }

  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    boolean useLookupIdx = (flags & Wizard.WRITE_OPT_USE_ARRAY_INDICES) != 0;
    out.writeInt(agentMask);
    out.writeInt((title == null) ? 0 : (useLookupIdx ? title.lookupIdx : title.id));
    out.writeInt((category == null) ? 0 : (useLookupIdx ? category.lookupIdx : category.id));
    out.writeInt((subCategory == null) ? 0 : (useLookupIdx ? subCategory.lookupIdx : subCategory.id));
    out.writeInt((person == null) ? 0 : (useLookupIdx ? person.lookupIdx : person.id));
    out.writeBoolean(true); // padding to help avoid reversing db encryption algorithms
    out.writeInt((rated == null) ? 0 : (useLookupIdx ? rated.lookupIdx : rated.id));
    out.writeInt((year == null) ? 0 : (useLookupIdx ? year.lookupIdx : year.id));
    out.writeInt((pr == null) ? 0 : (useLookupIdx ? pr.lookupIdx : pr.id));
    out.writeUTF(chanName);
    out.writeInt((network == null) ? 0 : (useLookupIdx ? network.lookupIdx : network.id));
    out.writeLong(createTime);
    out.writeInt(timeslots == null ? 0 : timeslots.length);
    if (timeslots != null)
    {
      for (int i = 0; i < timeslots.length; i++)
      {
        out.writeInt(timeslots[i]);
      }
    }
    out.writeInt(slotType);
    out.writeInt(weakAgents.length);
    for (int i = 0; i < weakAgents.length; i++)
      out.writeInt(weakAgents[i]);
    out.writeUTF(quality);
    out.writeLong(startPad);
    out.writeLong(stopPad);
    out.writeInt(agentFlags);
    out.writeByte(role);
    out.writeUTF(keyword);
    out.writeUTF(autoConvertFormat);
    out.writeUTF(autoConvertDest == null ? "" : autoConvertDest.getAbsolutePath());
    if (favProps == null)
      out.writeUTF("");
    else
    {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<Object, Object> ent : favProps.entrySet())
      {
        sb.append(sage.media.format.MediaFormat.escapeString(ent.getKey().toString()));
        sb.append('=');
        sb.append(sage.media.format.MediaFormat.escapeString(ent.getValue().toString()));
        sb.append(';');
      }
      out.writeUTF(sb.toString());
    }
  }

  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("Agent[Trend=");
    sb.append(getNameForType());
    if (title != null) sb.append(", Title=" + title.name);
    if (category != null) sb.append(", Cat=" + category.name);
    if (subCategory != null) sb.append(", SubCat=" + subCategory.name);
    if (person != null) sb.append(", Person=" + person.name);
    if (rated != null) sb.append(", Rated=" + rated.name);
    if (year != null) sb.append(", Year=" + year.name);
    if (pr != null) sb.append(", PR=" + pr.name);
    if (chanName.length() != 0) sb.append(", Chan=" + chanName);
    if (network != null) sb.append(", Network=" + network.name);
    if (timeslots != null && timeslots.length > 0)
    {
      sb.append(", Timeslot=");
      for (int i = 0; i < timeslots.length; i++)
      {
        if (i != 0)
          sb.append(", ");
        sb.append(timeslots[i]);
      }
    }
    if (keyword.length() > 0) sb.append(", Keyword=" + keyword);
    if (negator) sb.append(" NEGATOR ");
    sb.append(" id=");
    sb.append(agentID);
    sb.append(" watchProb=");
    sb.append(watchProb);
    sb.append(" createTime=");
    sb.append(Sage.df(createTime));
    if (stopPad != 0)
    {
      sb.append(" stopPad=").append(stopPad);
    }
    if (startPad != 0)
      sb.append(" startPad=").append(startPad);
    sb.append(" del=").append((testAgentFlag(DONT_AUTODELETE_FLAG) ? "manual" : "auto"));
    int keepAtMost = getAgentFlag(KEEP_AT_MOST_MASK);
    if (keepAtMost > 0)
      sb.append(" keep=").append(keepAtMost);
    sb.append(" enabled=").append(!testAgentFlag(DISABLED_FLAG));
    sb.append(']');
    return sb.toString();
  }

  // DO NOT use this to change the channel name; only use it when creating the object to set the internal ch array
  protected void setChannelName(String inName)
  {
    chanName = inName;
    if (chanName != null && chanName.length() > 0)
    {
      StringTokenizer toker = new StringTokenizer(chanName, ";,");
      chanNames = new String[toker.countTokens()];
      for (int i = 0; i < chanNames.length; i++)
        chanNames[i] = toker.nextToken();
    }
    else
      chanNames = null;
  }

  void bully(Agent weaker)
  {
    if (weaker == this) return;
    try {
      wiz.acquireWriteLock(Wizard.AGENT_CODE);
      for (int i = 0; i < weakAgents.length; i++)
        if (weakAgents[i] == weaker.id)
          return;

      /*
       * We need to ensure consistency in Agent priorities whenever we create a new Agent
       * The first thing to do is find all of the recursive weak children of the Agent
       * we're about to make our underling.
       */
      List<Agent> allWeaklings = new ArrayList<Agent>();
      allWeaklings.add(weaker);
      for (int i = 0; i < allWeaklings.size(); i++)
      {
        Agent bond = allWeaklings.get(i);
        for (int j = 0; j < bond.weakAgents.length; j++)
        {
          Agent foo = wiz.getAgentForID(bond.weakAgents[j]);
          if (foo != null && !allWeaklings.contains(foo))
            allWeaklings.add(foo);
        }
      }
      /*
       * Now go through the list of all of the weaklings. Any Agents in that list who have a
       * weakAgent that is 'this', must have that relationship removed, or an inconsistency will result.
       */
      for (int i = 0; i < allWeaklings.size(); i++)
      {
        Agent bond = allWeaklings.get(i);
        for (int j = 0; j < bond.weakAgents.length; j++)
          if (bond.weakAgents[j] == id)
          {
            bond.unbully(this);
            break;
          }
      }

      int[] newWeaks = new int[weakAgents.length + 1];
      System.arraycopy(weakAgents, 0, newWeaks, 0, weakAgents.length);
      newWeaks[weakAgents.length] = weaker.id;
      weakAgents = newWeaks;
      wiz.logUpdate(this, Wizard.AGENT_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.AGENT_CODE);
    }
  }

  void unbully(Agent weaker)
  {
    unbully(weaker.id);
  }
  void unbully(int weakerID)
  {
    try {
      wiz.acquireWriteLock(Wizard.AGENT_CODE);
      int i = 0;
      for (i = 0; i < weakAgents.length; i++)
        if (weakAgents[i] == weakerID)
          break;
      if (i == weakAgents.length) return;
      int[] newWeaks = new int[weakAgents.length - 1];
      int j = 0;
      for (i = 0; i < weakAgents.length; i++)
        if (weakAgents[i] != weakerID)
          newWeaks[j++] = weakAgents[i];
      weakAgents = newWeaks;
      wiz.logUpdate(this, Wizard.AGENT_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.AGENT_CODE);
    }
  }

  /*
   * The related airings don't change unless there's an update to the DB, so
   * cache this call.
   * This gets called from the AgentBrowser GUI as well as the Carny's main
   * thread, that's why it needs to be synced.
   */
  public synchronized Airing[] getRelatedAirings(DBObject[] allAirs, boolean mainCache, boolean controlCPUUsage,
      StringBuffer sbCache)
  {
    if (allAirs == null) return Pooler.EMPTY_AIRING_ARRAY;

    if (ENABLE_AGENT_AIRING_CACHE)
    {
      if (mainCache)
      {
        if (relAirCache != null && cacheTimestamp > wiz.getLastModified())
          return relAirCache;
        cacheTimestamp = Sage.time();
      }
      else
      {
        if (relAirCache2 != null && cacheTimestamp2 > wiz.getLastModified())
          return relAirCache2;
        cacheTimestamp2 = Sage.time();
      }
    }

    ArrayList<Airing> rv = new ArrayList<Airing>();
    boolean keywordTest = (this.agentMask&(LOVE_MASK|KEYWORD_MASK)) == (LOVE_MASK|KEYWORD_MASK);
    if(keywordTest) {
      // If we're only doing a keyword mask, speed it up via Lucene
      Show[] shows = wiz.searchShowsByKeyword(getKeyword());
      for (Show show : shows) {
        Airing[] airings = wiz.getAirings(show, 0);
        for(Airing a : airings) {
          if (followsTrend(a, true, sbCache, true))
            rv.add(a);
        }
      }
    } else {
      for (int i = 0; i < allAirs.length; i++)
      {
        Airing a = (Airing) allAirs[i];
        if (a == null) continue;
        if (followsTrend(a, true, sbCache))
          rv.add(a);
        if ((i % CPU_CONTROL_MOD_COUNT) == 0 && controlCPUUsage)
          try{Thread.sleep(Carny.SLEEP_PERIOD);}catch(Exception e){}
      }
    }
    if (ENABLE_AGENT_AIRING_CACHE)
    {
      if (mainCache)
        return (relAirCache = rv.toArray(Pooler.EMPTY_AIRING_ARRAY));
      else
        return (relAirCache2 = rv.toArray(Pooler.EMPTY_AIRING_ARRAY));
    }
    else
      return rv.toArray(Pooler.EMPTY_AIRING_ARRAY);
  }

  boolean validate()
  {
    if ((((agentMask & LOVE_MASK) == LOVE_MASK) && agentMask != LOVE_MASK) ||
        Carny.getInstance().isBaseTrend(agentMask))
    {
      if ((agentMask & TITLE_MASK) != 0 && title == null) return false;
      if ((agentMask & CATEGORY_MASK) != 0 && category == null)  return false;
      if ((agentMask & ACTOR_MASK) != 0 && person == null) return false;
      if ((agentMask & RATED_MASK) != 0 && rated == null) return false;
      if ((agentMask & YEAR_MASK) != 0 && year == null) return false;
      if ((agentMask & PR_MASK) != 0 && pr == null) return false;
      if ((agentMask & CHANNEL_MASK) != 0 && chanName.length() == 0) return false;
      if ((agentMask & NETWORK_MASK) != 0 && network == null) return false;
      if ((agentMask & KEYWORD_MASK) != 0 && keyword.length() == 0) return false;
      return true;
    }
    else
      return false;
  }

  synchronized void update(DBObject fromMe)
  {
    // Clear the airings caches
    cacheTimestamp = cacheTimestamp2 = 0;

    Agent bond = (Agent) fromMe;
    agentMask = bond.agentMask;
    createTime = bond.createTime;
    title = bond.title;
    category = bond.category;
    subCategory = bond.subCategory;
    setChannelName(bond.chanName);
    network = bond.network;
    rated = bond.rated;
    year = bond.year;
    pr = bond.pr;
    person = bond.person;
    slotType = bond.slotType;
    timeslots = (bond.timeslots == null ? null : ((int[]) bond.timeslots.clone()));
    weakAgents = bond.weakAgents.clone();
    quality = bond.quality;
    autoConvertFormat = bond.autoConvertFormat;
    autoConvertDest = bond.autoConvertDest;
    startPad = bond.startPad;
    stopPad = bond.stopPad;
    agentFlags = bond.agentFlags;
    role = bond.role;
    keyword = bond.keyword;
    if (bond.favProps != null)
      favProps = (Properties) bond.favProps.clone();
    else
      favProps = null;
    super.update(fromMe);
  }

  public boolean isFirstRunsOnly() { return (agentMask & FIRSTRUN_MASK) == FIRSTRUN_MASK; }
  public boolean isReRunsOnly() { return (agentMask & RERUN_MASK) == RERUN_MASK; }
  public boolean firstRunsAndReruns() { return !isFirstRunsOnly() && !isReRunsOnly(); }
  public String getTitle() { return (title == null) ? "" : title.name; }
  public String getCategory() { return (category == null) ? "" : category.name; }
  public String getSubCategory() { return (subCategory == null) ? "" : subCategory.name; }
  public String getPerson() { return (person == null) ? "" : person.name; }
  public Person getPersonObj() { return person; }
  public String getRated() { return (rated == null) ? "" : rated.name; }
  public int getRole() { return role; }
  public String getYear() { return (year == null) ? "" : year.name; }
  public String getPR() { return (pr == null) ? "" : pr.name; }
  public String getChannelName() { return chanName; }
  public String getNetwork() { return (network == null) ? "" : network.name; }
  public int[] getTimeslots() { return timeslots; }
  public int getSlotType() { return slotType; }
  public String getKeyword() { return keyword; }

  public boolean followsTrend(Airing air, boolean mustBeViewable, StringBuffer sbCache)
  {
    return followsTrend(air, mustBeViewable, sbCache, false);
  }

  /**
   * Determine if the given airing meets the criteria for this Agent. (i.e. could the given Airing be scheduled because
   * of this Agent)
   *
   * @param air The Airing to be tested
   * @param mustBeViewable If true, the Airing must be viewable for this method to return true.  Viewable means a 
   * recording that can be watched, or a channel that can be viewed.
   * @param sbCache A StringBuffer to be used by this method.  If null a new StringBuffer will be created.  If non-null
   * the buffer will be cleared and use.  When calling this method in a loop, the same StringBuffer can be used for each
   * call to limit object creation and memory use.
   * @param skipKeyword If true, keyword matching is not considered
   * @return true if the given Airing matches this Agent (given the parameter criteria) , false otherwise.
   */
  /*
   * TODO(codefu): skipKeyword is a hack before showcase. It works since the other flags are AND
   * tested; but we can have Lucene do this for us
   */
  public boolean followsTrend(Airing air, boolean mustBeViewable, StringBuffer sbCache, boolean skipKeyword)
  {
    if (air == null) return false;
    Show s = air.getShow();
    if (s == null) return false;

    //A disabled agent doesn't match any airings
    if(testAgentFlag(DISABLED_FLAG))
        return false;
    // Do not be case sensitive when checking titles!! We got a bunch of complaints about this on our forums.
    // Don't let null titles match all the Favorites!
    if (title != null && (s.title == null || (s.title != null && title != s.title && !title.toString().equalsIgnoreCase(s.title.toString()))))
      return false;
    if ((agentMask & FIRSTRUN_MASK) == FIRSTRUN_MASK && !air.isFirstRun())
      return false;
    if ((agentMask & RERUN_MASK) == RERUN_MASK && air.isFirstRun())
      return false;
    if (person != null)
    {
      int i = 0;
      for (; i < s.people.length; i++)
        if ((person == s.people[i] || person.name.equalsIgnoreCase(s.people[i].name)) && (role == Show.ALL_ROLES || role == 0 || role == s.roles[i]))
          break;
      if (i == s.people.length) return false;
    }
    // For categories, we can match sub-main, main-sub, or whatever
    if (category != null)
    {
      if (s.categories.length == 0 || category != s.categories[0])
      {
        if (!category.toString().equals(s.getSubCategory()))
          return false;
      }
    }
    if (subCategory != null)
    {
      if (s.categories.length < 2 || subCategory != s.categories[1])
      {
        if (!subCategory.toString().equals(s.getCategory()))
          return false;
      }
    }
    if (chanName.length() > 0 && (air.getChannel() == null || !chanNameMatches(air.getChannel().name)))
      return false;
    if (network != null && (air.getChannel() == null || network != air.getChannel().network))
      return false;
    if (rated != null && rated != s.rated)
      return false;
    if (year != null && year != s.year)
      return false;
    if (pr != null && !pr.toString().equals(air.getParentalRating()))
      return false;
    if (slotType != 0 && timeslots != null && timeslots.length > 0)
    {
      boolean anyMatches = false;
      for (int i = 0; i < timeslots.length; i++)
      {
        if (BigBrother.alignsSlot(air, slotType, timeslots[i]))
        {
          anyMatches = true;
          break;
        }
      }
      if (!anyMatches)
        return false;
    }

    if ((agentMask & LOVE_MASK) == 0 && (agentMask & TITLE_MASK) == 0)
    {
      // Non-title tracks only match English language shows
      if (s.language != null && !"English".equalsIgnoreCase(s.language.name) && !"en".equalsIgnoreCase(s.language.name))
        return false;
    }

    // Don't track anything that's unviewable
    if (mustBeViewable && wiz.getFileForAiring(air) == null && !air.isViewable())
      return false;

    if (skipKeyword == false && keyword.length() > 0)
    {
      boolean titleOnly = keyword.startsWith("TITLE:");
      String currKeyword = titleOnly ? keyword.substring("TITLE:".length()).trim() : keyword;
      // The fields in Show that we can test against are:
      // Title, Episode, Description, Year, People, category, subcategory, bonuses, ers, language
      StringBuffer fullShowTest;
      if (sbCache == null)
        fullShowTest = new StringBuffer(s.getTitle());
      else
      {
        sbCache.setLength(0);
        sbCache.append(s.getTitle());
        fullShowTest = sbCache;
      }
      if (!titleOnly)
      {
        fullShowTest.append('|'); fullShowTest.append(s.getEpisodeName());
        fullShowTest.append('|'); fullShowTest.append(s.getDesc());
        fullShowTest.append('|'); fullShowTest.append(s.getYear());
        for (int i = 0; i < s.people.length; i++)
        {
          fullShowTest.append('|'); fullShowTest.append(s.people[i].name);
        }
        fullShowTest.append('|'); fullShowTest.append(s.getCategory());
        fullShowTest.append('|'); fullShowTest.append(s.getSubCategory());
        for (int i = 2; i < s.categories.length; i++)
        {
          fullShowTest.append('|');
          fullShowTest.append(s.categories[i].name);
        }
        fullShowTest.append('|'); s.appendBonusesString(fullShowTest);
        fullShowTest.append('|'); s.appendExpandedRatingsString(fullShowTest);
        fullShowTest.append('|'); fullShowTest.append(s.getLanguage());
        fullShowTest.append('|'); fullShowTest.append(air.getChannelName());
        fullShowTest.append('|'); air.appendMiscInfo(fullShowTest);
        fullShowTest.append('|'); fullShowTest.append(s.getExternalID());
      }
      synchronized (this)
      {
        if (!currKeyword.equals(cachedKeywordForMats))
        {
          cachedKeywordForMats = currKeyword;

          // We break this up into groups by quotes, and then by spaces
          List<String> subPats = new ArrayList<String>();
          StringBuilder currPat = new StringBuilder();
          boolean inQuote = false;
          for (int i = 0; i < cachedKeywordForMats.length(); i++)
          {
            char c = cachedKeywordForMats.charAt(i);
            if (c == '"')
            {
              if (inQuote)
              {
                inQuote = false;
                if (currPat.length() > 0)
                {
                  subPats.add(currPat.toString());
                  currPat = new StringBuilder();
                }
              }
              else
              {
                if (currPat.length() > 0)
                {
                  subPats.add(currPat.toString());
                  currPat = new StringBuilder();
                }
                inQuote = true;
              }
            }
            else if (c == ' ')
            {
              if (inQuote)
              {
                currPat.append(c);
              }
              else
              {
                if (currPat.length() > 0)
                {
                  subPats.add(currPat.toString());
                  currPat = new StringBuilder();
                }
              }
            }
            else
            {
              currPat.append(c);
            }
          }
          if (currPat.length() > 0)
            subPats.add(currPat.toString());
          //				if (Sage.DBG) System.out.println("Parsed Keyword from [" + lcKeyword + "] into " + subPats);
          keywordMatchers = new Matcher[subPats.size()];
          for (int i = 0; i < keywordMatchers.length; i++)
          {
            String currPatStr = subPats.get(i);
            // Use whole word searches
            char c0 = currPatStr.charAt(0);
            if (Character.isLetterOrDigit(c0) || c0 == '*' || c0 == '?')
              currPatStr = "\\b" + currPatStr;
            c0 = currPatStr.charAt(currPatStr.length() - 1);
            if (Character.isLetterOrDigit(c0) || c0 == '*' || c0 == '?')
              currPatStr = currPatStr + "\\b";
            currPatStr = currPatStr.replaceAll("\\*", ".*").replaceAll("\\?", "[^| ]");
            //					if (Sage.DBG) System.out.println("Regex string #" + i + "=" + currPatStr);
            try
            {
              keywordMatchers[i] = Pattern.compile(currPatStr, Pattern.CASE_INSENSITIVE).matcher(fullShowTest);
            }
            catch (Exception ex)
            {
              System.out.println("ERROR with regex expression " + currKeyword + " in Favorite of: " + ex);
            }
          }
          for (int i = 0; i < keywordMatchers.length; i++)
          {
            if (keywordMatchers[i] == null || !keywordMatchers[i].find())
              return false;
          }
        }
        else
        {
          for (int i = 0; i < keywordMatchers.length; i++)
          {
            if (keywordMatchers[i] == null)
              return false;
            keywordMatchers[i].reset(fullShowTest);
            if (!keywordMatchers[i].find())
              return false;
          }
        }
      }
      //			if (Sage.DBG) System.out.println("Keyword found a match with:" + air + " text=" + fullShowTest);
    }

    return true;
  }

  // Returns false if it's an agent that shouldn't exist
  boolean calcWatchProb(boolean controlCPUUsage, Airing[] airWorkCache, StringBuffer sbCache)
  {
    if ((agentMask & LOVE_MASK) == LOVE_MASK)
    {
      watchProb = 1;
      negator = false;
      return true;
    }
    // There's three different things that affect this calculation.
    // 1. The Airings that are declared Watched that follow this trend
    // 2. The Airings that are Wasted that follow this trend

    /*
     * We do this with as few memory allocations as possible in order for it to not impact GC that much.
     * We first find all of the matching Watched Airings and put them in the storage array, tracking how many
     * are in there. Then we sort that by Airing ID.  Then we go through the Wasted by Airing ID and add to the
     * end of the array when we don't have overlap. We use a 'mergesort' type of scan to prevent overlap between
     * watched and wasted airings. We'll also need to track how many of them were Watched and how many were Wasted.
     */
    int numWatchedAirs = 0;
    int numWastedAirs = 0;
    DBObject[] watches = wiz.getRawAccess(Wizard.WATCH_CODE, (byte) 0);
    for (int i = 0; i < watches.length; i++)
    {
      Watched currWatch = (Watched) watches[i];
      if (watches[i] != null && BigBrother.isFullWatch(currWatch))
      {
        Airing testA = currWatch.getAiring();
        if (followsTrend(testA, false, sbCache))
        {
          airWorkCache[numWatchedAirs++] = testA;
        }
      }
      if (controlCPUUsage && (i % CPU_CONTROL_MOD_COUNT) == 0)
        try{Thread.sleep(Carny.SLEEP_PERIOD);}catch(Exception e){}
    }
    Arrays.sort(airWorkCache, 0, numWatchedAirs, DBObject.ID_COMPARATOR);
    DBObject[] waste = wiz.getRawAccess(Wizard.WASTED_CODE, (byte) 0);
    int numManualWaste = 0;
    int watchAirCompareIdx = 0;
    for (int i = 0; i < waste.length; i++)
    {
      Wasted currWaste = (Wasted) waste[i];
      if (waste[i] != null)
      {
        Airing testW = currWaste.getAiring();
        if (followsTrend(testW, false, sbCache))
        {
          if (currWaste.manual)
            numManualWaste++;
          // Check for overlap with the Watches before we add it
          int idDiff = 1;
          while (watchAirCompareIdx < numWatchedAirs)
          {
            idDiff = airWorkCache[watchAirCompareIdx].getID() - testW.getID();
            if (idDiff < 0)
            {
              watchAirCompareIdx++;
              idDiff = 1;
            }
            else
              break;
          }
          if (idDiff > 0)
            airWorkCache[numWatchedAirs + numWastedAirs++] = testW;
        }
      }
      if (controlCPUUsage && (i % CPU_CONTROL_MOD_COUNT) == 0)
        try{Thread.sleep(Carny.SLEEP_PERIOD);}catch(Exception e){}
    }

    float watchCount = 0;
    int totalCount = 0;

    // Make all non-title based agents title unique for tracking
    boolean titleUniqueCount = (agentMask & TITLE_MASK) == 0;
    Map<Show, List<Airing>> doneShowMap = new HashMap<Show, List<Airing>>();
    Map<Stringer, Float> titleWatchMap = null;
    Map<Stringer, Integer> titleAllMap = null;
    if (titleUniqueCount)
    {
      titleWatchMap = new HashMap<Stringer, Float>();
      titleAllMap = new HashMap<Stringer, Integer>();
    }
    for (int i = 0; i < numWatchedAirs + numWastedAirs; i++)
    {
      Airing currAir = airWorkCache[i];
      Show currShow = currAir.getShow();
      if (currShow == null) continue;
      Stringer theTit = null;
      if (titleUniqueCount)
        theTit = currShow.title;
      List<Airing> airSet = doneShowMap.get(currShow);
      if (airSet != null)
      {
        boolean didIt = false;
        for (int j = 0; j < airSet.size() && !didIt; j++)
        {
          if (BigBrother.areSameShow(airSet.get(j), currAir, true))
            didIt = true;
        }
        if (didIt)
          continue;
      }
      else
      {
        airSet = new ArrayList<Airing>();
        doneShowMap.put(currShow, airSet);
      }
      airSet.add(currAir);

      if (i < numWatchedAirs)
      {
        if (titleUniqueCount)
        {
          Float theFloat = titleWatchMap.get(theTit);
          titleWatchMap.put(theTit, 1 + ((theFloat == null) ? 0 : theFloat));

          Integer theInt = titleAllMap.get(theTit);
          titleAllMap.put(theTit, 1 + ((theInt == null) ? 0 : theInt));
        }
        else
        {
          watchCount++;
          totalCount++;
        }
        continue;
      }
      else
      {
        if (titleUniqueCount)
        {
          Integer theInt = titleAllMap.get(theTit);
          titleAllMap.put(theTit, 1 + ((theInt == null) ? 0 : theInt));
        }
        else
        {
          totalCount++;
        }
      }
    }
    int realTotalCount;
    if (titleUniqueCount)
    {
      for (Map.Entry<Stringer, Integer> ent : titleAllMap.entrySet())
      {
        int total = ent.getValue();
        Float currW = titleWatchMap.get(ent.getKey());
        if (currW != null)
          watchCount += currW / total;
        totalCount++;
      }
      realTotalCount = totalCount;
      totalCount = Math.max(totalCount, MIN_TOTAL);
    }
    else // This makes title agents need 3 watches to become 99%
    {
      realTotalCount = totalCount;
      totalCount = Math.max(totalCount, 3);
    }

    watchProb = watchCount/totalCount;
    watchProb = Math.min(MAX_WATCH_PROB, Math.max(MIN_WATCH_PROB, watchProb));
    if ((agentMask & TITLE_MASK) == 0)
      watchProb /= 2;

    negator = ((agentMask & TITLE_MASK) != 0 || Sage.getBoolean("aggressive_negative_profiling", false)) &&
        ((numWatchedAirs == 0 && numWastedAirs > 1) ||
            (numManualWaste > numWatchedAirs));

    // We need two data points to exist if we're actor based
    if (realTotalCount == 0) return false;
    if ((agentMask & TITLE_MASK) != 0) return true;
    if (watchCount == 0) return false;
    if ((agentMask & ACTOR_MASK) != ACTOR_MASK) return true;
    return (watchCount >= 2);
  }

  public String getTypeName()
  {
    if (keyword != null && keyword.length() > 0)
    {
      if (keyword.startsWith("TITLE:"))
        return Sage.rez("Title Keyword");
      else
        return Sage.rez("Keyword");
    }
    if (person != null)
    {
      if (title != null)
        return Sage.rez("Team");
      else
        return Sage.rez("Person");
    }
    if (title != null)
      return Sage.rez("Show");
    return Sage.rez("Misc");
  }

  public String getCause()
  {
    return getCause(false);
  }

  public String getCause(boolean clean)
  {
    StringBuilder sb = new StringBuilder();
    if (title != null)
    {
      sb.append(title.name);
      sb.append(' ');
    }
    if (category != null)
    {
      sb.append(category.name);
      if (subCategory != null)
      {
        sb.append('/');
        sb.append(subCategory.name);
      }
      sb.append(' ');
    }
    if (person != null)
    {
      if (role > 0 && role < Show.ROLE_NAMES.length)
      {
        sb.append(Show.ROLE_NAMES[role]);
        sb.append(' ');
      }
      sb.append(person.name);
      sb.append(' ');
    }
    if (year != null)
    {
      sb.append(year.name);
      sb.append(' ');
    }
    if (pr != null)
    {
      sb.append(pr.name);
      sb.append(' ');
    }
    if (rated != null)
    {
      sb.append(rated.name);
      sb.append(' ');
    }
    if (network != null)
    {
      sb.append(network.name);
      sb.append(' ');
    }
    if (keyword.length() > 0)
    {
      if (clean)
      {
        if (keyword.startsWith("TITLE:"))
          sb.append(keyword.substring(6));
        else
          sb.append(keyword);
      }
      else
      {
        sb.append('[');
        sb.append(keyword);
        sb.append("] ");
      }
      sb.append(' ');
    }
    if ((agentMask & FIRSTRUN_MASK) == FIRSTRUN_MASK)
    {
      sb.append(Sage.rez("First_Runs"));
      sb.append(' ');
    }
    if ((agentMask & RERUN_MASK) == RERUN_MASK)
    {
      sb.append(Sage.rez("ReRuns"));
      sb.append(' ');
    }
    if (slotType != 0 && timeslots != null && timeslots.length > 0)
    {
      if (slotType == BigBrother.FULL_ALIGN)
      {
        // Coalesce the dates & times
        ArrayList<String> used = Pooler.getPooledArrayList();
        for (int i = 0; i < timeslots.length; i++)
        {
          String str = BigBrother.getTimeslotString(BigBrother.DAY_ALIGN, timeslots[i]);
          if (!used.contains(str))
          {
            used.add(str);
            sb.append(str);
            sb.append(' ');
          }
        }
        used.clear();
        for (int i = 0; i < timeslots.length; i++)
        {
          String str = BigBrother.getTimeslotString(BigBrother.TIME_ALIGN, timeslots[i]);
          if (!used.contains(str))
          {
            used.add(str);
            sb.append(str);
            sb.append(' ');
          }
        }
        Pooler.returnPooledArrayList(used);
      }
      else
      {
        for (int i = 0; i < timeslots.length; i++)
        {
          sb.append(BigBrother.getTimeslotString(slotType, timeslots[i]));
          sb.append(' ');
        }
      }
    }
    if (chanName.length() > 0)
    {
      sb.append(chanName);
    }
    return sb.toString().trim();
  }

  void clearCache() { relAirCache = null; }

  public boolean isNegativeNelly()
  {
    return negator;
  }

  public String getRecordingQuality()
  {
    return quality;
  }
  public void setRecordingQuality(String quality)
  {
    if (this.quality == quality || this.quality.equals(quality)) return;
    try {
      wiz.acquireWriteLock(Wizard.AGENT_CODE);
      this.quality = quality;
      wiz.logUpdate(this, Wizard.AGENT_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.AGENT_CODE);
    }
  }
  public String getAutoConvertFormat()
  {
    return autoConvertFormat;
  }
  public void setAutoConvertFormat(String format)
  {
    if (format == null) format = "";
    if (format.equals(autoConvertFormat)) return;
    try {
      wiz.acquireWriteLock(Wizard.AGENT_CODE);
      autoConvertFormat = format;
      wiz.logUpdate(this, Wizard.AGENT_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.AGENT_CODE);
    }
  }
  public File getAutoConvertDest()
  {
    return autoConvertDest;
  }
  public void setAutoConvertDest(File f)
  {
    if (f == autoConvertDest || (f != null && f.equals(autoConvertDest))) return;
    try {
      wiz.acquireWriteLock(Wizard.AGENT_CODE);
      autoConvertDest = f;
      wiz.logUpdate(this, Wizard.AGENT_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.AGENT_CODE);
    }
  }

  public long getStartPadding()
  {
    return startPad;
  }
  public void setStartPadding(long newPad)
  {
    if (startPad != newPad)
    {
      try {
        wiz.acquireWriteLock(Wizard.AGENT_CODE);
        startPad = newPad;
        wiz.logUpdate(this, Wizard.AGENT_CODE);
      } finally {
        wiz.releaseWriteLock(Wizard.AGENT_CODE);
      }
    }
  }

  public long getStopPadding()
  {
    return stopPad;
  }
  public void setStopPadding(long newPad)
  {
    if (stopPad != newPad)
    {
      try {
        wiz.acquireWriteLock(Wizard.AGENT_CODE);
        stopPad = newPad;
        wiz.logUpdate(this, Wizard.AGENT_CODE);
      } finally {
        wiz.releaseWriteLock(Wizard.AGENT_CODE);
      }
    }
  }

  public int getUID()
  {
    return id;
  }

  public int getAgentMask() { return agentMask; }
  public boolean isFavorite() { return (agentMask & LOVE_MASK) == LOVE_MASK; }
  public boolean testAgentFlag(int maskTest)
  {
    return (agentFlags & maskTest) == maskTest;
  }
  public int getAgentFlag(int whichFlag)
  {
    if (whichFlag == DONT_AUTODELETE_FLAG)
      return agentFlags & DONT_AUTODELETE_FLAG;
    else if (whichFlag == KEEP_AT_MOST_MASK)
      return (agentFlags & KEEP_AT_MOST_MASK) >> 1;
    else if (whichFlag == DELETE_AFTER_CONVERT_FLAG)
      return agentFlags & DELETE_AFTER_CONVERT_FLAG;
    else if (whichFlag == DISABLED_FLAG)
      return agentFlags & DISABLED_FLAG;
    else
      return 0;
  }
  void setAgentFlags(int maskBits, int values)
  {
    if (maskBits == KEEP_AT_MOST_MASK)
      values = values << 1;
    if ((agentFlags & maskBits) == values) return;
    try {
      wiz.acquireWriteLock(Wizard.AGENT_CODE);
      agentFlags = (agentFlags & (~maskBits)) | (maskBits & values);
      wiz.logUpdate(this, Wizard.AGENT_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.AGENT_CODE);
    }
  }

  private boolean chanNameMatches(String name)
  {
    if (chanNames == null) return false;
    for (int i = 0; i < chanNames.length; i++)
      if (chanNames[i].equals(name))
        return true;
    return false;
  }

  public String getProperty(String name)
  {
    if (favProps == null)
      return "";
    String rv = favProps.getProperty(name);
    return (rv == null) ? "" : rv;
  }

  public void setProperty(String name, String value)
  {
    if (value == null && (favProps == null || !favProps.containsKey(name)))
      return;
    if (value != null && favProps != null && value.equals(favProps.getProperty(name)))
      return;
    try {
      wiz.acquireWriteLock(Wizard.AGENT_CODE);
      if (value == null)
      {
        favProps.remove(name);
      }
      else
      {
        if (favProps == null)
          favProps = new Properties();
        favProps.setProperty(name, value);
      }
      wiz.logUpdate(this, Wizard.AGENT_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.AGENT_CODE);
    }
  }

  public Properties getProperties()
  {
    if (favProps == null)
      return new Properties();
    return (Properties) favProps.clone();
  }

  final int agentID;
  int agentMask;
  transient float watchProb;
  Stringer title;
  Stringer category;
  Stringer subCategory;
  Person person;
  int role;
  Stringer rated;
  Stringer year;
  Stringer pr;
  String chanName = "";
  String[] chanNames;
  Stringer network;
  int[] timeslots;
  int slotType;
  long createTime;
  int[] weakAgents;
  String quality = "";
  String autoConvertFormat = "";
  File autoConvertDest;
  long startPad;
  long stopPad;
  int agentFlags;
  String keyword = "";
  Properties favProps;
  private Matcher[] keywordMatchers;
  private String cachedKeywordForMats;
  private transient Wizard wiz;
  private transient Airing[] relAirCache;
  private transient long cacheTimestamp;
  private transient Airing[] relAirCache2;
  private transient long cacheTimestamp2;
  private transient boolean negator = false;
}
