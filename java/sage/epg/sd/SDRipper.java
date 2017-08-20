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
package sage.epg.sd;

import sage.Airing;
import sage.CaptureDeviceInput;
import sage.Channel;
import sage.DBObject;
import sage.EPG;
import sage.EPGDataSource;
import sage.MMC;
import sage.Person;
import sage.Pooler;
import sage.Sage;
import sage.SageTV;
import sage.Show;
import sage.WarlockRipper;
import sage.Wizard;
import sage.epg.sd.json.headend.SDHeadend;
import sage.epg.sd.json.headend.SDHeadendLineup;
import sage.epg.sd.json.images.SDImage;
import sage.epg.sd.json.images.SDProgramImages;
import sage.epg.sd.json.lineup.SDAccountLineup;
import sage.epg.sd.json.lineup.SDAccountLineups;
import sage.epg.sd.json.locale.SDCountry;
import sage.epg.sd.json.locale.SDLanguage;
import sage.epg.sd.json.locale.SDRegion;
import sage.epg.sd.json.map.SDChannelMap;
import sage.epg.sd.json.map.SDLineupMap;
import sage.epg.sd.json.map.channel.SDAntennaChannelMap;
import sage.epg.sd.json.map.station.SDLogo;
import sage.epg.sd.json.map.station.SDStation;
import sage.epg.sd.json.programs.SDInProgressSport;
import sage.epg.sd.json.programs.SDKeyWords;
import sage.epg.sd.json.programs.SDMovie;
import sage.epg.sd.json.programs.SDPerson;
import sage.epg.sd.json.programs.SDProgram;
import sage.epg.sd.json.programs.SDSeriesDesc;
import sage.epg.sd.json.schedules.SDProgramSchedule;
import sage.epg.sd.json.schedules.SDScheduleMd5;
import sage.epg.sd.json.schedules.SDStationSchedule;
import sage.epg.sd.json.status.SDStatus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SDRipper extends EPGDataSource
{
  public static final String SOURCE_NAME = "SDRipper";

  private static final String PROP_PREFIX = "sdepg_core";
  private static final String AUTH_FILE = "sdauth";
  private static final String PROP_REGION = PROP_PREFIX + "/locale/region";
  private static final String PROP_COUNTRY = PROP_PREFIX + "/locale/country";
  private static final String PROP_POSTAL_CODE = PROP_PREFIX + "/locale/postal_code";
  private static final String PROP_DISABLE_REGEX = PROP_PREFIX + "/disable_regex";
  private static final String PROP_REMOVE_LEADING_ZEROS = PROP_PREFIX + "/remove_leading_zeros";
  //private static final String PROP_RATING_BODY = PROP_PREFIX + "/rating_body";
  private static final String PROP_MOVIE_RATING_BODY = PROP_PREFIX + "/movie_rating_body";
  private static final String PROP_DIGRAPH = PROP_PREFIX + "/preferred_desc_digraph";
  private static final String PROP_SAGETV_COMPAT = PROP_PREFIX + "/sagetv_compat";
  private static final String PROP_EDITORIAL_NEXT_RUN = PROP_PREFIX + "/editorial/next_run";
  private static final String PROP_EDITORIAL_UPDATE_INTERVAL = PROP_PREFIX + "/editorial/update_interval";
  private static final String PROP_EDITORIAL_IMPORT_LIMIT = PROP_PREFIX + "/editorial/import_limit";
  private static final String PROP_DOWNLOAD_ALTERNATE_LOGOS = PROP_PREFIX + "/use_alternate_logos";
  private static final String FILE_PROGRAM_MD5 = "sdmd5prog";
  private static final String FILE_SCHEDULE_MD5 = "sdmd5sched";

  public static final String SOURCE_LABEL = " (sdepg)";
  private static final String SOURCE_LINEUP_ID = "epg_sd_name";
  private static final String ADD_LINEUP_BY_ID = "Add lineup by ID...";

  // This is the preferred rating body. You would only be interested in changing this if you live in
  // another country and want to see a more familiar rating system.
  // JS 9/28/2016: This information is not used in regards to anything other than movies.
  //private static final String ratingBody = Sage.get(PROP_RATING_BODY, "USA Parental Rating");
  // This is the preferred rating body for movies. You would only be interested in changing this if
  // you live in another country and want to see a more familiar rating system.
  private static final String movieRatingBody = Sage.get(PROP_MOVIE_RATING_BODY, "Motion Picture Association of America");
  // This is the two letter preferred language for descriptions. If this language is not available,
  // the first available description is used.
  private static final String preferedDescDigraph = Sage.get(PROP_DIGRAPH, "en");
  // Remove pre-pended zeros from virtual channels on import.
  private static final boolean removeLeadingZeros = Sage.getBoolean(PROP_REMOVE_LEADING_ZEROS, true);
  // Disables regex checking for postal code in case it causes problems for someone.
  private static final boolean disableRegex = Sage.getBoolean(PROP_DISABLE_REGEX, false);
  // Enables conversion of 14 character program IDs into 12 character IDs when the indexes 2 and 3
  // are zero.
  private static final boolean enableSageTVCompat = Sage.getBoolean(PROP_SAGETV_COMPAT, true);
  // This is the amount of time in milliseconds that must have elapsed for the editorials to be
  // updated again. This will help keep the editorials from being updated constantly for those with
  // many lineups.
  private static final int editorialUpdateInterval = Sage.getInt(PROP_EDITORIAL_UPDATE_INTERVAL, 86400000); // 24h
  // This is the most editorials that will ever be imported within one interval. If this value is
  // set to 0, the import process is disabled.
  private static final int editorialImportLimit = Sage.getInt(PROP_EDITORIAL_IMPORT_LIMIT, 50);
  // This is used to synchronize on when we are modifying channels to ensure the are updated in sets
  // and not half updated by one thread and half updated by another.
  private static final Object updateChannelsLock = new Object();

  private static long postalCodeCacheTime;
  private static SDRegion regions[] = new SDRegion[0];
  private static SDCountry countryCache = null;
  private final static Map<String, SDHeadendLineup> lineupCache = new ConcurrentHashMap<String, SDHeadendLineup>();
  private final static Map<String, String> countryDigraphToName = new ConcurrentHashMap<>();

  private final static ReadWriteLock sessionLock = new ReentrantReadWriteLock();
  // This session is used for all lineups.
  private static SDSession session;
  // This is set to a future time when we want to purposefully fail the updates until the specified
  // time due to authentication problems or the Schedules Direct server is offline.
  private static volatile long retryWait = 0;
  // The name of the lineup on Schedules Direct.
  private String lineupID;

  public SDRipper(int inEPGSourceID)
  {
    super(inEPGSourceID);

    Sage.put(prefsRoot + EPG.EPG_CLASS, SOURCE_NAME);
    lineupID = Sage.get(prefsRoot + SOURCE_LINEUP_ID, "");
  }

  // Do not call this from inside of a sessionLock
  private static void refreshCountryRegionCache() throws IOException, SDException
  {
    if (postalCodeCacheTime < Sage.time())
    {
        regions = ensureSession().getRegions();
        countryCache = null;
        postalCodeCacheTime = Sage.time() + Sage.MILLIS_PER_DAY;
    }
  }

  public static SDSession ensureSession() throws SDException, IOException
  {
    SDSession localSession;

    sessionLock.writeLock().lock();

    try
    {
      if (session == null)
      {
        localSession = openNewSession();
        session = localSession;
      }
      else
      {
        localSession = session;
      }
    }
    finally
    {
      sessionLock.writeLock().unlock();
    }

    return localSession;
  }

  public static void reopenSession() throws SDException, IOException
  {
    sessionLock.writeLock().lock();

    try
    {
      session = openNewSession();
    }
    finally
    {
      sessionLock.writeLock().unlock();
    }
  }

  // Do not use this method without getting a sessionLock write lock first.
  private static SDSession openNewSession() throws IOException, SDException
  {
    SDSession returnValue;
    BufferedReader reader = null;

    File authFile = new File(AUTH_FILE);
    if (!authFile.exists() || authFile.length() == 0)
      throw new SDException(SDErrors.SAGETV_NO_PASSWORD);

    try
    {
      reader = new BufferedReader(new FileReader(authFile));
      String auth = reader.readLine();
      if (auth == null)
      {
        if (Sage.DBG) System.out.println("SDEPG Error: sdauth file is empty.");
        throw new SDException(SDErrors.SAGETV_NO_PASSWORD);
      }
      int split = auth.indexOf(' ');
      // If the file is not formatted correctly, it's as good as not existing.
      if (split == -1)
      {
        if (Sage.DBG) System.out.println("SDEPG Error: sdauth file is missing a space between the username and password.");
        throw new SDException(SDErrors.SAGETV_NO_PASSWORD);
      }

      String username = auth.substring(0, split);
      String password = auth.substring(split + 1);

      // This will throw an exception if there are any issues connecting.
      returnValue = new SDSageSession(username, password);
    }
    catch (FileNotFoundException e)
    {
      throw new SDException(SDErrors.SAGETV_NO_PASSWORD);
    }
    finally
    {
      if (reader != null)
      {
        try
        {
          reader.close();
        } catch (Exception e) {}
      }
    }

    // We have just successfully authenticated, so this needs to be cleared so that updates can
    // start immediately.
    SDRipper.retryWait = 0;
    if (Sage.DBG) System.out.println("SDEPG Successfully got token: " + returnValue.token);
    return returnValue;
  }

  /**
   * Get a property specific to this EPG source instance.
   * <p/>
   * This does not strictly need to be a saved property. This can also be used to return
   * specialized status information, etc.
   *
   * @param property The name of the property to get.
   * @param parameter An optional parameter related to the property.
   * @return The value for the given property.
   */
  public static Object getProperty(String property, String parameter)
  {
    if (Sage.DBG) System.out.println("SDEPG Getting the property '" + property + "' with the parameter '" + parameter + "'");

    int splitIndex;
    String lookupCountry;
    String lookupPostalCode;

    try
    {
      switch (property)
      {
        case "Authenticate":
          // Verify that the saved credentials will get a valid token.
          reopenSession();
          return "OK";
        case "Saved":
          // Check if we have successfully completed the region, country, postal code wizard once.
          return Sage.get(PROP_REGION, "").length() != 0 &&
              Sage.get(PROP_COUNTRY, "").length() != 0 &&
              Sage.get(PROP_POSTAL_CODE, "").length() != 0;
        case "CurrentRegion":
          // Get the last saved region or the first one on the list if a saved region doesn't exist.
          return getCurrentRegion();
        case "Regions":
          // Get the list of available regions.
          return getRegions();
        case "CurrentCountry":
          // Get the last saved country or the first one on the list if a saved country doesn't exist.
          return getCurrentCountry(parameter);
        case "Countries":
          // Get all countries for a provided region.
          return getCountries(parameter);
        case "CurrentPostalCode":
          // Get the last saved postal code or an empty string if a saved postal code doesn't exist.
          return getCurrentPostalCode();
        case "PostalCodeExample":
          // Get an example postal code for a provided region.
          return getCountryExample(parameter);
        case "PostalCodeSkip":
          // Determine if a country only has one postal code so we don't need to ask for a postal code.
          return getSkipPostalCode(parameter);
        case "PostalCodeValidate":
          // Verify that a provided postal code is valid for a provided country.
          // Ex. United States|12345
          splitIndex = parameter.indexOf('|');
          if (splitIndex == -1)
            break;
          lookupCountry = parameter.substring(0, splitIndex);
          lookupPostalCode = parameter.substring(splitIndex + 1);

          getValidateRegexForPostalCode(lookupCountry, lookupPostalCode);
          return "OK";
        case "Lineups":
          // Get all available lineups for a provided country and postal code.
          // Ex. United States|12345
          splitIndex = parameter.indexOf('|');
          if (splitIndex == -1)
            break;
          lookupCountry = parameter.substring(0, splitIndex);
          lookupPostalCode = parameter.substring(splitIndex + 1);

          return getLineupsForPostalCode(lookupCountry, lookupPostalCode);
        case "AccountLineups":
          // Get all lineups currently added to the account.
          return getLineupsForAccount();
        case "UpdateChannelLogos":
          // Update all of the channel logos with the latest URL's. This is called to quickly
          // alternate between the primary and alternative channel logos.
          getUpdateChannelLogos();
          return "OK";
      }
    }
    catch (NumberFormatException e)
    {
      return null;
    }
    catch (SDException e)
    {
      e.printStackTrace(System.out);
      return e.getMessage();
    }
    catch (IOException e)
    {
      e.printStackTrace(System.out);
      return EPG.EPG_SERVER_CONNECTION_FAILURE;
    }

    // If all of the properties are managed correctly in the UI, we should never reach this, but we
    // will call it a connection failure and log the real cause because we can't reliably continue
    // with an error like this.
    System.out.println("SDEPG Error: The property doesn't exist for Schedules Direct.");
    return EPG.EPG_SERVER_CONNECTION_FAILURE;
  }

  /**
   * Set a property specific to this EPG source instance.
   *
   * @param property The name of the property to set.
   * @param value The value to set the property to.
   * @return The result of setting the property.
   */
  public static Object setProperty(String property, String value)
  {
    if (Sage.DBG) System.out.println("SDEPG Setting the property '" + property + "' with the value '" + value + "'");

    try
    {
      switch (property)
      {
        case "AddLineup":
          return addLineupToAccount(value);
        case "AddLineupByID":
          return addLineupToAccountByID(value);
        case "DeleteLineup":
          return deleteLineupFromAccount(value);
        case "CurrentRegion":
          setCurrentRegion(value);
          return "OK";
        case "CurrentCountry":
          setCurrentCountry(value);
          return "OK";
        case "CurrentPostalCode":
          setCurrentPostalCode(value);
          return "OK";
      }
    }
    catch (SDException e)
    {
      e.printStackTrace(System.out);
      return e.getMessage();
    }
    catch (IOException e)
    {
      e.printStackTrace(System.out);
      return EPG.EPG_SERVER_CONNECTION_FAILURE;
    }

    // If all of the properties are used correctly in the API, we should never reach this, but we
    // will call it a connection failure and log the real cause because we can't reliably continue
    // with an error like this.
    System.out.println("SDEPG Error: The property isn't valid for Schedules Direct.");
    return EPG.EPG_SERVER_CONNECTION_FAILURE;
  }

  private static String getCurrentRegion() throws IOException, SDException
  {
    String region = Sage.get(SDRipper.PROP_REGION, null);

    if (region != null)
      return region;

    return getRegions()[0];
  }

  private static String getCurrentCountry(String region) throws IOException, SDException
  {
    String country = Sage.get(SDRipper.PROP_COUNTRY, null);

    if (country != null)
    {
      // Only return a non-default country if the last saved country isn't in the requested region.
      if (isCountryInRegion(country, region))
        return country;
    }

    if (Sage.DBG) System.out.println("SDEPG The region " + region + " doesn't match the last country " + country);

    return getCountries(region != null ? region : getCurrentRegion())[0];
  }

  private static String getCurrentPostalCode()
  {
    return Sage.get(SDRipper.PROP_POSTAL_CODE, "");
  }

  /**
   * Set the current region for future lookups.
   *
   * @param region The region.
   */
  public static void setCurrentRegion(String region)
  {
    Sage.put(PROP_REGION, region);
  }

  public static void setCurrentCountry(String country)
  {
    Sage.put(PROP_COUNTRY, country);
  }

  public static void setCurrentPostalCode(String postalCode)
  {
    Sage.put(PROP_POSTAL_CODE, postalCode);
  }

  public String getLineupID()
  {
    return lineupID;
  }

  public void setLineupID(String s)
  {
    if (s == null) s = "";
    lineupID = s;
    Sage.put(prefsRoot + SOURCE_LINEUP_ID, s);
  }

  public static String[] getRegions() throws IOException, SDException
  {
    List<String> returnValue;

    refreshCountryRegionCache();

    sessionLock.readLock().lock();

    try
    {
      returnValue = new ArrayList<>(regions.length);
      int i = 0;
      for (SDRegion region : regions)
      {
        // ZZZ correlates with a legacy feature of Schedules Direct for satellite feeds. It doesn't
        // currently return anything and it doesn't appear that it will ever be used for anything.
        // We can return this to the list if that changes in the future, but for now it's better to
        // not have it.
        if (!region.getRegion().equals("ZZZ"))
          returnValue.add(region.getRegion());
      }
      // Add the option to add by ID if you already know what the ID is.
      returnValue.add(ADD_LINEUP_BY_ID);
    }
    finally
    {
      sessionLock.readLock().unlock();
    }

    if (Sage.DBG) System.out.println("SDEPG Returning regions: " + returnValue);

    return returnValue.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public static String[] getCountries(String region) throws IOException, SDException
  {
    refreshCountryRegionCache();

    sessionLock.readLock().lock();

    try
    {
      for (SDRegion lookupRegion : regions)
      {
        if (lookupRegion.getRegion().equals(region))
        {
          String returnValue[] = new String[lookupRegion.getCountries().length];

          int i = 0;
          for (SDCountry country : lookupRegion.getCountries())
          {
            returnValue[i++] = country.getFullName();
          }

          if (Sage.DBG) System.out.println("SDEPG Returning countries: " + Arrays.toString(returnValue));
          return returnValue;
        }
      }
    }
    finally
    {
      sessionLock.readLock().unlock();
    }

    if (Sage.DBG) System.out.println("SDEPG Returning no countries.");
    return Pooler.EMPTY_STRING_ARRAY;
  }

  public static SDCountry getCountry(String country) throws IOException, SDException
  {
    // Generally we will always be working with the same country, so this keeps us from needing to
    // look it up from the array constantly.
    SDCountry tempCountry = countryCache;
    if (tempCountry != null && tempCountry.getFullName().equals(country))
      return tempCountry;

    refreshCountryRegionCache();

    sessionLock.readLock().lock();

    try
    {
      for (SDRegion lookupRegion : regions)
      {
        for (SDCountry lookupCountry : lookupRegion.getCountries())
        {
          if (lookupCountry.getFullName().equals(country))
          {
            countryCache = lookupCountry;
            return lookupCountry;
          }
        }
      }
    }
    finally
    {
      sessionLock.readLock().unlock();
    }

    return null;
  }

  public static String getCountryExample(String lookupCountry)
  {
    // Make sure we don't return an exception. This piece isn't important enough to create problems.
    try
    {
      SDCountry country = getCountry(lookupCountry);
      if (country != null)
      {
        if (Sage.DBG) System.out.println("SDEPG Returning example: " + country.getPostalCodeExample());
        return country.getPostalCodeExample();
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG)
      {
        System.out.println("SDEPG Unable to get an example for '" + lookupCountry + "'.");
        e.printStackTrace(System.out);
      }
    }

    // If everything is working correctly, by the time we get to this point in the UI wizards, this
    // should never get here especially since we cache everything starting with the region selection.
    return EPG.EPG_SERVER_CONNECTION_FAILURE;
  }

  /**
   * Determine if you can use the example postal code as the postal code.
   * <p/>
   * This checks if the regular expression provided to validate the postal code for a given country
   * will only match the example provided by Schedules Direct. This can be used for the UI to
   * determine if you can skip the postal code because it can only be one value.
   * <p/>
   * If there are any issues getting information about the country, <code>false</code> will be
   * returned.
   *
   * @param lookupCountry The country to check.
   * @return <code>true</code> if the example postal code is the only valid postal code.
   */
  public static boolean getSkipPostalCode(String lookupCountry)
  {
    // Make sure we don't return an exception. This piece isn't important enough to create problems.
    try
    {
      SDCountry country = getCountry(lookupCountry);
      if (country != null)
      {
        String match = country.getPostalCode();
        int firstIndex = match.indexOf("/");
        int lastIndex = match.lastIndexOf("/");
        // The regular expressions are enclosed in /'s, but just in case the format changes, we make
        // sure we aren't breaking the regex. +1 so we know we have at least one character between
        // the forward slashes.
        if (firstIndex == 0 && lastIndex > firstIndex + 1)
        {
          match = match.substring(1, lastIndex);
          if (Sage.DBG) System.out.println("SDEPG Converted regex from '" + country.getPostalCode() + "' to '" + match + "'");
        }

        // We can check the regex to see if it will only match one specific string and if it does,
        // we can auto-fill the postal code and skip the postal code screen entirely.
        return match.equals(country.getPostalCodeExample());
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG)
      {
        System.out.println("SDEPG Unable to determine if we don't need the postal code for '" + lookupCountry + "'.");
        e.printStackTrace(System.out);
      }
    }

    // If everything is working correctly, by the time we get to this point, this should never get
    // here especially since we cache everything starting with the region selection.
    return false;
  }

  /**
   * Get all lineups available in specific country and postal code formatted for display in UI.
   * <p/>
   * This will automatically remove any lineups that already exist in the current Schedules Direct
   * account.
   *
   * @param countryLookup The country to look up the postal code in.
   * @param postalCode The postal code to look up lineups for.
   * @return A <code>String</code> array of lineups formatted for display in UI.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public static String[] getLineupsForPostalCode(String countryLookup, String postalCode) throws IOException, SDException
  {
    SDCountry country = getCountry(countryLookup);
    if (country == null)
      throw new SDException(SDErrors.INVALID_COUNTRY);

    SDHeadend headends[] = ensureSession().getHeadends(country.getShortName(), postalCode);
    SDAccountLineups accountLineups;

    // This could throw an exception just because the account is empty and while it's nice to have
    // lineups already in the account removed, it's not required for this to be functional.
    try
    {
      accountLineups = ensureSession().getAccountLineups();
    }
    catch (Exception e)
    {
      // NO_LINEUPS is the only error we actually expect.
      if (Sage.DBG && !e.getMessage().equals(SDErrors.NO_LINEUPS.name()))
      {
        System.out.println("SDEPG Unable to get lineups from account for exclusion: " + e.getMessage());
        e.printStackTrace(System.out);
      }

      // An empty array will be returned from this object by default.
      accountLineups = new SDAccountLineups();
    }

    if (Sage.DBG)
    {
      System.out.println("SDEPG Received " + headends.length + " headends.");
      for (SDHeadend headend : headends)
      {
        System.out.println("SDEPG Headend: " + headend.toString());
      }
    }

    // Often this will be the total array size so the List will likely not need to grow its
    // internal array, but sometimes we have more than one lineup per headend, so we combine them
    // into the a List first.
    List<String> lineups = new ArrayList<>(headends.length);
    Set<String> processed = new HashSet<>();

    String lineupName;

    for (SDHeadend headend : headends)
    {
      for (SDHeadendLineup lineup : headend.getLineups())
      {
        lineupName = screenFormatHeadendLineup(headend, lineup);

        // If the name is a duplicate, append the lineup ID. The ID is always unique.
        if (!processed.add(lineupName))
        {
          lineupName = lineupName + " - " + lineup.getLineup();
          processed.add(lineupName);
        }

        // Remove any lineups we already have added to the account.
        for (SDAccountLineup accountLineup : accountLineups.getLineups())
        {
          // 06/09/2017 JS: We were using getURI() here, but when a lineup is deleted, the URI does
          // not exist and would throw a null pointer exception. getLineup() is always existential
          // according to the API and is equally unique, so we are using that instead.
          if (accountLineup.getLineup().equals(lineup.getLineup()))
          {
            lineupName = null;
            break;
          }
        }

        // This lineup is already added to the account, so we skip it.
        if (lineupName == null)
          continue;

        lineups.add(lineupName);
        // Cache every lineup we are going to return in the search results.
        lineupCache.put(lineupName, lineup);
      }
    }

    return lineups.toArray(new String[lineups.size()]);
  }

  /**
   * Check if a provided postal code is valid for a provided country via regex.
   *
   * @param countryLookup The country to check the postal code against.
   * @param postalCode The postal code to check against the country via regex.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct or the postal code did
   *                     not pass validation.
   */
  public static void getValidateRegexForPostalCode(String countryLookup, String postalCode) throws IOException, SDException
  {
    if (disableRegex)
      return;

    SDCountry country = getCountry(countryLookup);
    if (country == null)
      throw new SDException(SDErrors.INVALID_COUNTRY);

    String match = country.getPostalCode();
    int firstIndex = match.indexOf("/");
    int lastIndex = match.lastIndexOf("/");
    // The regular expressions are enclosed in /'s, but just in case the format changes, we make
    // sure we aren't breaking the regex. +1 so we know we have at least one character between
    // the forward slashes.
    if (firstIndex == 0 && lastIndex > firstIndex + 1)
    {
      match = match.substring(1, lastIndex);
      if (Sage.DBG) System.out.println("SDEPG Converted regex from '" + country.getPostalCode() + "' to '" + match + "'");
    }
    else
    {
      // Return true when we don't recognize the regular expression.
      if (Sage.DBG) System.out.println("SDEPG Unknown regex '" + country.getPostalCode() + "' returning true.");
      return;
    }
    Pattern pattern = Pattern.compile(match);
    Matcher matcher = pattern.matcher(postalCode);
    boolean matches = matcher.matches();

    if (Sage.DBG) System.out.println("SDEPG Returning regex matching '" + match + "' to '" + postalCode + "' is " + matches);

    if (!matches)
      throw new SDException(SDErrors.INVALID_PARAMETER_POSTALCODE);
  }

  /**
   * Get a formatted <code>String</code> array of the lineups currently added to the account.
   * <p/>
   * If a lineup has been deleted on Schedules Direct, (Deleted) will be appended for the UI. Use
   * <code>removeFormatDeleted(String)</code> to whenever you are doing any lookup based on values
   * returned from this method.
   *
   * @return Formatted <code>String</code> array of the lineups currently added to the account.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public static String[] getLineupsForAccount() throws IOException, SDException
  {
    Set<String> processed = new HashSet<>();
    SDAccountLineups getLineups = ensureSession().getAccountLineups();

    SDAccountLineup lineups[] = getLineups.getLineups();
    String returnValue[] = new String[lineups.length];

    for (int i = 0; i < lineups.length; i++)
    {
      returnValue[i] = screenFormatAccountLineup(lineups[i]);
      // If this name has already been used, we append the lineup ID because it is unique.
      if (!processed.add(returnValue[i]))
      {
        returnValue[i] = returnValue[i] + " - " + lineups[i].getLineup();
      }
    }

    if (Sage.DBG) System.out.println("SDEPG Returning account lineups: " + Arrays.toString(returnValue));

    return returnValue;
  }

  private static StringBuilder screenFormatBuilder;
  /**
   * Convert an account lineup to a <code>String</code> that can be displayed in the UI.
   *
   * @param lineup The account lineup.
   * @return The formatted account lineup <code>String</code>.
   */
  public synchronized static String screenFormatAccountLineup(SDAccountLineup lineup)
  {
    if (screenFormatBuilder == null)
      screenFormatBuilder = new StringBuilder();
    else
      screenFormatBuilder.setLength(0);

    screenFormatBuilder.append(lineup.getName());

    // When a lineup is deleted, location is null.
    if (lineup.getLocation() == null)
      screenFormatBuilder.append(" - ").append(lineup.getLineup());
    else if (!lineup.getName().equals(lineup.getLocation()))
      screenFormatBuilder.append(" - ").append(lineup.getLocation());

    return screenFormatBuilder.toString();
  }

  /**
   * Convert a headend lineup to a <code>String</code> that can be displayed in the UI.
   *
   * @param headend The headend of the lineup.
   * @param lineup The headend lineup.
   * @return The formatted headend lineup <code>String</code>.
   */
  public synchronized static String screenFormatHeadendLineup(SDHeadend headend, SDHeadendLineup lineup)
  {
    if (screenFormatBuilder == null)
      screenFormatBuilder = new StringBuilder();
    else
      screenFormatBuilder.setLength(0);

    screenFormatBuilder.append(lineup.getName());

    if (!lineup.getName().equals(headend.getLocation()))
      screenFormatBuilder.append(" - ").append(headend.getLocation());

    return screenFormatBuilder.toString();
  }

  /**
   * Get if a provided country is in the provided region.
   *
   * @param lookupCountry The country to check against the region.
   * @param lookupRegion The region to check the country against.
   * @return <code>true</code> if the provided country is in the provided region.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public static boolean isCountryInRegion(String lookupCountry, String lookupRegion) throws IOException, SDException
  {
    String regions[] = getCountries(lookupRegion);

    for (String region : regions)
    {
      if (region.equals(lookupCountry))
      {
        return true;
      }
    }

    return false;
  }

  /**
   * Delete a lineup from the Schedules Direct account.
   *
   * @param lineupName The name of the lineup as provided by <code>getLineupsForAccount()</code>.
   * @return The number of account add/remove changes remaining.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public static int deleteLineupFromAccount(String lineupName) throws IOException, SDException
  {
    SDSession session = ensureSession();

    for (SDAccountLineup lineup : session.getAccountLineups().getLineups())
    {
      String compareLineupName = screenFormatAccountLineup(lineup);
      // If there's more than one lineup with the same name added to the account, it could be listed
      // either way.
      if (lineupName.equals(compareLineupName + " - " + lineup.getLineup()) || compareLineupName.equals(lineupName))
      {
        int returnValue = session.deleteLineup(lineup);
        if (Sage.DBG) System.out.println("SDEPG Deleted lineup '" + lineupName + "' with " + returnValue + " changes remaining.");
        return returnValue;
      }
    }

    if (Sage.DBG) System.out.println("SDEPG Error: Unable find the lineup details to delete '" + lineupName + "'");
    throw new SDException(SDErrors.INVALID_LINEUP_DELETE);
  }

  /**
   * Add a lineup to the Schedules Direct account.
   *
   * @param lineupName The name of the lineup as provided by
   *                   <code>getLineupsForPostalCode(String, String)</code>.
   * @return The number of account add/remove changes remaining.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public static int addLineupToAccount(String lineupName) throws IOException, SDException
  {
    SDHeadendLineup lineup = lineupCache.get(lineupName);
    if (lineup == null)
    {
      String postalCode = getCurrentPostalCode();
      if (postalCode != null)
      {
        // Fill the cache with the last search.
        getLineupsForPostalCode(getCurrentCountry(getCurrentRegion()), postalCode);
        lineup = lineupCache.get(lineupName);
      }

      // This should never happen since we always look up the lineups first, then add them to the
      // account, so the lineup would have been cached in that step. The only explanation would be
      // the lineup name is wrong.
      if (lineup == null)
      {
        if (Sage.DBG) System.out.println("SDEPG Error: Unable to find lineup details to add '" + lineupName + "'");
        throw new SDException(SDErrors.INVALID_LINEUP);
      }
    }

    int returnValue = ensureSession().addLineup(lineup.getUri());
    if (Sage.DBG) System.out.println("SDEPG Added lineup '" + lineupName + "' with " + returnValue + " changes remaining.");
    return returnValue;
  }

  /**
   * Add a lineup to the Schedules Direct account.
   *
   * @param lineupID The ID of the lineup as provided by {@link SDHeadendLineup#getLineup()}.
   * @return The number of account add/remove changes remaining.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public static int addLineupToAccountByID(String lineupID) throws IOException, SDException
  {
    return ensureSession().addLineupByID(lineupID);
  }

  /**
   * Get details for an account lineup from a screen formatted lineup name.
   *
   * @param lineupName The lineup name as provided by <code>getAccountLineups()</code>.
   * @return The account lineup details or <code>null</code> if the lineup was not found.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public static SDAccountLineup getAccountLineup(String lineupName) throws IOException, SDException
  {
    // The STV may append a string to denote Schedules Direct lineups.
    if (lineupName.endsWith(SOURCE_LABEL))
      lineupName = lineupName.substring(0, lineupName.length() - SOURCE_LABEL.length());

    SDAccountLineups getLineups = ensureSession().getAccountLineups();
    SDAccountLineup lineups[] = getLineups.getLineups();

    for (SDAccountLineup lineup : lineups)
    {
      String compareLineupName = screenFormatAccountLineup(lineup);
      // If there's more than one lineup with the same name added to the account, it could be listed
      // either way.
      if (lineupName.equals(compareLineupName) || lineupName.equals(compareLineupName + " - " + lineup.getLineup()))
      {
        return lineup;
      }
    }

    return null;
  }

  public static long getHashFromAccountLineup(SDAccountLineup lineup) throws IOException, SDException
  {
    if (lineup == null)
      return  0;

    String charLineup = lineup.getLineup();
    if (charLineup == null)
      return 0;

    char chars[] = charLineup.toCharArray();
    long hash = 0;
    // Standard Java hashing function using long instead of int.
    for (int i = 0; i < chars.length; i++) {
      hash = 31 * hash + chars[i];
    }

    // Limit to 56 bits so we don't cause problems with the hash when we set the 63rd bit. We are
    // only using the low 56 bits in case we need to set more high bits in the future. Use unsigned
    // bit-shift and do not discard the extra bits.
    hash = (hash >>> 8) | (hash & 0xFF);

    // There could be collisions (like any hashing), but generally this should be unique enough and
    // a normal account will not have more than 6 lineups at most, so the odds of that happening are
    // also very low. The 2 highest bit is set to prevent collisions with WarlockRipper.
    return hash | 0x4000000000000000L; //((long)lineup.getLineup().hashCode() & 0xFFFFFFFFL) | 0x4000000000000000L;
  }

  public static String getLanguageForDigraph(String digraph)
  {
    String returnValue = countryDigraphToName.get(digraph);
    if (returnValue == null && countryDigraphToName.size() == 0)
    {
      try
      {
        SDLanguage languages[] = ensureSession().getLanguages();
        for (SDLanguage language : languages)
        {
          String countryDiagraph = language.getDigraph();
          if (countryDiagraph == null || countryDiagraph.length() == 0)
            continue;
          String name = language.getName();
          if (name == null || name.length() == 0)
            continue;

          countryDigraphToName.put(countryDiagraph, name);
        }

        returnValue = countryDigraphToName.get(digraph);
      } catch (Exception e){ countryDigraphToName.clear(); }
    }

    // Don't return a null value since this is used to get the language when importing programs and
    // returning a null value is never desirable in that case.
    return returnValue != null ? returnValue : "";
  }

  private static void getUpdateChannelLogos() throws IOException, SDException
  {
    Wizard wiz = Wizard.getInstance();
    SDAccountLineups accountLineups = ensureSession().getAccountLineups();
    EPGDataSource[] dataSources = EPG.getInstance().getDataSources();
    int logoID[] = new int[1];
    boolean didAdd[] = new boolean[1];
    // We don't have any good reason to throw this exception preventing anything from updating if it
    // could have, so if there are any with a lineup missing, we wait until the end to throw the
    // lineup missing exception.
    SDException throwLineupNotFound = null;

    synchronized (updateChannelsLock)
    {
      boolean useAlternativeChannelLogos = Sage.getBoolean(PROP_DOWNLOAD_ALTERNATE_LOGOS, false);
      for (EPGDataSource dataSource : dataSources)
      {
        if (!(dataSource instanceof SDRipper))
          continue;

        SDAccountLineup lineup = null;
        for (SDAccountLineup accountLineup : accountLineups.getLineups())
        {
          if (accountLineup.getLineup() != null &&
            accountLineup.getLineup().equals(((SDRipper) dataSource).getLineupID()))
          {
            lineup = accountLineup;
            break;
          }
        }
        // We have a lineup defined without a lineup in the actual account. Throw an exception.
        if (lineup == null)
        {
          throwLineupNotFound = new SDException(SDErrors.LINEUP_NOT_FOUND);
          continue;
        }

        String uri = lineup.getUri();
        SDLineupMap map = ensureSession().getLineup(uri);
        int numChans = map.getStations().length;
        if (Sage.DBG) System.out.println("SDEPG updating " + numChans + " channel logos");

        SDStation stations[] = map.getStations();
        for (SDStation station : stations)
        {
          Channel channel = wiz.getChannelForStationID(station.getStationID());
          // Don't use this opportunity to add new channels. Leave that for the next EPG update.
          if (channel == null)
            continue;

          String logoURLEncode;
          if (useAlternativeChannelLogos)
          {
            SDLogo logos[] = station.getStationLogos();
            // There should be an alternative logo in several cases. If there isn't we default to the
            // logo we would have had otherwise.
            logoURLEncode = logos.length > 1 ? logos[1].getURL() : logos.length != 0 ? logos[0].getURL() : null;
          }
          else
          {
            SDLogo logo = station.getLogo();
            logoURLEncode = logo != null ? logo.getURL() : null;
          }

          int logoMask;
          byte[] logoURL;
          if (logoURLEncode != null)
          {
            logoURL = SDImages.encodeLogoURL(logoURLEncode, logoID);
            logoMask = Channel.SD_LOGO_MASK | logoID[0];
          }
          else
          {
            logoURL = Pooler.EMPTY_BYTE_ARRAY;
            logoMask = 0;
          }

          // We are only changing the logo. Do not change anything else about this channel at this
          // time. We will leave any real changes to the next EPG update so we can ensure any new
          // notifications we implement in the future relating to channels will not have this one
          // path as an exception.
          wiz.addChannel(channel.getName(), channel.getLongName(),
            channel.getNetwork(), channel.getStationID(), logoMask, logoURL, didAdd);
        }
      }
    }

    if (throwLineupNotFound != null)
      throw throwLineupNotFound;
  }

  @Override
  protected boolean extractGuide(long inGuideTime)
  {
    // retryWait is volatile, so we need to create a local reference first before we do anything or
    // we might end up confusing the reason we immediately failed this method.
    long localWait = SDRipper.retryWait;
    if (Sage.time() < Math.abs(localWait))
    {
      if (Sage.DBG)
      {
        if (localWait <= 0)
        {
          System.out.println("SDEPG Warning: Schedules Direct servers are currently offline. Returning failure.");
        }
        else
        {
          System.out.println("SDEPG Error: Username and/or password are incorrect or missing. Returning failure.");
        }
      }
      return false;
    }

    try
    {
      SDAccountLineups accountLineups = ensureSession().getAccountLineups();
      SDAccountLineup lineup = null;

      // Get lineup by URI.
      for (SDAccountLineup accountLineup : accountLineups.getLineups())
      {
        if (accountLineup.getLineup() != null && accountLineup.getLineup().equals(this.lineupID))
        {
          lineup = accountLineup;
          break;
        }
      }

      if (lineup == null)
      {
        // The lineup is not present in the account. If the lineup is removed for any reason while
        // the updates are are happening, this exact error is the most likely one to be be thrown.
        throw new SDException(SDErrors.LINEUP_NOT_FOUND);
      }

      if (SageTV.getSyncSystemClock())
      {
        EPGDataSource[] dataSources = EPG.getInstance().getDataSources();
        boolean skip = false;

        // Don't update the time if WarlockRipper is also being used in case the times differ
        // significantly. We don't want to have potentially largish time shifts that could make
        // things a little confusing for the end user.
        for (EPGDataSource dataSource : dataSources)
        {
          if (dataSource instanceof WarlockRipper)
          {
            skip = true;
            break;
          }
        }

        if (!skip)
        {
          long beforeRequestTime = Sage.time();
          SDStatus status = ensureSession().getStatus();
          try
          {
            long sysTime = SDUtils.SDFullUTCToMillis(status.getDatetime());
            long afterRequestTime = Sage.time();
            sysTime += (afterRequestTime - beforeRequestTime) / 2;
            if (Math.abs(Sage.time() - sysTime) > 100)
            {
              Sage.setSystemTime(sysTime);
              if (Sage.DBG) System.out.println("SDEPG Set the system clock to be " + Sage.df(sysTime));
            }
          } catch (Exception e) {}
        }
      }

      if (lineup.isDeleted())
      {
        throw new SDException(SDErrors.LINEUP_DELETED);
      }

      // This value is set to the next update time if any content is not immediately available and
      // needs to be generated. A value of -1 means we have everything.
      long nextUpdate = -1;
      String uri = lineup.getUri();
      SDLineupMap map = ensureSession().getLineup(uri);
      int numChans = map.getStations().length;
      if (Sage.DBG) System.out.println("SDEPG got " + numChans + " channels");

      // Include any additional stations the the user has added to their lineup. There's no
      // guarantee that Schedules Direct will actually be able to use these since we have to look
      // things up based on if they exist in a lineup that is a part of the lineups added to the
      // account.
      java.util.Map<Integer, String[]> overrideMap = EPG.getInstance().getOverrideMap(getProviderID());
      if (overrideMap == null) overrideMap = new java.util.HashMap<Integer, String[]>();
      int numExtraChans = overrideMap.size();

      int[] stations = new int[numChans + numExtraChans];

      java.util.Iterator<Integer> walker = overrideMap.keySet().iterator();
      int extraChanNum = 0;
      while (walker.hasNext())
      {
        Integer val = walker.next();
        if (val != null && (!Sage.getBoolean("wizard/remove_airings_on_unviewable_channels2", true) || EPG.getInstance().canViewStation(val)))
        {
          stations[numChans + extraChanNum++] = val;
        }
      }

      EPG epg = EPG.getInstance();
      // Also include all channels from any other lineups that are not Zap2it lineups so they get
      // downloaded somewhere. This may end up being redundant...but so is our channel downloading
      // across lineups anyways. On embedded we also need to find any other channels that may have
      // been enabled on other lineups and do them in our update here.
      CaptureDeviceInput[] conInputs = MMC.getInstance().getConfiguredInputs();
      int warlockRipperStations[][] = new int[conInputs.length][];
      for (int j = 0; j < conInputs.length; j++)
      {
        CaptureDeviceInput conInput = conInputs[j];
        if (conInput.getProviderID() != getProviderID())
        {
          // Check if this is a non-Zap2it lineup
          EPGDataSource otherDS = epg.getSourceForProviderID(conInput.getProviderID());

          if (otherDS instanceof SDRipper)
          {
            continue;
          }
          else if (otherDS instanceof WarlockRipper)
          {
            // Get all of the stations covered by WarlockRipper so we can exclude them from being
            // updated by Schedules Direct. Otherwise we can end up overwriting data back and forth
            // as the providers might disagree on a description or title for example.
            warlockRipperStations[j] = epg.getAllStations(conInput.getProviderID());
            continue;
          }

          int[] altStations = epg.getAllStations(conInput.getProviderID());
          for (int i = 0; i < altStations.length; i++)
          {
            if (!epg.canViewStation(altStations[i]))
              altStations[i] = 0;
          }
          int[] newStations = new int[stations.length + altStations.length];
          System.arraycopy(stations, 0, newStations, 0, stations.length);
          System.arraycopy(altStations, 0, newStations, stations.length, altStations.length);
          stations = newStations;
        }
      }

      // Loop through again now that we have all of the copied stations and WarlockRipper stations
      // to ensure we are only updating ones that only Schedules Direct will be able to update.
      for (int[] warlockRipperStation : warlockRipperStations)
      {
        if (warlockRipperStation == null)
          continue;

        for (int j = 0; j < stations.length; j++)
        {
          for (int k = 0; k < warlockRipperStation.length; k++)
          {
            if (stations[j] == warlockRipperStation[k])
            {
              stations[j] = 0;
              break;
            }
          }
        }
      }

      // Only load these hash lookup maps when we need them. They are usually only needed once per
      // unique lineup per day. The amount of data in these files typically amounts to 3MB, but it
      // could be a lot more under some circumstances.
      Map<Integer, Map<String, String>> stationDayMd5s = loadStationDayMd5Map();
      Map<String, String> programMd5Map = loadProgramMd5Map();

      java.util.Map<Integer, String[]> lineMap = new java.util.HashMap<Integer, String[]>();
      java.util.Map<Integer, String[]> physicalMap = null;
      List<String> channelNumbers = new ArrayList<String>();
      boolean[] caddrv = new boolean[1];
      int logoID[] = new int[1];

      synchronized (updateChannelsLock)
      {
        boolean useAlternativeChannelLogos = Sage.getBoolean(PROP_DOWNLOAD_ALTERNATE_LOGOS, false);
        for (int i = 0; i < numChans; i++)
        {
          SDStation station = map.getStations()[i];

          int stationID = station.getStationID();
          String chanName = station.getCallsign();
          String longName = station.getName();
          String networkName = station.getAffiliate();
          String logoURLEncode;
          if (useAlternativeChannelLogos)
          {
            SDLogo logos[] = station.getStationLogos();
            // There should be an alternative logo in several cases. If there isn't we default to the
            // logo we would have had otherwise.
            logoURLEncode = logos.length > 1 ? logos[1].getURL() : logos.length != 0 ? logos[0].getURL() : null;
          }
          else
          {
            SDLogo logo = station.getLogo();
            logoURLEncode = logo != null ? logo.getURL() : null;
          }

          int logoMask;
          byte[] logoURL;
          if (logoURLEncode != null)
          {
            logoURL = SDImages.encodeLogoURL(logoURLEncode, logoID);
            logoMask = Channel.SD_LOGO_MASK | logoID[0];
          }
          else
          {
            logoURL = Pooler.EMPTY_BYTE_ARRAY;
            logoMask = 0;
          }
          // Physical channel.
          String dtvChan = null;
          channelNumbers.clear();


          // Get all channels with this station ID.
          for (int j = 0; j < map.getMap().length; j++)
          {
            SDChannelMap channelMap = map.getMap()[j];

            if (stationID == channelMap.getStationID())
            {
              // Antenna maps will never have leading zeros because they don't use virtual channels.
              if (SDRipper.removeLeadingZeros && !(channelMap instanceof SDAntennaChannelMap))
              {
                channelNumbers.add(SDUtils.removeLeadingZeros(channelMap.getChannel()));
              }
              else
              {
                String tempString = channelMap.getPhysicalChannel();
                if (tempString != null)
                  dtvChan = tempString;

                channelNumbers.add(channelMap.getChannel());
              }
            }
          }


          String[] chanNums = channelNumbers.toArray(new String[channelNumbers.size()]);
          // This will not return a null value.
          String[] prevChans = EPG.getInstance().getChannels(providerID, stationID);
          // Preserve the first priority channel # for this station.
          if (prevChans.length > 0 && chanNums.length > 0 &&
              !prevChans[0].equals(chanNums[0]))
          {
            for (int j = 1; j < chanNums.length; j++)
            {
              if (chanNums[j].equals(prevChans[0]))
              {
                chanNums[j] = chanNums[0];
                chanNums[0] = prevChans[0];
                break;
              }
            }
          }

          lineMap.put(stationID, chanNums);

          Channel freshChan = wiz.addChannel(chanName, longName, networkName, stationID, logoMask, logoURL, caddrv);
          if (caddrv[0])
          {
            // Clear any potentially existing MD5 hashes for this channel and save immediately. If we
            // don't do this and the channel MD5 hashes have not changed since the last update, we
            // might not download anything for the entire channel.
            if (stationDayMd5s.remove(stationID) != null)
            {
              if (Sage.DBG) System.out.println("SDEPG Removed channel MD5 hash: " + freshChan);
              saveStationDayMd5Map(stationDayMd5s);
            }

            wiz.resetAirings(stationID);
            if (chanDownloadComplete && !Sage.getBoolean("epg/enable_newly_added_channels", true))
            {
              if (Sage.DBG) System.out.println("SDEPG Disabling newly added channel: " + freshChan);
              setCanViewStation(stationID, false);
            }
          }
          else if (chanDownloadComplete && prevChans.length == 1 && prevChans[0].equals("") && !Sage.getBoolean("epg/enable_newly_added_channels", true))
          {
            if (Sage.DBG) System.out.println("SDEPG Disabling newly added channel: " + freshChan);
            setCanViewStation(stationID, false);
          }
          if (chanDownloadComplete && (caddrv[0] || (prevChans.length == 1 && prevChans[0].equals(""))))
          {
            // Channel download already done on this lineup and this channel is either new in the DB
            // or it didn't have a mapping before on this lineup; either case means it's 'new' on the lineup
            String chanString = "";
            for (int x = 0; x < chanNums.length; x++)
            {
              chanString += chanNums[x];
              if (x < chanNums.length - 1)
                chanString += ',';
            }
            if (Sage.DBG) System.out.println("SDEPG Sending system message for new channel on lineup newAdd=" + caddrv[0] + " prevChans=" + java.util.Arrays.asList(prevChans) + " chan=" + freshChan);
            sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createNewChannelMsg(this, freshChan, chanString));
          }
          // If we haven't downloaded the channel data yet; then the station viewability cache in the EPG won't be correct yet
          if (!chanDownloadComplete || EPG.getInstance().canViewStation(stationID) || !Sage.getBoolean("wizard/remove_airings_on_unviewable_channels2", true))
            stations[i] = stationID;
          else
            stations[i] = 0;
          if (dtvChan != null && dtvChan.length() > 0 && dtvChan.indexOf('-') != -1)
          {
            // We were not even using this information; so now we put the physical channel string here instead, if parsing fails we just continue on and ignore it
            if (physicalMap == null)
              physicalMap = new java.util.HashMap<Integer, String[]>();
            physicalMap.put(stationID, new String[] { dtvChan });
          }
        }
      }

      if (numChans > 0)
      {
        EPG.getInstance().setLineup(providerID, lineMap);
        if (physicalMap != null)
          EPG.getInstance().setPhysicalLineup(providerID, physicalMap);
      }

      Sage.putBoolean(prefsRoot + CHAN_DOWNLOAD_COMPLETE, chanDownloadComplete = true);

      if (abort || !enabled) return false;

      Wizard wiz = Wizard.getInstance();

      // This is the most times we will try to get content that wasn't immediately available. The
      // longest wait between retries is 30 minutes. This number should not be made lower than 2.
      int retryLimit = 4;
      // This is used to count the number of times we have attempted to download all desired content.
      int retryAttempts = 0;
      // Determine what stations need updates and for what day. The map is set up with a string that
      // correlates with the day and a List that contains all of the station ID's that need to be
      // updated. We don't need to worry about days we don't have a hash for since those implicitly
      // won't match the md5 hash from Schedules Direct and will be included in the update. We are
      // using a LinkedHashMap so that the dates for the most part remain in chronological order as
      // they are added.
      Map<String, List<Integer>> updateDayStations = new LinkedHashMap<String, List<Integer>>();
      // Schedules Direct accepts multiple dates in one request, but to keep it simple, we are just
      // looking up one date with up to 500 stations. The actual limit is 5000, but it's not
      // recommended to do that in case the computer can't keep up. This should not be a significant
      // impact on overall performance.
      String dates[] = new String[1];
      // The station schedules to be queried at one time are aggregated into this List.
      List<Integer> lookupStationSchedule = new ArrayList<Integer>(500);
      // Programs discovered while iterating over the schedules that we either need to add or update
      // are aggregated into this List. Because these are always compared against the last MD5 hash,
      // we shouldn't get any duplicates here.
      List<String> needProgramDetails = new ArrayList<>();
      // This is used for after all of the program details have been gathered to quickly determine
      // if an airing can be created or not.
      Set<String> noProgramDetails = new HashSet<>();
      // Used to queue up all of the series that we would like to get more details for after all of
      // the programs are loaded. We are using a Set to remove duplicates since this is accumulated
      // as we see what episodes likely have a series associated with them.
      Set<String> needSeriesDetails = new HashSet<>();
      // String array used for various lookups that only contain one item.
      String singleLookup[] = new String[1];
      // All people added to the database are placed in this Set to be looked up after all guide
      // data has been populated. We are using a Set to remove duplicates for each pass.
      Set<SDPerson> addedPeople = new HashSet<>();
      // All people aliases added to the database are added to this Set to be looked up after all
      // guide data has been populated. The people aliases are overwritten with the addedPeople
      // before the lookup happens so that we are mostly only looking up non-alias people.
      Set<SDPerson> addedAliases = new HashSet<>();

      int downloadedPrograms = 0;
      int importAirings = 0;
      int downloadedAirings = 0;
      int downloadedTeams = 0;

      while (true)
      {
        {
          // We are using a Set to mostly eliminate duplicates on put. We just merged several
          // station ID's without any deduplication, so if there's more than one lineup, there's a
          // good chance we could be doing the same work twice without this.
          Set<Integer> lookupIds = new HashSet<Integer>();

          for (int i = 0; i < stations.length;)
          {
            if (abort || !enabled) return false;

            int stationID = stations[i++];
            if (stationID >= 10000) // Numbers below this are non-SD station ID
              lookupIds.add(stationID);

            // We likely have less than 1000 stations for most lineups, there is a 5000 station
            // limit, and a 10 minute timeout from the server, so we do need to make sure we don't
            // somehow exceed the limit and this number is chosen more to reduce the chances of a
            // timeout on particularly slow computers/connections.
            if (lookupIds.size() >= 1000 || (i == stations.length && lookupIds.size() > 0))
            {
              SDScheduleMd5 schedules[] = ensureSession().getSchedulesMd5(lookupIds, null);
              lookupIds.clear();

              for (SDScheduleMd5 schedule : schedules)
              {
                stationID = schedule.getStationID();
                Map<String, String> dayHash = stationDayMd5s.get(stationID);

                if (dayHash == null)
                {
                  dayHash = new HashMap<String, String>();
                  stationDayMd5s.put(stationID, dayHash);
                }

                for (SDScheduleMd5.Md5 md5Date : schedule.getDates())
                {
                  String day = md5Date.getDate();
                  String oldMd5 = dayHash.get(day);
                  String newMd5 = md5Date.getMd5();

                  // Nothing has changed, don't include it.
                  if (newMd5.equals(oldMd5))
                    continue;

                  // Update old hash. We don't save these values until we are completely done
                  // updating and we will not be referencing this again, so it's ok to do this now.
                  dayHash.put(day, newMd5);
                  List<Integer> updateStation = updateDayStations.get(day);

                  if (updateStation == null)
                  {
                    updateStation = new ArrayList<>();
                    updateDayStations.put(day, updateStation);
                  }

                  updateStation.add(stationID);
                }
              }
            }
          }
        }

        for (Map.Entry<String, List<Integer>> updateDayStation : updateDayStations.entrySet())
        {
          dates[0] = updateDayStation.getKey();
          List<Integer> updateStations = updateDayStation.getValue();
          Integer lookupStations[] = updateStations.toArray(new Integer[updateStations.size()]);

          for (int i = 0; i < lookupStations.length;)
          {
            if (abort || !enabled) return false;

            lookupStationSchedule.add(lookupStations[i++]);

            // Using 1000 as an upper limit because there is a good amount of data that will be
            // returned and this will help get data visible in the guide a little sooner on the
            // first run for this lineup.
            if (lookupStationSchedule.size() == 1000 || i == lookupStations.length && lookupStationSchedule.size() > 0)
            {
              SDStationSchedule schedules[] = ensureSession().getSchedules(lookupStationSchedule, dates);
              lookupStationSchedule.clear();

              for (int j = 0; j < schedules.length;)
              {
                SDStationSchedule schedule = schedules[j++];
                if (abort || !enabled) return false;

                //int stationID = schedule.getStationID();
                // Remove everything we are not going to retry later.
                // 7100 = The schedule is queued. Retry at a later time.
                // 2201 = Station no longer exists.
                //    0 = Station schedule was successfully received.
                if (schedule.getCode() != 7100)
                {
                  // Must be an Integer object. Otherwise we are removing an index.
                  updateStations.remove((Integer) schedule.getStationID());
                }
                else
                {
                  // At the end, if nextUpdate is not -1, we will wait until that time or up to 30
                  // minutes; whichever is less. We are going to set nextUpdate to the longest
                  // suggested wait and hope the next pass will get everything remaining.
                  long retryTime = SDUtils.SDFullUTCToMillis(schedule.retryTime());
                  if (retryTime > nextUpdate)
                    nextUpdate = retryTime;
                }

                if (schedule.getCode() != 0)
                {
                  continue;
                }

                SDProgramSchedule[] programSchedules = schedule.getPrograms();
                // This isn't completely accurate because if the program doesn't download, the
                // airing won't be created, but it's close enough.
                importAirings += programSchedules.length;

                // Gather all new programs or programs with changes so we can pull down the new data.
                for (SDProgramSchedule airing : programSchedules)
                {
                  String extID = airing.getProgramID();
                  String oldMd5 = programMd5Map.get(extID);
                  String newMd5 = airing.getMd5();
                  // The md5 hash is for the related program data, not the airing.
                  if (oldMd5 != null && newMd5 != null && oldMd5.equals(newMd5))
                  {
                    // Make sure the show is in the database before we assume it's ok to skip
                    // downloading the show details. There's no reason to log when this happens.
                    Show show = wiz.getShowForExternalID(enableSageTVCompat ? SDUtils.fromProgramToSageTV(extID) : extID);
                    if (show != null) continue;
                  }

                  // Update the hash. This isn't saved until the end of the update, so it's ok to do
                  // this now. This will also prevent another station with some or all of the same
                  // programs from downloading them a second time.
                  programMd5Map.put(extID, newMd5);
                  needProgramDetails.add(extID);

                  // This should never happen, but just in case, we will cut this off at the max
                  // query size threshold. Any programs we miss will be caught in the next pass.
                  if (needProgramDetails.size() >= 5000)
                  {
                    // Force the update to loop back and get the remainder.
                    nextUpdate = 0;
                    break;
                  }
                }

                // We have to do >= 500 programs because we could be anywhere from 500 to ~600. The
                // limit is 5000 and a typical request is a little over 500 combined between several
                // stations, so this should never be a problem. The most I have ever seen added for
                // one station was around 80. We also force one last request even if it's under 500
                // if we are on the last station for this batch.
                if (needProgramDetails.size() >= 500 || (j == schedules.length && needProgramDetails.size() > 0))
                {
                  if (Sage.DBG) System.out.println("SDEPG Importing " + needProgramDetails.size() + " programs for " + dates[0] + "...");

                  // Pull in all new programs for this station.
                  SDProgram programDetails[] = ensureSession().getPrograms(needProgramDetails);
                  needProgramDetails.clear();

                  for (SDProgram programDetail : programDetails)
                  {
                    // Remove everything we are not going to retry later.
                    // 6001 = The program is queued. Retry at a later time.
                    // 6000 = Program no longer exists.
                    //    0 = Program was successfully received.
                    if (programDetail.getCode() == 6001)
                    {
                      // This will prevent an airing from being created for this program that will
                      // not yet exist.
                      noProgramDetails.add(programDetail.getProgramID());
                      // At the end, if nextUpdate has a value greater than 0, we will wait until
                      // that time or up to 30 minutes; whichever is less. We aren't told how long
                      // to wait for this program to be generated, so we'll assume 5 minutes from
                      // now. If by the time we reach the retry, 5 minutes has already passed, then
                      // we will just retry immediately.
                      long retryTime = Sage.time() + Sage.MILLIS_PER_MIN * 5;
                      // Values for station ID day generation might provide a time that would be
                      // longer than 5 minutes from now, so we will wait for that time instead if
                      // that's the case.
                      if (retryTime > nextUpdate) nextUpdate = retryTime;
                      // Remove this program from the Md5 map so in case we exceed the retry limit
                      // today, the program details will be added/updated in the future.
                      String extID = programDetail.getProgramID();
                      programMd5Map.remove(extID);
                      // Remove the series from the need series details List because we will not
                      // have all of the shows present and this works best if the shows are added
                      // before series info. This will be added again on the next loop.
                      String removeSeries = SDUtils.getSeriesForEpisode(extID);
                      if (removeSeries != null) needSeriesDetails.remove(removeSeries);
                    }

                    if (schedule.getCode() != 0)
                    {
                      // Ensure we don't try to create an airing for this program.
                      noProgramDetails.add(programDetail.getProgramID());
                      continue;
                    }
                    String extID = programDetail.getProgramID();
                    String showType = programDetail.getShowType();
                    String entityType = programDetail.getEntityType();
                    boolean isSports = "Sports".equals(entityType);
                    boolean canGetSeries = SDUtils.canGetSeries(extID);
                    boolean isSeries = "Series".equals(showType);
                    boolean isMovie = extID.startsWith("MV");
                    SDMovie movie = isMovie ? programDetail.getMovie() : null;

                    // Add/update the series info any time an episode of a series is updated. This
                    // is a Set, so we will not create duplicates.
                    if (isSeries && canGetSeries)
                    {
                      needSeriesDetails.add(SDUtils.getSeriesForEpisode(programDetail.getProgramID()));
                    }

                    String title = programDetail.getTitle();
                    String episodeName = programDetail.getEpisodeTitle150();
                    String desc = programDetail.getDescriptions().getDescription(preferedDescDigraph, false).getDescription();
                    long showDuration = movie != null ? movie.getDuration() : programDetail.getDuration();

                    SDPerson cast[] = programDetail.getCast();
                    SDPerson crew[] = programDetail.getCrew();
                    SDPerson teams[] = programDetail.getTeams();
                    int castLen = cast.length;
                    int castCrewLen = castLen + crew.length;
                    int totalPeople = castCrewLen + teams.length;
                    List<Person> people = new ArrayList<>(totalPeople);
                    List<Byte> roles = new ArrayList<>(totalPeople);

                    SDPerson person;
                    for (int k = 0; k < totalPeople; k++)
                    {
                      if (k < castLen)
                        person = cast[k];
                      else if (k < castCrewLen)
                        person = crew[k - castLen];
                      else
                        person = teams[k - castCrewLen];

                      // Skip all unknown roles.
                      byte role = person.getRoleID();
                      if (role == 0)
                        continue;

                      Person newPerson = SDUtils.getPerson(person, wiz);
                      if (newPerson != null)
                      {
                        // Remove duplicates of the same person with the same role.
                        boolean addPerson = true;
                        for (int l = 0; l < people.size(); l++)
                        {
                          if (people.get(l).getID() == newPerson.getID() && roles.get(l) == role)
                          {
                            addPerson = false;
                            break;
                          }
                        }
                        if (!addPerson)
                          continue;

                        if (role == Show.TEAM_ROLE)
                          downloadedTeams++;

                        people.add(newPerson);
                        roles.add(role);

                        if (person.isAlias())
                          addedAliases.add(person);
                        else if (person.getPersonId().length() > 0)
                          addedPeople.add(person);
                      }
                    }
                    String[] expandedRatings = programDetail.getContentAdvisory();
                    // Is only set when the program is a movie.
                    String rated = movie != null ? programDetail.getContentRating(movieRatingBody) : ""; // programDetail.getContentRating(ratingBody);
                    String year = movie != null ? movie.getYear() : "";
                    String parentalRating = null; //not used programDetail.getContentRating(ratingBody);
                    SDKeyWords keyWords = programDetail.getKeyWords();
                    String[] bonus = keyWords != null ? keyWords.getAllKeywords() : Pooler.EMPTY_STRING_ARRAY;
                    if (movie != null)
                    {
                      String qualityRating = movie.getFormattedQualityRating();
                      if (qualityRating.length() > 0)
                      {
                        String newBonus[] = new String[bonus.length + 1];
                        newBonus[0] = qualityRating;
                        System.arraycopy(bonus, 0, newBonus, 1, bonus.length);
                        bonus = newBonus;
                      }
                    }
                    boolean uniqueShow = !extID.startsWith("SH") || "Special".equals(showType);
                    // Observation has shown this to be reasonably accurate when the show
                    // description is not in English, but is not the correct way to get this kind
                    // of information.
                    String language = ""; //getLanguageForDigraph(programDetail.getDescriptions().getFirstDescription().getDescriptionLanguage());
                    long originalAirDate = SDUtils.SDDateUTCToMillis(programDetail.getOriginalAirDate());
                    short seasonNum = programDetail.getSeason();
                    short episodeNum = programDetail.getEpisode();
                    String[] categories = programDetail.getGenres();
                    if ("Children".equals(programDetail.getAudience()))
                    {
                      categories = Arrays.copyOf(categories, categories.length + 1);
                      categories[categories.length - 1] = "Children target audience";
                    }
                    // If this is a sports event, the first category needs to be Sports event or
                    // Sports non-event.
                    if (isSports && showType != null)
                    {
                      String newCategories[] = new String[categories.length + 1];
                      // This will be 'Sports event' or 'Sports non-event'
                      newCategories[0] = showType;
                      System.arraycopy(categories, 0, newCategories, 1, categories.length);
                      categories = newCategories;
                    }
                    // Ensure Movie is first if the ID starts with MV and if it doesn't, add it.
                    else if (isMovie)
                    {
                      if (categories.length == 0)
                      {
                        categories = new String[1];
                        categories[0] = "Movie";
                      }
                      else
                      {
                        if (!"Movie".equals(categories[0]))
                        {
                          boolean addMovie = true;
                          for (int k = 0; k < categories.length; k++)
                          {
                            if ("Movie".equals(categories[k]))
                            {
                              categories[k] = categories[0];
                              categories[0] = "Movie";
                              addMovie = false;
                              break;
                            }
                          }
                          if (addMovie)
                          {
                            String newCategories[] = new String[categories.length + 1];
                            newCategories[0] = "Movie";
                            System.arraycopy(categories, 0, newCategories, 1, categories.length);
                            categories = newCategories;
                          }
                        }
                      }
                    }
                    if (categories == null || categories.length == 0) categories = Pooler.EMPTY_STRING_ARRAY;
                    int showcardID = 0;
                    byte imageURLs[][] = null;

                    if (!canGetSeries && programDetail.hasImageArtwork())
                    {
                      // Don't fail the show entry if there's an issue parsing the JSON for images.
                      try
                      {
                        singleLookup[0] = extID;
                        SDProgramImages images[] = ensureSession().getProgramImages(singleLookup);
                        singleLookup[0] = null;

                        if (images != null && images.length == 1 && images[0].getCode() == 0)
                        {
                          int showcardIdRef[] = new int[1];
                          imageURLs = SDImages.encodeImages(
                            images[0].getImages(), showcardIdRef, SDImages.ENCODE_ALL);
                          showcardID = showcardIdRef[0];
                        }
                      }
                      catch (Exception e)
                      {
                        SDSession.writeDebugException(e);
                      }
                    }

                    // The episode images appear to always be tall images that look rather awkward
                    // in the UI because they are photos from the specific episode that do not fit
                    // the dimensions of any normal recording, so we are only using them
                    // when the program is a movie and this is the only image available.
                    SDImage episodeImage;
                    if ((imageURLs == null || imageURLs.length == 0) && isMovie &&
                      (episodeImage = programDetail.getEpisodeImage()) != null)
                    {
                      int showcardIdRef[] = new int[1];
                      imageURLs = SDImages.encodeEpisodeImage(episodeImage, showcardIdRef);
                      showcardID = showcardIdRef[0];
                    }

                    if (imageURLs == null) imageURLs = Pooler.EMPTY_2D_BYTE_ARRAY;

                    if (enableSageTVCompat)
                      extID = SDUtils.fromProgramToSageTV(extID);

                    Person addPeople[] = people.size() == 0 ?
                      Pooler.EMPTY_PERSON_ARRAY : people.toArray(new Person[people.size()]);
                    byte addRoles[] = roles.size() == 0 ?
                      Pooler.EMPTY_BYTE_ARRAY : SDUtils.byteCollectionToByteArrayPrimitive(roles);

                    downloadedPrograms++;
                    wiz.addShow(title, episodeName, desc, showDuration, categories, addPeople,
                      addRoles, rated, expandedRatings, year, parentalRating, bonus, extID, language,
                      originalAirDate, DBObject.MEDIA_MASK_TV, seasonNum, episodeNum, uniqueShow,
                      showcardID, imageURLs);
                  }
                }
              }

              if (Sage.DBG) System.out.println("SDEPG Importing up to " + importAirings + " airings across " + schedules.length + " stations for " + dates[0] + "...");
              importAirings = 0;

              // Now we can add the airings for all of the programs we just added.
              for (SDStationSchedule schedule : schedules)
              {
                if (abort || !enabled) return false;
                if (schedule.getCode() != 0) continue;

                int stationID = schedule.getStationID();

                // We always add/update all of the airings unless the associated program couldn't be
                // downloaded at this time.
                for (SDProgramSchedule airing : schedule.getPrograms())
                {
                  if (abort || !enabled) return false;
                  String extID = airing.getProgramID();

                  // If the program is still in this array, we didn't successfully add it.
                  if (noProgramDetails.contains(extID)) continue;

                  long startTime = SDUtils.SDFullUTCToMillis(airing.getAirDateTime());
                  // I haven't seen this happen yet, but it will not make a useful contribution to
                  // the guide and could potentially create some unexpected situations.
                  if (startTime == 0)
                  {
                    if (Sage.DBG) System.out.println("SDEPG Warning: Airing has a 0 start time. Skipping: " + airing.toString());
                    continue;
                  }

                  long duration = airing.getDuration();
                  byte partsByte;
                  partsByte = airing.getMultipartByte();
                  byte parentalRatingByte = airing.getParentalRatingByte();
                  int misc = airing.getMisc();

                  // I haven't seen this happen yet, but it would look very strange in the guide,
                  // so I think it's better to not even add it.
                  if (duration == 0)
                  {
                    if (Sage.DBG) System.out.println("SDEPG Warning: Airing has a 0 duration. Skipping: " + airing.toString());
                    continue;
                  }

                  if (enableSageTVCompat)
                    extID = SDUtils.fromProgramToSageTV(extID);

                  downloadedAirings++;
                  wiz.addAiring(extID, stationID, startTime, duration, partsByte, misc, parentalRatingByte, DBObject.MEDIA_MASK_TV);
                }
              }
            }
          }
        }

        // Clear the Set of programs that have not been added for the next pass.
        noProgramDetails.clear();
        if (abort || !enabled) return false;

        // Get series details. If for any reason we don't have all of the potentially associated
        // programs, wait until we do or it's almost the very last pass. If it is the last pass and
        // we can't get all of the needed series info, it's not critical information, so we'll just
        // get it the next time we receive a new episode for the series.
        if (needSeriesDetails.size() > 0 && (nextUpdate == -1 || retryAttempts >= retryLimit - 1))
        {
          if (Sage.DBG) System.out.println("SDEPG Getting series info for " + needSeriesDetails.size() + " shows.");

          String series[] = needSeriesDetails.toArray(new String[needSeriesDetails.size()]);

          int i = 0;
          while (i < series.length)
          {
            int limit = i + Math.min(500, series.length - i);
            if (i == limit) break;
            while (i < limit)
            {
              String seriesDetails = series[i++];
              needProgramDetails.add(seriesDetails);
            }

            // Schedules Direct doesn't give us a lot to work with for series, but this is where a
            // lot of the artwork is being stored, so we are creating the entries with what little
            // information we can reliably get.
            SDProgram programs[] = ensureSession().getPrograms(needProgramDetails);
            needProgramDetails.clear();

            for (SDProgram seriesDetail : programs)
            {
              if (abort || !enabled) return false;

              if (seriesDetail.getCode() == 6001)
              {
                long retryTime = Sage.time() + Sage.MILLIS_PER_MIN * 5;
                if (retryTime > nextUpdate) nextUpdate = retryTime;
                continue;
              }
              else if (seriesDetail.getCode() != 0)
              {
                needSeriesDetails.remove(seriesDetail.getProgramID());
                continue;
              }

              String extID = seriesDetail.getProgramID();

              try
              {
                if (extID.startsWith("SH") && extID.length() == 14)
                {
                  String seriesTitle = seriesDetail.getTitle();
                  long showDuration = seriesDetail.getDuration();
                  int showcardID = 0;
                  String showCardBase = extID.substring(2, extID.length() - 4);
                  int legacySeriesID = Integer.parseInt(showCardBase);
                  String seriesDesc = null;
                  byte[][] seriesURLs = null;

                  char[] newID = extID.toCharArray();
                  newID[0] = 'E';
                  newID[1] = 'P';
                  newID[10] = '0';
                  newID[11] = '0';
                  newID[12] = '0';
                  newID[13] = '1';

                  singleLookup[0] = new String(newID);
                  SDSeriesDesc seriesDescs[] = ensureSession().getSeriesDesc(singleLookup);
                  singleLookup[0] = null;

                  // We should always only get one, but just in case.
                  if (seriesDescs.length == 1 && seriesDescs[0].getCode() == 0)
                  {
                    seriesDesc = seriesDescs[0].getDescription1000();
                    if (seriesDesc == null) seriesDesc = seriesDescs[0].getDescription100();
                  }
                  if (seriesDesc == null)
                    seriesDesc = seriesDetail.getDescriptions().getDescription(preferedDescDigraph, true).getDescription();
                  if (seriesDesc == null)
                    seriesDesc = "";

                  if (seriesDetail.hasImageArtwork())
                  {
                    newID[0] = 'S';
                    newID[1] = 'H';
                    singleLookup[0] = new String(newID, 0, 10);
                    SDProgramImages images[] = ensureSession().getProgramImages(singleLookup);
                    singleLookup[0] = null;

                    if (images != null && images.length == 1 && images[0].getCode() == 0)
                    {
                      int showcardIdRef[] = new int[1];
                      seriesURLs = SDImages.encodeImages(images[0].getImages(), showcardIdRef, SDImages.ENCODE_SERIES_ONLY);
                      showcardID = showcardIdRef[0];
                    }
                  }

                  // We don't have a description and we don't have any images.
                  if (seriesURLs == null || seriesURLs.length <= 1)
                    seriesURLs = Pooler.EMPTY_2D_BYTE_ARRAY;

                  // Don't bother adding a series that doesn't have a description and doesn't have
                  // any images to contribute.
                  if (seriesDesc.length() == 0 && seriesURLs.length == 0)
                    continue;

                  // Sometimes we will get cast without a character name.
                  SDPerson seriesPeople[] = SDUtils.removeNoCharacterPeople(seriesDetail.getCast());
                  SDPerson seriesCrew[] = SDUtils.removeNoCharacterPeople(seriesDetail.getCrew());
                  int seriesPeopleLen = seriesPeople.length;
                  int seriesPeopleCrewLen = seriesPeople.length + seriesCrew.length;
                  List<Person> people = new ArrayList<>(seriesPeopleCrewLen);
                  List<String> characters = new ArrayList<>(seriesPeopleCrewLen);

                  for (int j = 0; j < seriesPeopleCrewLen; j++)
                  {
                    SDPerson person;
                    if (j < seriesPeopleLen)
                      person = seriesPeople[j];
                    else
                      person = seriesCrew[j - seriesPeopleLen];

                    Person newPerson = SDUtils.getPerson(person, wiz);
                    if (newPerson != null)
                    {
                      String newCharacter = person.getCharacterName();
                      // Remove duplicates of the same person with the same character.
                      boolean addPerson = true;
                      for (int k = 0; k < people.size(); k++)
                      {
                        if (people.get(k).getID() == newPerson.getID() && characters.get(k).equals(newCharacter))
                        {
                          addPerson = false;
                          break;
                        }
                      }
                      if (!addPerson)
                        continue;

                      people.add(newPerson);
                      characters.add(newCharacter);

                      if (person.isAlias())
                        addedAliases.add(person);
                      else if (person.getPersonId().length() > 0)
                        addedPeople.add(person);
                    }
                  }

                  Person addPeople[] = people.size() == 0 ?
                    Pooler.EMPTY_PERSON_ARRAY : people.toArray(new Person[people.size()]);
                  String addCharacters[] = characters.size() == 0 ?
                    Pooler.EMPTY_STRING_ARRAY : characters.toArray(new String[characters.size()]);

                  downloadedPrograms++;
                  wiz.addSeriesInfo(legacySeriesID, showcardID, seriesTitle, "" /*Network*/,
                    seriesDesc, "" /*Historical description*/, "" /*Premiere date*/,
                    "" /*Finale date*/, "" /*Day of week*/,
                    showDuration == 0 ? "" : Sage.durFormatPretty(showDuration),
                    addPeople, addCharacters, seriesURLs);
                }
              }
              catch (Exception e)
              {
                // This is really only useful for debugging. Sometimes Schedules Direct
                // returns JSON data in a format that is not actually mentioned anywhere in
                // the guide and this will generate parsing errors. This kind of series
                // information is considered a bonus, so there is no reason to show failures
                // when this data cannot be imported/updated.
                SDSession.writeDebugException(e);
                needSeriesDetails.remove(extID);
              }
            }
          }
        }

        // This value is -1 if all content was processed. If this is set to 0, that means that some
        // content was not processed, but that content did not provide a specific time in the future
        // to check again. Set the next pass to start immediately.
        if (nextUpdate == 0)
          nextUpdate = Sage.time();

        // Overwrite all aliases that match original names. The alias is not used for images unless
        // the alias is the only entry.
        for (SDPerson person : addedPeople)
        {
          addedAliases.add(person);
        }
        addedPeople.clear();

        // Import Person object image URLs (and maybe other information one day). We are doing this
        // at a time when we might potentially need to wait for data that was not available on the
        // first pass, so this makes the waiting a little more productive.
        int peopleSize = addedAliases.size();
        if (peopleSize > 0)
        {
          if (Sage.DBG) System.out.println("SDEPG Attempting to import images for " +
            peopleSize + " pe" + (peopleSize == 1 ? "rson" : "ople") + "...");

          int remainingImports;
          final AtomicInteger totalPeople = new AtomicInteger(0);
          BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(9);
          // We are using a fixed thread size so they don't get re-created ever. This was a big
          // problem for users with slow internet connections because a full minute would actually
          // timeout while this thread was executing, then a new thread would need to spin up.
          ThreadPoolExecutor executor = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, queue) {
            @Override
            public ThreadFactory getThreadFactory()
            {
              return new ThreadFactory()
              {
                @Override
                public Thread newThread(Runnable r)
                {
                  if (Sage.DBG) System.out.println("SDEPG Starting new import thread...");
                  Thread returnThread = new Thread(r);
                  returnThread.setName("SDEPG-Import");
                  // I don't believe this has created any issues, but let's not allow these threads
                  // to run at the same priority as threads that directly impact the UI experience
                  // just in case.
                  returnThread.setPriority(Thread.MIN_PRIORITY + 1);
                  return returnThread;
                }
              };
            }
          };

          // If the queue is currently full, do the lookup on this thread.
          executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

          for (SDPerson nextPerson : addedAliases)
          {
            final SDPerson person = nextPerson;
            final Wizard threadWiz = wiz;

            Runnable runnable = new Runnable()
            {
              @Override
              public void run()
              {
                try
                {
                  String personName = person.getName();
                  int personId = person.getPersonIdAsInt();
                  int nameId = person.getNameIdAsInt();
                  String lookupPersonId = person.getPersonId();

                  // If these don't match, this is an alias.
                  if (personId != 0 && personId == nameId)
                    personId *= -1;

                  // We don't have any sources for these at the moment. It might not be a bad idea
                  // to see if we can hook into well-established free sources reliably for this
                  // extra data (e.g. TheTVDB.com).
                  int personDob = 0;
                  int personDod = 0;
                  String birthPlace = "";
                  short yearList[] = Pooler.EMPTY_SHORT_ARRAY;
                  String awardList[] = Pooler.EMPTY_STRING_ARRAY;

                  byte headShotUrls[][];
                  if (lookupPersonId.length() > 0)
                  {
                    try
                    {
                      headShotUrls = SDImages.encodeHeadShots(ensureSession().getCelebrityImages(lookupPersonId));
                    }
                    catch (Throwable e)
                    {
                      // Issues in getting head-shots should not be considered a significant issue,
                      // but we will report it in case it becomes the source of a major issue.
                      SDSageSession.writeDebugException(e);
                      if (Sage.DBG)
                      {
                        System.out.println("SDEPG Unable to get head-shots for: " + person);
                        e.printStackTrace(System.out);
                      }
                      headShotUrls = Pooler.EMPTY_2D_BYTE_ARRAY;
                    }
                  }
                  else
                  {
                    headShotUrls = Pooler.EMPTY_2D_BYTE_ARRAY;
                  }

                  // Since no other details would be updated, we can just skip this update
                  // completely. This should be removed or expanded if more data becomes available
                  // in the future.
                  if (headShotUrls.length == 0)
                    return;

                  totalPeople.incrementAndGet();

                  //System.out.println("SDEPG added head-shots for: " + person);
                  threadWiz.addPerson(personName, personId, personDob, personDod, birthPlace,
                    yearList, awardList, headShotUrls, DBObject.MEDIA_MASK_TV);
                }
                catch (Throwable e)
                {
                  System.out.println("SDEPG Unexpected exception getting head-shots for: " + person);
                  e.printStackTrace(System.out);
                }
              }
            };

            executor.execute(runnable);
          }

          executor.shutdown();
          // Some people have extremely slow internet connections that actually warrant this lengthy
          // wait. This will return the instant the queue is empty, so it's not like everyone will
          // be waiting for 5 minutes to pass.
          executor.awaitTermination(5, TimeUnit.MINUTES);
          // It's not a problem to not wait for the potentially remaining threads to complete. The
          // operation is thread-safe.
          addedAliases.clear();
          remainingImports = queue.size();
          peopleSize = totalPeople.get();
          if (Sage.DBG) System.out.println("SDEPG Imported images for " +
            peopleSize + " pe" + (peopleSize == 1 ? "rson" : "ople") +
            " (+" + remainingImports + ")");
        }

        // If the value isn't -1, that means some data was not able to be received immediately.
        if (nextUpdate != -1)
        {
          // We have retried enough times. Remove the hashes for the schedules that didn't get
          // updated so they will be tried again on the next update.
          if (retryAttempts++ >= retryLimit)
          {
            for (Map.Entry<String, List<Integer>> dayStation : updateDayStations.entrySet())
            {
              String updateDay = dayStation.getKey();
              List<Integer> updateStations = dayStation.getValue();
                for (Map.Entry<Integer, Map<String, String>> stationDayMd5 : stationDayMd5s.entrySet())
                {
                  int station = stationDayMd5.getKey();

                  for (int updateStation : updateStations)
                  {
                    if (updateStation == station)
                    {
                      Map<String, String> dayMd5 = stationDayMd5.getValue();
                      dayMd5.remove(updateDay);
                      break;
                    }
                  }
                }
              }
            if (Sage.DBG) System.out.println("SDEPG Some programs or schedules are still" +
                " needed. " + retryAttempts + " attempts have been made. Stopping with what we" +
                " have until the next cycle.");
            break;
          }

          if (abort || !enabled) return false;
          long currentTime = Sage.time();
          // Wait at least 5 seconds.
          long totalWait = Math.max(5000, nextUpdate - currentTime);
          // Don't wait longer than 30 minutes.
          if (totalWait > Sage.MILLIS_PER_MIN * 30)
          {
            totalWait = Sage.MILLIS_PER_MIN * 30;
            nextUpdate = currentTime + totalWait;
          }

          if (Sage.DBG) {
            String updateTime = Long.toString(totalWait / 60000);
            System.out.println("SDEPG Some programs and/or schedules are still" +
              " needed. Waiting " + updateTime + " minutes to get the remaining data.");
          }

          int loops = (int)Math.min(totalWait / 5000, Integer.MAX_VALUE);
          for (int i = 0; i < loops; i++)
          {
            if (abort || !enabled) return false;
            Thread.sleep(5000);

            // Fix the intervals once a ~minute. The sleep function works correctly, but I don't
            // trust it for 30 minutes + other code to be mostly accurate.
            if (i % 12 == 0)
            {
              currentTime = Sage.time();
              totalWait = Math.max(5000, nextUpdate - currentTime);
              loops = (int)Math.min(totalWait / 5000, Integer.MAX_VALUE);
            }
          }

          nextUpdate = -1;
        }
        else
        {
          if (Sage.DBG) System.out.println("SDEPG No updates left to process.");
          break;
        }
      }

      // Save the daily schedule md5 hashes.
      saveStationDayMd5Map(stationDayMd5s);
      // Save the md5 hashes for all programs.
      saveProgramMd5Map(programMd5Map);

      // We are tracking the number of teams added so that we have a reference point to verify if
      // the teams are broken or the show in question just does not have more detail.
      if (Sage.DBG) System.out.println("SDEPG Downloaded " + downloadedPrograms +
        " programs containing " + downloadedTeams + " teams for " +
        downloadedAirings + " airings for: " + lineup);

      long currentTime;
      if (editorialImportLimit > 0 &&
        (currentTime = Sage.time()) >= Sage.getLong(PROP_EDITORIAL_NEXT_RUN, currentTime))
      {
        // The dates must be in this format or the Wizard will never remove the entries.
        SimpleDateFormat editorialDate = new SimpleDateFormat("yyyy-MM-dd");
        List<SDEditorial> editorials = SDRecommended.getUsable(SDRecommended.getRecommendations(false));
        // Try to at least fill one screen with recommendations if possible.
        int minRecommendations = Math.min(6, editorialImportLimit / 2);
        if (editorials.size() < minRecommendations)
        {
          if (Sage.DBG) System.out.println("SDEPG Got less than " + minRecommendations +
            " recommendations, adding intelligent recordings");
          editorials = SDRecommended.getUsable(SDRecommended.getRecommendations(true));
        }
        SDRecommended.reduceByWeight(editorials, editorialImportLimit);
        for (SDEditorial editorial : editorials)
        {
          Airing airing = editorial.getAiring();
          String startTime = editorialDate.format(airing.getStartTime());
          String channelName = airing.getChannelName();
          Show show = airing.getShow();
          String description = show.getDesc();
          String title = show.getTitle();
          String externalId = show.getExternalID();
          if (enableSageTVCompat)
            externalId = SDUtils.fromProgramToSageTV(externalId);
          String url = SDRecommended.getBestEditorialImage(editorial);
          wiz.addEditorial(externalId, title, startTime, channelName, description, url);
        }
        Sage.putLong(PROP_EDITORIAL_NEXT_RUN, currentTime + editorialUpdateInterval);
      }
    }
    catch (SDException e)
    {
      switch (e.ERROR)
      {
        case NO_LINEUPS:
        case LINEUP_NOT_FOUND:
          sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createSDLineupMissingMsg(this));
          break;
        case LINEUP_DELETED:
          sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createLineupLostMsg(this));
          break;
        case ACCOUNT_EXPIRED:
          sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createSDAccountExpiredMsg());
          break;
        // No password is as good as an authentication failure from a corrective standpoint.
        case SAGETV_NO_PASSWORD:
        case INVALID_HASH:
        case INVALID_USER:
          sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createSDInvalidUsernamePasswordMsg());
          break;
        case ACCOUNT_LOCKOUT:
          sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createSDAccountLockOutMsg());
          break;
        case ACCOUNT_DISABLED:
          sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createSDAccountDisabledMsg());
          break;
      }
      setExceptionTimeout(e.ERROR);
      System.out.println("SDEPG Exception: " + e.getMessage() + " code=" + e.ERROR.CODE);
      e.printStackTrace(System.out);
      SDSageSession.writeDebugException(e);
      return false;
    }
    catch (Exception e)
    {
      System.out.println("SDEPG Exception thrown: " + e.getMessage());
      e.printStackTrace(System.out);
      SDSageSession.writeDebugException(e);
      return false;
    }

    return true;
  }

  private void saveStationDayMd5Map(Map<Integer, Map<String, String>> serialMap)
  {
    synchronized (FILE_SCHEDULE_MD5)
    {
      BufferedWriter writer = null;
      try
      {
        // Remove MD5 hashes for days older than yesterday. Otherwise we will slowly accumulate a very
        // large amount of data over the years.
        long yesterday = Sage.time() - Sage.MILLIS_PER_DAY;
        for (Iterator<Map.Entry<Integer, Map<String, String>>> iterator = serialMap.entrySet().iterator(); iterator.hasNext();)
        {
          // This is a maintenance task and does not need to complete every time for the update to
          // be considered successful.
          if (abort || !enabled) break;

          Map<String, String> dayMd5s = iterator.next().getValue();
          for (Iterator<Map.Entry<String, String>> dayMd5 = dayMd5s.entrySet().iterator(); dayMd5.hasNext();)
          {
            String day = dayMd5.next().getKey();
            long thisDay = SDUtils.SDDateUTCToMillis(day);
            if (thisDay < yesterday) dayMd5.remove();
          }

          // This will allow for stations to eventually be completely removed when they are no
          // longer being used or no longer exist.
          if (dayMd5s.size() == 0)
            iterator.remove();
        }

        writer = new BufferedWriter(new FileWriter(FILE_SCHEDULE_MD5));
        serializeStationDayMd5Map(serialMap, writer);
      }
      catch (IOException e)
      {
        if (Sage.DBG) System.out.println("SDEPG Unable to save " + FILE_SCHEDULE_MD5 + " error of: " + e.getMessage());
      }
      finally
      {
        if (writer != null)
        {
          try
          {
            writer.close();
          } catch (IOException e) {}
        }
      }
    }
  }

  private Map<Integer, Map<String, String>> loadStationDayMd5Map()
  {
    synchronized (FILE_SCHEDULE_MD5)
    {
      File programFile = new File(FILE_SCHEDULE_MD5);
      if (programFile.exists())
      {
        BufferedReader reader = null;
        try
        {
          reader = new BufferedReader(new FileReader(programFile));
          Map<Integer, Map<String, String>> returnValue = deserializeStationDayMd5Map(reader);

          // Check for stations without any airing data right now and if we have saved hashes for
          // that station, remove them so the station will update.
          int size = returnValue.size();
          long currTime = Sage.time();
          for (Iterator<Integer> iterator = returnValue.keySet().iterator(); iterator.hasNext(); )
          {
            Integer stationID = iterator.next();
            Airing airings[] = Wizard.getInstance().getAirings(stationID, currTime, currTime + 1, false);
            if (airings == null || airings.length == 0)
            {
              if (Sage.DBG) System.out.println("SDEPG Removing hashes for station " + stationID);
              iterator.remove();
            }
            else if (wiz.isNoShow(airings[0]))
            {
              if (Sage.DBG) System.out.println("SDEPG Removing hashes for station " + stationID + " because " + airings[0] + " is no show.");
              iterator.remove();
            }
          }

          // If we have made changes, save them so if we shutdown in the middle of any update, we
          // won't be left with only partial data.
          if (returnValue.size() != size)
          {
            saveStationDayMd5Map(returnValue);
          }

          return returnValue;
        }
        catch (IOException e)
        {
          if (Sage.DBG) System.out.println("SDEPG Unable to load " + FILE_SCHEDULE_MD5 + " error of: " + e.getMessage());
        }
        finally
        {
          if (reader != null)
          {
            try
            {
              reader.close();
            } catch (IOException e) {}
          }
        }
      }

      return new HashMap<Integer, Map<String, String>>();
    }
  }

  private void saveProgramMd5Map(Map<String, String> serialMap)
  {
    Wizard wiz = Wizard.getInstance();

    synchronized (FILE_PROGRAM_MD5)
    {
      BufferedWriter writer = null;
      try
      {
        // Remove md5 hashes for all programs no longer in the Wizard.
        for (Iterator<Map.Entry<String, String>> iterator = serialMap.entrySet().iterator(); iterator.hasNext();)
        {
          // This is a maintenance task and does not need to complete every time for the update to
          // be considered successful.
          if (abort || !enabled) break;
          String program = iterator.next().getKey();
          if (program == null) continue;
          Show show = wiz.getShowForExternalID(enableSageTVCompat ? SDUtils.fromProgramToSageTV(program) : program);
          if (show == null) iterator.remove();
        }

        writer = new BufferedWriter(new FileWriter(FILE_PROGRAM_MD5));
        serializeProgramMd5Map(serialMap, writer);
      }
      catch (IOException e)
      {
        if (Sage.DBG) System.out.println("SDEPG Unable to save " + FILE_PROGRAM_MD5 + " error of: " + e.getMessage());
      }
      finally
      {
        if (writer != null)
        {
          try
          {
            writer.close();
          } catch (IOException e) {}
        }
      }
    }
  }

  private Map<String, String> loadProgramMd5Map()
  {
    synchronized (FILE_PROGRAM_MD5)
    {
      File programFile = new File(FILE_PROGRAM_MD5);
      if (programFile.exists())
      {
        BufferedReader reader = null;
        try
        {
          reader = new BufferedReader(new FileReader(programFile));
          return deserializeProgramMd5Map(reader);
        }
        catch (IOException e)
        {
          if (Sage.DBG)
            System.out.println("SDEPG Unable to load " + FILE_PROGRAM_MD5 + " error of: " + e.getMessage());
        }
        finally
        {
          if (reader != null)
          {
            try
            {
              reader.close();
            } catch (IOException e) {}
          }
        }
      }

      return new HashMap<String, String>();
    }
  }

  private static void serializeStationDayMd5Map(Map<Integer, Map<String, String>> serialMap, BufferedWriter writer) throws IOException
  {
    for (Map.Entry<Integer, Map<String, String>> stationDayMd5 : serialMap.entrySet())
    {
      Integer station = stationDayMd5.getKey();
      if (station == null) continue;
      writer.write(Integer.toString(station));

      for (Map.Entry<String, String> dayMd5 : stationDayMd5.getValue().entrySet())
      {
        String day = dayMd5.getKey();
        if (day == null) continue;
        String md5 = dayMd5.getValue();
        if (md5 == null) continue;

        writer.write(",");
        writer.write(day);
        writer.write("=");
        writer.write(md5);
      }

      writer.write(System.lineSeparator());
    }

    writer.flush();
  }

  private static Map<Integer, Map<String, String>> deserializeStationDayMd5Map(BufferedReader reader) throws IOException
  {
    // This could be done more efficiently, but it's not really that important or noticeable.
    Map<Integer, Map<String, String>> returnValue = new HashMap<Integer, Map<String, String>>();

    String station;
    while ((station = reader.readLine()) != null)
    {
      if (station.length() == 0)
        continue;

      StringTokenizer tokenizer = new StringTokenizer(station, ",");
      // If we don't have more than the provider ID, there's no point in loading this data.
      if (tokenizer.hasMoreTokens())
      {
        String providerString = tokenizer.nextToken();
        try
        {
          int providerId = Integer.parseInt(providerString);
          Map<String, String> dayMd5Map = new HashMap<String, String>();

          while (tokenizer.hasMoreTokens())
          {
            String newDayMd5 = tokenizer.nextToken();
            int split = newDayMd5.indexOf("=");

            if (split != -1)
            {
              // The MD5 hash will always be 22 characters. Index 23 is one past our goal since we
              // are actually checking the index of the = sign delimiter.
              if ((newDayMd5.length() - split) != 23)
                continue;

              String day = newDayMd5.substring(0, split);
              // If there isn't a day or it doesn't parse correctly, skip this entry.
              if (day.length() == 0 || SDUtils.SDDateUTCToMillis(day) == 0) continue;
              String md5 = newDayMd5.substring(split + 1, newDayMd5.length());

              dayMd5Map.put(day, md5);
            }
          }

          returnValue.put(providerId, dayMd5Map);
        }
        catch (NumberFormatException e)
        {
          if (Sage.DBG) System.out.println("SDEPG Expected a number, got '" + providerString + "'");
        }
      }
    }

    return returnValue;
  }

  private static void serializeProgramMd5Map(Map<String, String> serialMap, BufferedWriter writer) throws IOException
  {
    for (Map.Entry<String, String> programMd5 : serialMap.entrySet())
    {
      String program = programMd5.getKey();
      if (program == null) continue;
      String md5 = programMd5.getValue();
      if (md5 == null) continue;

      writer.write(program);
      writer.write("=");
      writer.write(md5);
      writer.write(System.lineSeparator());
    }

    writer.flush();
  }

  private static Map<String, String> deserializeProgramMd5Map(BufferedReader reader) throws IOException
  {
    Map<String, String> returnValue = new HashMap<String, String>();

    String station;
    while ((station = reader.readLine()) != null)
    {
      //Ex. EP009370080144=H/s6ppFXjCnZ/m2+z9D2RA

      // Every single line will be 37 characters if the formatting is correct.
      if (station.length() != 37)
        continue;

      int split = station.indexOf("=");
      if (split != -1)
      {
        // Programs are always 14 characters. If this is true, the index will be 14.
        if (split != 14)
          continue;

        String program = station.substring(0, split);
        String md5 = station.substring(split + 1, station.length());

        returnValue.put(program, md5);
      }
    }

    return returnValue;
  }

  /**
   * Is Schedules Direct offline?
   * <p/>
   * This is determined by if the last time Schedules Direct was used if the service reported that
   * it was offline and 30 minutes has not yet passed since that time. This will not report that the
   * service is offline due to authentication failures.
   * <p/>
   * It is acceptable to poll this if you are just trying to determine when you will be able to try
   * to use the Schedules Direct service again.
   *
   * @return <code>true</code> if Schedules Direct is offline.
   */
  public static boolean isOffline()
  {
    long localWait = SDRipper.retryWait;
    // Negative means the service is offline.
    return localWait < 0 && Math.abs(localWait) > Sage.time();
  }

  /**
   * Is Schedules Direct available?
   * <p/>
   * This is determined by trying to communicate with Schedules Direct if an unexpired token is
   * present. If no token currently exists or it is expired, a new token will be acquired. If
   * the token is unable to be obtained for any reason, the service is considered unavailable.
   * <p/>
   * Most of the time if this method returns <code>false</code>, {@link #isOffline()} returns
   * <code>false</code> and you know credentials were provided, this is an authentication failure.
   * If this is the case, this method will not perform a real check again the service again until a
   * fixed waiting time (30 minutes to an hour) has expired or proper credentials have been entered.
   * This wait is to try to prevent the account from being locked due to bad credentials being
   * rapidly provided.
   *
   * @return <code>true</code> if Schedules Direct is available on this server.
   */
  public static boolean isAvailable()
  {
    long localWait = SDRipper.retryWait;
    if (localWait != 0 && Math.abs(localWait) > Sage.time())
    {
      if (Sage.DBG) System.out.println("SDEPG Unable to use the Schedules Direct service at this time.");
      return false;
    }

    try
    {
      ensureSession();
      return true;
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("SDEPG Unable to use the Schedules Direct service at this time: " + e.getMessage());
      if (e instanceof SDException)
        setExceptionTimeout(((SDException) e).ERROR);
      return false;
    }
  }

  /**
   * Get details for multiple in progress sports.
   * <p/>
   * This method will not throw any exceptions. If there are exceptions, they will be converted to
   * the numeric code for the actual errors and contained in {@link SDInProgressSport} objects. If
   * {@link SDInProgressSport#getCode()} equals 0, then the entire object is valid. Otherwise the
   * code corresponds with {@link SDErrors#CODE}.
   *
   * @param programIDs The program (external) ID's to look up.
   * @return {@link SDInProgressSport[]} with indexes that correspond with the same indexes as
   *         {@param programIDs}.
   */
  public static SDInProgressSport[] getInProgressSport(String programIDs[])
  {
    if (programIDs == null || programIDs.length == 0)
      return new SDInProgressSport[0];

    SDInProgressSport returnValues[] = new SDInProgressSport[programIDs.length];

    long localWait = SDRipper.retryWait;
    if (localWait != 0 && Math.abs(localWait) > Sage.time())
    {
      if (localWait < 0)
        Arrays.fill(returnValues, new SDInProgressSport(SDErrors.SERVICE_OFFLINE.CODE));
      else
        Arrays.fill(returnValues, new SDInProgressSport(SDErrors.SAGETV_NO_PASSWORD.CODE));
      return returnValues;
    }

    for (int i = 0; i < returnValues.length; i++)
    {
      try
      {
        returnValues[i] = ensureSession().getInProgressSport(programIDs[i]);
      }
      catch (SDException e)
      {
        if (Sage.DBG)
        {
          System.out.println("SDEPG Unable to get in progress sport: " + e.toString() + " programId=" + programIDs[i]);
          e.printStackTrace(System.out);
        }
        setExceptionTimeout(e.ERROR);
        switch (e.ERROR)
        {
          case SERVICE_OFFLINE:
            Arrays.fill(returnValues, i, returnValues.length, new SDInProgressSport(SDErrors.SERVICE_OFFLINE.CODE));
            return returnValues;
          case SAGETV_NO_PASSWORD:
          case INVALID_HASH:
          case INVALID_USER:
            Arrays.fill(returnValues, i, returnValues.length, new SDInProgressSport(SDErrors.SAGETV_NO_PASSWORD.CODE));
            return returnValues;
          default:
            returnValues[i] = new SDInProgressSport(SDErrors.SAGETV_UNKNOWN.CODE);
            break;
        }
      }
      catch (Exception e)
      {
        if (Sage.DBG)
        {
          System.out.println("SDEPG Unable to get in progress sport: " + e.toString() + " programId=" + programIDs[i]);
          e.printStackTrace(System.out);
        }
        returnValues[i] = new SDInProgressSport(SDErrors.SAGETV_UNKNOWN.CODE);
      }
    }
    return returnValues;
  }

  private static void setExceptionTimeout(SDErrors error)
  {
    switch (error)
    {
      case SERVICE_OFFLINE:
        // When the service is offline, we should only check every 30 minutes to see if it's back.
        // This might generate EPG warnings in the UI if it goes on for a while.
        SDRipper.retryWait = -(Sage.time() + Sage.MILLIS_PER_MIN * 30);
        break;
      case SAGETV_NO_PASSWORD:
      case INVALID_HASH:
      case INVALID_USER:
        // Set this to an hour so we aren't too obnoxious about the authentication error messages
        // and so we shouldn't accidentally lock the account out.
        SDRipper.retryWait = Sage.time() + Sage.MILLIS_PER_HR;
        break;
      case ACCOUNT_LOCKOUT:
        SDRipper.retryWait = Sage.time() + Sage.MILLIS_PER_HR;
        break;
    }
  }
}
