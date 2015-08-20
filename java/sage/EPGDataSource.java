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

import sage.Wizard.MaintenanceType;

public class EPGDataSource
{
  public static final long MILLIS_PER_HOUR = 1000L * 60L * 60L;
  protected static final String PROVIDER_ID = "provider_id";
  protected static final String ENABLED = "enabled";
  protected static final String LAST_RUN = "last_run";
  protected static final String EXPANDED_UNTIL = "expanded_until";
  protected static final String EPG_NAME = "epg_name";
  protected static final String DUMP_DIR = "dump_dir";
  protected static final String UNAVAILABLE_STATIONS = "unavailable_stations";
  protected static final String UNAVAILABLE_CHANNEL_NUMS = "unavailable_channel_nums";
  protected static final String APPLIED_SERVICE_LEVEL = "applied_service_level";
  protected static final String CHAN_DOWNLOAD_COMPLETE = "chan_download_complete";
  protected static final String SERVER_UPDATE_ID = "server_update_id";
  protected static final String DISABLE_DATA_SCANNING = "disable_data_scanning";
  protected static final String EPG_DATA_SCAN_PERIOD = "epg_data_scan_period";

  protected static final long CONFIRMATION_AHEAD_TIME = 24*MILLIS_PER_HOUR;
  private static long GPS_OFFSET;
  private static final java.text.DateFormat utcDateFormat = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
  private static final java.text.DateFormat localDateFormat = new java.text.SimpleDateFormat("MM/dd/yyyy hh:mm:ss");
  static
  {
    // start time (GPS time), 0 is at Jan 6, 1980
    java.util.GregorianCalendar gcal = new java.util.GregorianCalendar(1980, java.util.Calendar.JANUARY, 6, 0, 0, 0);
    gcal.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
    GPS_OFFSET = gcal.getTimeInMillis();
    utcDateFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
  }

  public EPGDataSource(int inEPGSourceID)
  {
    wiz = Wizard.getInstance();
    epgSourceID = inEPGSourceID;
    //Wizard.getInstance().notifyOfID(epgSourceID);

    prefsRoot = EPG.EPG_DATA_SOURCES_KEY + '/' + epgSourceID + '/';
    lastRun = Sage.getLong(prefsRoot + LAST_RUN, 0);
    expandedUntil = Sage.getLong(prefsRoot + EXPANDED_UNTIL, 0);
    providerID = Sage.getLong(prefsRoot + PROVIDER_ID, 0);
    enabled = Sage.getBoolean(prefsRoot + ENABLED, true);
    name = Sage.get(prefsRoot + EPG_NAME, "Undefined Source Name");
    Sage.put(prefsRoot + EPG.EPG_CLASS, "Basic");
    unavailStations = Sage.parseCommaDelimIntSet(Sage.get(prefsRoot + UNAVAILABLE_STATIONS, ""));
    unavailChanNums = Sage.parseCommaDelimSet(Sage.get(prefsRoot + UNAVAILABLE_CHANNEL_NUMS, ""));
    appliedServiceLevel = Sage.getInt(prefsRoot + APPLIED_SERVICE_LEVEL, 0);
    chanDownloadComplete = Sage.getBoolean(prefsRoot + CHAN_DOWNLOAD_COMPLETE, false);
    dataScanAllowed = !Sage.getBoolean(prefsRoot + DISABLE_DATA_SCANNING, false);
    dumpDir = Sage.get(prefsRoot + DUMP_DIR, null);
    if (dumpDir != null)
    {
      new java.io.File(dumpDir).mkdirs();
    }
    updateIDMap = java.util.Collections.synchronizedMap(new java.util.HashMap());
    String updateStr = Sage.get(prefsRoot + SERVER_UPDATE_ID, "");
    java.util.StringTokenizer toker = new java.util.StringTokenizer(updateStr, ";");
    while (toker.hasMoreTokens())
    {
      String toke = toker.nextToken();
      int idx = toke.indexOf(',');
      if (idx != -1)
      {
        try
        {
          updateIDMap.put(new Integer(toke.substring(0, idx)), new Long(toke.substring(idx + 1)));
        }
        catch (Exception e)
        {}
      }
    }
  }

  public boolean doesStationIDWantScan(int statID)
  {
    // These are the station IDs we assign ourselves in the channel scan. Exclude the ones that map to default channels and
    // also ones that map to tribune station IDs
    return statID > 0 && statID < 10000 && dataScanAllowed;
  }

  public boolean initDataScanInfo()
  {
    if (Sage.client || !dataScanAllowed) return false;
    // Get our CDI and find out if it does data scans, and if it does then sync our update times with when
    // we're expanded until so the EPG will tell us to update when the next scan can be done
    CaptureDeviceInput[] cdis = MMC.getInstance().getInputsForProvider(providerID);
    scannedUntil = Long.MAX_VALUE;
    if (cdis.length > 0 && cdis[0].doesDataScanning())
    {
      int[] allStations = EPG.getInstance().getAllStations(providerID);
      for (int i = 0; i < allStations.length; i++)
      {
        if (canViewStation(allStations[i]) && doesStationIDWantScan(allStations[i]))
        {
          Long stationUpdateTime = (Long) updateIDMap.get(new Integer(allStations[i]));
          if (stationUpdateTime != null)
          {
            scannedUntil = Math.min(stationUpdateTime.longValue(), scannedUntil);
          }
          else
            scannedUntil = 0; // haven't scanned yet for this station!
        }
      }
    }
    if (scannedUntil <= Sage.time())
    {
      boolean foundOne = false;
      for (int i = 0; i < cdis.length; i++)
      {
        if (cdis[i].getCaptureDevice().requestDataScan(cdis[i]))
          foundOne = true;
      }
      if (foundOne)
      {
        dataScanRequested = true;
        Seeker.getInstance().kick();
      }
      return true;
    }
    else
      return false;
  }

  protected void doDataScan()
  {
    long dataScanPeriod = Sage.getLong(EPG_DATA_SCAN_PERIOD, 4*Sage.MILLIS_PER_HR);
    // This does the data scan if it needs to be done
    CaptureDeviceInput[] cdis = MMC.getInstance().getInputsForProvider(providerID);
    CaptureDeviceInput cdi = null;
    boolean kickSeekNow = false;
    for (int i = 0; i < cdis.length; i++)
    {
      if (cdi != null)
      {
        cdis[i].getCaptureDevice().cancelDataScanRequest(cdis[i]);
        kickSeekNow = true;
      }
      else if (cdis[i].isActive() && cdis[i].getCaptureDevice().isDataScanning())
        cdi = cdis[i];
    }
    if (kickSeekNow)
      Seeker.getInstance().kick();
    if (dataScanAllowed && cdi != null && cdi.isActive() && cdi.getCaptureDevice().isDataScanning())
    {
      if (Sage.DBG) System.out.println("EPGDS " + name + " found a capture device to start data scanning with:" + cdi);
      // Now we need to find the actual stations we want to scan for and go to it!
      int[] allStations = EPG.getInstance().getAllStations(providerID);
      long newScannedUntil = Long.MAX_VALUE;
      java.util.HashMap majorToChannelMap = new java.util.HashMap();
      for (int i = 0; i < allStations.length; i++)
      {
        if (abort || !enabled) return;
        if (canViewStation(allStations[i]) && doesStationIDWantScan(allStations[i]))
        {
          Long stationUpdateTime = (Long) updateIDMap.get(new Integer(allStations[i]));
          if (stationUpdateTime != null)
          {
            if (stationUpdateTime.longValue() > Sage.time())
            {
              newScannedUntil = Math.min(stationUpdateTime.longValue(), newScannedUntil);
              continue;
            }
          }
          String currChan = EPG.getInstance().getPhysicalChannel(providerID, allStations[i]);
          java.util.StringTokenizer toker = new java.util.StringTokenizer(currChan, "-");
          String majChan = currChan;
          java.util.ArrayList currStatList = null;
          if (toker.countTokens() > 1)
          {
            if (toker.countTokens() > 2)
              majChan = toker.nextToken() + "-" + toker.nextToken();
            else
              majChan = toker.nextToken();
          }
          /*					else
					{
						// This probably isn't a digital TV channel which means it'll screw us up, so skip it
						// This could also be a channel which doesn't have a major-minor identifier!!
						continue;
					}*/
          currStatList = (java.util.ArrayList) majorToChannelMap.get(majChan);
          if (currStatList == null)
          {
            currStatList = new java.util.ArrayList();
            majorToChannelMap.put(majChan, currStatList);
          }
          currStatList.add(new Integer(allStations[i]));
        }
      }

      java.util.Iterator walker = majorToChannelMap.entrySet().iterator();
      while (walker.hasNext())
      {
        if (abort || !enabled) return;
        java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
        String currMajor = (String) ent.getKey();
        java.util.ArrayList currStatList = (java.util.ArrayList) ent.getValue();

        // If we're here then we want to scan!
        synchronized (Seeker.getInstance())
        {
          if (cdi.getCaptureDevice().isDataScanning())
          {
            cdi.tuneToChannel(EPG.getInstance().getPhysicalChannel(providerID, ((Integer)currStatList.get(0)).intValue()));
          }
          else // our data scanning has been stopped, so just return
            return;
        }

        // Now we wait until we think we have all of the data for this channel
        try
        {
          if (Sage.DBG) System.out.println("EPGDS waiting for data scan on major channel " + currMajor + "....");
          Thread.sleep(Sage.getLong("epg/data_scan_channel_dwell_new", 2*Sage.MILLIS_PER_MIN));
        }
        catch (Exception e)
        {}
        // We should do a scan for DTV data every 4 hours, so mark it as done for the next 4 hours
        // But round this up to the next hour so we don't do a bunch of incremental scans
        // when that timer runs out
        long newval = Sage.time() + dataScanPeriod;
        newval = (newval - (newval % (Sage.MILLIS_PER_HR))) + Sage.MILLIS_PER_HR;
        for (int i = 0; i < currStatList.size(); i++)
        {
          updateIDMap.put(currStatList.get(i), new Long(newval));
        }
        newScannedUntil = Math.min(newval, newScannedUntil);
      }
      if (newScannedUntil < Long.MAX_VALUE)
      {
        saveUpdateMap();
        scannedUntil = newScannedUntil;
      }
      for (int i = 0; i < cdis.length; i++)
        cdis[i].getCaptureDevice().cancelDataScanRequest(cdis[i]);
      dataScanRequested = false;
      Seeker.getInstance().kick();
    }
  }

  public void processEPGDataMsg(sage.msg.SageMsg msg)
  {
    if (!dataScanAllowed) return;
    /*
     * The EPG message data format is as follows:
     * EPG-0|major-minor AN/DT|startTimeGPS|durationSeconds|language|title|description|rating|
     */

    String msgString;
    try
    {
      msgString = new String((byte[])msg.getData(), Sage.BYTE_CHARSET);
    }
    catch (java.io.UnsupportedEncodingException e)
    {
      msgString = new String((byte[])msg.getData());
    }
    if (((byte[])msg.getData()).length != msgString.length())
      throw new InternalError("Byte array length is not the same length as string and we used a byte charset!!!");
    if (msgString.length() == 0) return;
    try
    {
      int offset = 0;
      java.util.StringTokenizer toker = new java.util.StringTokenizer(msgString, "|", true);
      offset += toker.nextToken().length(); // First token is "EPG-0"
      offset += toker.nextToken().length(); // delimiter
      String chanInfo = toker.nextToken(); // Channel number and DT or AN
      offset += chanInfo.length();
      String chanNum = chanInfo.substring(0, chanInfo.indexOf(' '));
      int stationID = sage.EPG.getInstance().guessStationIDFromPhysicalChannel(providerID, chanNum, chanNum.indexOf('-') != -1);
      if (stationID == 0)
        stationID = sage.EPG.getInstance().guessStationID(providerID, chanNum);

      if (stationID > 10000)
      {
        // It has TMS EPG data, so do NOT overwrite it with what we have found here
        // For TVTV they're station IDs overlap with the generated ones so don't take anything if using their data
        //if (sage.Sage.DBG) System.out.println("Skipping EPG data message because we have that channel's EPG data from a better source");
        return;
      }
      if (stationID == 0)
      {
        //if (sage.Sage.DBG) System.out.println("Skipping EPG data message because we don't have a station ID for this channel");
        return;
      }
      offset += toker.nextToken().length(); // delimiter
      String timeStr = toker.nextToken();
      offset += timeStr.length();
      long startTime;
      try
      {
        if (timeStr.startsWith("GPS:"))
        {
          startTime = Long.parseLong(timeStr.substring(4)) * 1000;
          startTime += GPS_OFFSET;
          // Fix issues with leap second differences between GPS & UTC time
          startTime -= startTime % 60000;
        }
        else if (timeStr.startsWith("UTC:"))
        {
          startTime = utcDateFormat.parse(timeStr.substring(4)).getTime();
        }
        else if (timeStr.startsWith("LOCAL:"))
        {
          localDateFormat.setTimeZone(java.util.TimeZone.getDefault());
          startTime = localDateFormat.parse(timeStr.substring(6)).getTime();
        }
        else
          startTime = Long.parseLong(timeStr) * 1000;
      }
      catch (Exception e)
      {
        System.out.println("ERROR parsing EPG message start time of:" + e);
        return;
      }
      offset += toker.nextToken().length(); // delimiter
      String durStr = toker.nextToken();
      offset += durStr.length();
      int duration = Integer.parseInt(durStr); // duration
      offset += toker.nextToken().length(); // delimiter
      String language = toker.nextToken();
      offset += language.length();
      if (!"|".equals(language))
        offset += toker.nextToken().length(); // delimiter
      else
        language = "";
      if (language.length() > 0)
      {
        if ("eng".equalsIgnoreCase(language))
          language = "English";
        else if ("spa".equalsIgnoreCase(language))
          language = "Spanish";
        else if ("dan".equalsIgnoreCase(language))
          language = "Danish";
        else if ("swe".equalsIgnoreCase(language))
          language = "Swedish";
        else if ("fra".equalsIgnoreCase(language))
          language = "French";
      }
      // Now we need to check for alternate character sets which means we'd need to switch to the byte arrays
      String title="", description="";
      for (int i = 0; i < 2 && offset < msgString.length(); i++)
      {
        if (msgString.charAt(offset) != '[')
        {
          if (i == 0)
          {
            title = toker.nextToken(); // title
            offset += title.length();
            if ("|".equals(title))
              title = "";
            else if (toker.hasMoreTokens())
              offset += toker.nextToken().length(); // delimiter
          }
          else
          {
            description = toker.nextToken(); // description
            offset += description.length();
            if ("|".equals(description))
              description = "";
            else if (toker.hasMoreTokens())
              offset += toker.nextToken().length(); // delimiter
          }
        }
        else
        {
          String charset = Sage.BYTE_CHARSET;
          int len = msgString.indexOf('|', offset + 1) - offset;
          int fullLen = len;
          int baseOffset = offset;
          do
          {
            int brack1 = offset;
            do
            {
              int brack2 = msgString.indexOf(']', brack1);
              if (brack2 == -1)
                break;
              int eqIdx = msgString.indexOf('=', brack1);
              if (eqIdx > brack2 || eqIdx == -1)
                break;
              String attName = msgString.substring(brack1 + 1, eqIdx);
              String attValue = msgString.substring(eqIdx + 1, brack2);
              if ("set".equals(attName))
                charset = attValue;
              else if ("len".equals(attName))
              {
                try
                {
                  len = Integer.parseInt(attValue);
                }
                catch (NumberFormatException e){
                  if (Sage.DBG) System.out.println("Formatting error with EPG data:" + e);
                }
              }
              offset += brack2 - offset + 1;
              brack1 = msgString.indexOf('[', brack2);
            } while (brack1 != -1 && brack1 < offset + len);
            try
            {
              if (i == 0)
                title += new String((byte[])msg.getData(), offset, len, charset);
              else
                description += new String((byte[])msg.getData(), offset, len, charset);
            }
            catch (java.io.UnsupportedEncodingException e)
            {
              if (Sage.DBG) System.out.println("Unsupported encoding for EPG data of:" + charset + " err=" + e);
              if (i == 0)
                title += new String((byte[])msg.getData(), offset, len);
              else
                description += new String((byte[])msg.getData(), offset, len);
            }
            //if (Sage.DBG) System.out.println("Parsing EPG data w/ charset=" + charset + " len=" + len + ((i == 0) ? (" title=" + title) : (" desc=" + description)));
            offset += len + 1;
          } while (baseOffset + fullLen > offset);
          do
          {
            baseOffset += toker.nextToken().length();
          } while (baseOffset < offset);
        }
      }
      String rating = (toker.hasMoreTokens() ? toker.nextToken() : "");
      byte prByte = (byte)0;
      String[] ers = null;
      String rated = null;
      if (rating.length() > 0)
      {
        if (rating.indexOf("PG-13") != -1)
          rated = "PG-13";
        else if (rating.indexOf("NC-17") != -1)
          rated = "NC-17";
        // Extract the portion of interest
        int pidx1 = rating.indexOf('(');
        int pidx2 = rating.indexOf(')');
        if (pidx1 != -1 && pidx2 > pidx1)
        {
          // Break down the rating information into the parts we care about.
          java.util.StringTokenizer ratToker = new java.util.StringTokenizer(rating.substring(pidx1 + 1, pidx2), "-;");
          if (ratToker.countTokens() > 1)
          {
            String tvRating = ratToker.nextToken() + ratToker.nextToken();
            for (int i = 1; i < sage.Airing.PR_NAMES.length; i++)
            {
              if (tvRating.equalsIgnoreCase(sage.Airing.PR_NAMES[i]))
              {
                prByte = (byte) i;
                break;
              }
            }
            java.util.ArrayList erList = Pooler.getPooledArrayList();
            while (ratToker.hasMoreTokens())
            {
              // Now extract the other specific rating information
              String currRate = ratToker.nextToken();
              if ("V".equals(currRate))
              {
                if (prByte == Airing.TVMA_VALUE)
                  erList.add("Graphic Violence");
                else if (prByte == Airing.TV14_VALUE)
                  erList.add("Violence");
                else
                  erList.add("Mild Violence");
              }
              else if ("S".equals(currRate))
              {
                if (prByte == Airing.TVMA_VALUE)
                  erList.add("Strong Sexual Content");
                else if (!erList.contains("Adult Situations"))
                  erList.add("Adult Situations");
              }
              else if ("D".equals(currRate))
              {
                if (!erList.contains("Adult Situations"))
                  erList.add("Adult Situations");
                if (!erList.contains("Language"))
                  erList.add("Language");
              }
              else if ("L".equals(currRate))
              {
                if (!erList.contains("Language"))
                  erList.add("Language");
              }
              else if (rated == null && ("G".equals(currRate) || "PG".equals(currRate) || "R".equals(currRate)))
                rated = currRate;
              else if (rated == null && "X".equals(currRate))
                rated = "AO";
              else if (rated == null && "NR".equals(currRate))
                rated = "NR";
            }
            if (!erList.isEmpty())
              ers = (String[]) erList.toArray(Pooler.EMPTY_STRING_ARRAY);
            Pooler.returnPooledArrayList(erList);
          }
        }
      }
      if (!"|".equals(rating) && toker.hasMoreTokens())
        toker.nextToken(); // delimiter
      String category = (toker.hasMoreTokens() ? toker.nextToken() : null);
      String subcategory = null;
      if ("|".equals(category))
        category = null;
      if (category != null)
      {
        int idx = category.indexOf('/');
        if (idx != -1)
        {
          subcategory = category.substring(idx + 1);
          category = category.substring(0, idx);
        }
      }
      title = title.trim();
      description = description.trim();
      String extID = "DT" + Math.abs((title + "-" + duration + "-" + description).hashCode());
      String[] categories = new String[(category == null ? 0 : 1) + (subcategory == null ? 0 : 1)];
      if (category != null)
        categories[0] = category;
      if (subcategory != null)
        categories[1] = subcategory;
      sage.Show myShow = sage.Wizard.getInstance().addShow(title, null, null, description, 0, categories, null, null, rated, ers, null, null, null,
          extID, language, 0, DBObject.MEDIA_MASK_TV, (short)0, (short)0, false, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0);
      //System.out.println("Added show:" + myShow);
      sage.Airing myAir = sage.Wizard.getInstance().addAiring(extID, stationID, startTime, duration*1000L, (byte)0, (byte)0, prByte,
          DBObject.MEDIA_MASK_TV);
      //System.out.println("Added air:" + myAir + " start=" + sage.Sage.dfFull(myAir.getStartTime()));
    }
    catch (RuntimeException re)
    {
      if (Sage.DBG) System.out.println("ERROR processing EPG data message \"" + msgString + "\" of:" + re);
      if (Sage.DBG) re.printStackTrace();
    }
  }

  public String getName()
  {
    return name;
  }

  public void setName(String s)
  {
    if (s == null) s = "";
    name = s;
    Sage.put(prefsRoot + EPG_NAME, name);
  }

  public final long getLastRun()
  {
    return lastRun;
  }

  public boolean usesPlugin()
  {
    return EPG.getInstance().hasEPGPlugin() && !Sage.getBoolean(prefsRoot + "disable_plugin", false);
  }

  public long getExpandedUntil()
  {
    return expandedUntil;
  }

  protected void setExpandedUntil(long x)
  {
    expandedUntil = x;
    Sage.putLong(prefsRoot + EXPANDED_UNTIL, expandedUntil);
  }

  public void reset()
  {
    setExpandedUntil(0);
    lastRun = 0;
    chanDownloadComplete = false;
    Sage.putLong(prefsRoot + LAST_RUN, 0);
  }

  public final void setEnabled(boolean x)
  {
    enabled = x;
    Sage.putBoolean(prefsRoot + ENABLED, enabled);
  }

  public final boolean getEnabled()
  {
    return enabled;
  }

  public final long getProviderID()
  {
    return providerID;
  }
  public final void setProviderID(long id)
  {
    Sage.putLong(prefsRoot + PROVIDER_ID, providerID = id);
  }

  public void abortUpdate()
  {
    abort = true;
  }
  public final void clearAbort()
  {
    abort = false;
  }

  // Formerly abstract
  protected boolean extractGuide(long guideTime)
  {
    int defaultStationID = Long.toString(providerID).hashCode();
    if (defaultStationID > 0)
      defaultStationID *= -1;
    boolean[] didAdd = new boolean[1];
    MMC mmc = MMC.getInstance();
    CaptureDeviceInput cdi = mmc.getInputForProvider(providerID);
    // We're no longer needed, we'll get cleaned up soon.
    if (cdi == null) return true;
    // Don't automatically insert the default channels for digital tuners; let them
    // be found from a scan instead
    if (cdi.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
    {
      // We still need to put empty maps in there so it thinks there's actually lineup
      // data for this source...and there is in the overrides.
      EPG.getInstance().setLineup(providerID, new java.util.HashMap());
      EPG.getInstance().setServiceLevels(providerID, new java.util.HashMap());
      return true;
    }
    int minChan = cdi.getMinChannel();
    int maxChan = cdi.getMaxChannel();
    if ((cdi.getType() != 1 || cdi.weirdRF()) && Sage.getBoolean("epg/dont_create_full_channel_list_for_non_tuner_inputs", true))
    {
      // Not a tv tuner, so just set the min and max equal so it only creates a single channel
      maxChan = minChan;
    }
    java.util.HashMap lineMap = new java.util.HashMap();
    for (int i = minChan; i <= maxChan; i++)
    {
      wiz.addChannel(cdi.getCrossName(), name, null, defaultStationID + i, 0, didAdd);
      if (didAdd[0])
        wiz.resetAirings(defaultStationID + i);
      lineMap.put(new Integer(defaultStationID + i), new String[] { Integer.toString(i) });
    }
    EPG.getInstance().setLineup(providerID, lineMap);
    EPG.getInstance().setServiceLevels(providerID, new java.util.HashMap());
    return true;
  }

  public final boolean expand()
  {
    String arf = "expand called on " + getName() + " at " + Sage.df() +
        " expandedUntil=" + Sage.df(expandedUntil) + " scannedUntil=" + Sage.df(scannedUntil);
    if (Sage.DBG) System.out.println(arf);
    errorText += arf + "\r\n";
    if (!enabled || (getTimeTillUpdate() > 0))
    {
      return true;
    }
    else if (abort)	return false;

    lastRun = Sage.time();
    Sage.putLong(prefsRoot + LAST_RUN, lastRun);

    // Reload this info so we cna figure out who needs a scan
    initDataScanInfo();

    if ((getTimeTillExpand() == 0) && !abort && enabled)
    {
      if (Sage.DBG) System.out.println("EPG Expanding " + getName() + " at " + Sage.df());
      errorText += "EPG Expanding " + getName() + " at " + Sage.df() + "\r\n";
      // We're expanded into the present at least
      boolean needsExpand = expandedUntil < Sage.time();
      if (needsExpand)
        setExpandedUntil(Math.max(Sage.time(), expandedUntil));

      // Log our request for a data scan if we need one
      if (dataScanAllowed && scannedUntil <= Sage.time() && !Sage.client)
      {
        CaptureDeviceInput[] cdis = MMC.getInstance().getInputsForProvider(providerID);
        boolean foundOne = false;
        for (int i = 0; i < cdis.length; i++)
        {
          if (cdis[i].getCaptureDevice().requestDataScan(cdis[i]))
            foundOne = true;
        }
        if (foundOne)
        {
          dataScanRequested = true;
          Seeker.getInstance().kick();
        }
      }

      if (Sage.client || !needsExpand || extractGuide(expandedUntil))
      {
        Sage.putBoolean(prefsRoot + CHAN_DOWNLOAD_COMPLETE, chanDownloadComplete = true);
        if (!abort && enabled && needsExpand) setExpandedUntil(expandedUntil + getGuideWidth());
        if (!abort && enabled && !Sage.client && scannedUntil <= Sage.time() && dataScanAllowed)
          doDataScan();
      }
      else
      {
        if (!abort && enabled && scannedUntil <= Sage.time() && dataScanAllowed)
          doDataScan();
        return false;
      }
    }
    return true;
  }

  // Formerly abstract
  protected long getGuideWidth()
  {
    return Sage.MILLIS_PER_DAY;
  }
  // Formerly abstract
  protected long getDesiredExpand()
  {
    return 0;
  }
  public final long getTimeTillUpdate()
  {
    return getTimeTillExpand();
  }

  public long getTimeTillExpand()
  {
    if (!enabled) return Long.MAX_VALUE;

    // We only factor in the scanning time if the device is available for scanning,
    // or if we haven't submitted the scan request to the device yet
    if (dataScanAllowed)
    {
      CaptureDeviceInput[] cdis = MMC.getInstance().getInputsForProvider(providerID);
      for (int i = 0; i < cdis.length; i++)
      {
        if ((cdis[i].isActive() && cdis[i].getCaptureDevice().isDataScanning()) || (cdis[i].doesDataScanning() && !dataScanRequested))
        {
          return Math.max(0, Math.min(expandedUntil - Sage.time(), scannedUntil - Sage.time()));
        }
      }
    }
    return Math.max(0, expandedUntil - Sage.time());
  }

  public String getErrorText()
  {
    return errorText;
  }

  protected void appendExceptionError(Throwable t)
  {
    java.io.StringWriter sw = new java.io.StringWriter();
    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
    if (Sage.DBG) t.printStackTrace(pw);
    pw.flush();
    errorText += sw.toString();
  }

  public final int getEPGSourceID()
  {
    return epgSourceID;
  }

  protected String getNewErrorText()
  {
    if (errTextPos < errorText.length())
    {
      String rv = errorText.substring(errTextPos);
      errTextPos = errorText.length();
      return rv;
    }
    else return "";
  }

  /*public int[] getUnavailableStations()
	{
        int[] rv = new int[unavailStations.size()];
		java.util.Iterator walker = unavailStations.iterator();
		int idx = 0;
		while (walker.hasNext())
			rv[idx++] = ((Integer) walker.next()).intValue();
		return rv;
	}*/

  public boolean canViewStation(int x)
  {
    return !unavailStations.contains(new Integer(x));
  }

  public boolean canViewStationOnChannel(int statID, String chanNum)
  {
    return !unavailStations.contains(new Integer(statID)) &&
        !unavailChanNums.contains(chanNum);
  }

  public void setCanViewStation(int stationID, boolean good)
  {
    int startSize = unavailStations.size();
    if (good)
      unavailStations.remove(new Integer(stationID));
    else
      unavailStations.add(new Integer(stationID));

    if (startSize != unavailStations.size())
    {
      Sage.put(prefsRoot + UNAVAILABLE_STATIONS, Sage.createCommaDelimSetString(unavailStations));
      if (good)
        setExpandedUntil(0);
      synchronized (updateIDMap)
      {
        if (updateIDMap.keySet().removeAll(unavailStations))
        {
          saveUpdateMap();
        }
      }
      NetworkClient.distributeRecursivePropertyChange(EPG.EPG_DATA_SOURCES_KEY);
      EPG.getInstance().resetViewableStationsCache();
    }
  }

  public void setCanViewStationOnChannel(int stationID, String chanNum, boolean good)
  {
    String[] possChans = EPG.getInstance().getChannels(providerID, stationID);
    int startSize1 = unavailStations.size();
    int startSize2 = unavailChanNums.size();
    if (possChans.length <= 1)
    {
      if (good)
        unavailStations.remove(new Integer(stationID));
      else
        unavailStations.add(new Integer(stationID));
      if (possChans.length == 1)
        unavailChanNums.remove(possChans[0]);
    }
    else if (good)
    {
      if (unavailStations.contains(new Integer(stationID)))
      {
        // All chans were bad for this station, now we're marking one of them good
        unavailStations.remove(new Integer(stationID));
        for (int i = 0; i < possChans.length; i++)
        {
          if (chanNum.equals(possChans[i]))
          {
            if (i != 0)
            {
              possChans[i] = possChans[0];
              possChans[0] = chanNum;
              // This changes the actual storage array, so we can just update it
              EPG.getInstance().setLineup(providerID, EPG.getInstance().getLineup(providerID));
            }
          }
          else
            unavailChanNums.add(possChans[i]);
        }
      }
      else // Just remove this one from the bad num list
        unavailChanNums.remove(chanNum);
    }
    else
    {
      if (!unavailStations.contains(new Integer(stationID)) && !unavailChanNums.contains(chanNum))
      {
        // Not all were bad before, they may be now so check it out
        int goodChanIdx = -1;
        for (int i = 0; i < possChans.length; i++)
        {
          if (!chanNum.equals(possChans[i]) && !unavailChanNums.contains(possChans[i]))
          {
            goodChanIdx = i;
            break;
          }
        }
        if (goodChanIdx != -1)
        {
          String swap = possChans[0];
          possChans[0] = possChans[goodChanIdx];
          possChans[goodChanIdx] = swap;
          unavailChanNums.add(chanNum);
        }
        else
        {
          for (int i = 0; i < possChans.length; i++)
            unavailChanNums.remove(possChans[i]);
          unavailStations.add(new Integer(stationID));
        }
      }
    }

    if (startSize1 != unavailStations.size())
    {
      Sage.put(prefsRoot + UNAVAILABLE_STATIONS, Sage.createCommaDelimSetString(unavailStations));
      if (good)
        setExpandedUntil(0);
      synchronized (updateIDMap)
      {
        if (updateIDMap.keySet().removeAll(unavailStations))
        {
          saveUpdateMap();
        }
      }
    }
    if (startSize2 != unavailChanNums.size())
      Sage.put(prefsRoot + UNAVAILABLE_CHANNEL_NUMS, Sage.createCommaDelimSetString(unavailChanNums));
    if (startSize1 != unavailStations.size() || startSize2 != unavailChanNums.size())
    {
      NetworkClient.distributeRecursivePropertyChange(EPG.EPG_DATA_SOURCES_KEY);
      EPG.getInstance().resetViewableStationsCache();
    }
  }

  public void applyServiceLevel(int newLevel)
  {
    if (appliedServiceLevel == newLevel)
      return;

    java.util.Set badStations = new java.util.HashSet();
    EPG epg = EPG.getInstance();
    int[] stations = epg.getAllStations(providerID);
    for (int i = 0; i < stations.length; i++)
    {
      if (epg.getServiceLevel(providerID, stations[i]) > newLevel)
        badStations.add(new Integer(stations[i]));
    }
    unavailStations = badStations;
    Sage.put(prefsRoot + UNAVAILABLE_STATIONS, Sage.createCommaDelimSetString(unavailStations));
    Sage.putInt(prefsRoot + APPLIED_SERVICE_LEVEL, appliedServiceLevel = newLevel);
    setExpandedUntil(0);
    synchronized (updateIDMap)
    {
      if (updateIDMap.keySet().removeAll(unavailStations))
      {
        saveUpdateMap();
      }
    }
    NetworkClient.distributeRecursivePropertyChange(EPG.EPG_DATA_SOURCES_KEY);
    EPG.getInstance().resetViewableStationsCache();
  }

  protected void saveUpdateMap()
  {
    java.util.Iterator walker = updateIDMap.entrySet().iterator();
    StringBuffer sb = new StringBuffer();
    while (walker.hasNext())
    {
      java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
      sb.append(ent.getKey());
      sb.append(',');
      sb.append(ent.getValue());
      sb.append(';');
    }
    Sage.put(prefsRoot + SERVER_UPDATE_ID, sb.toString());
  }

  public boolean isChanDownloadComplete()
  {
    return chanDownloadComplete;
  }

  public int getAppliedServiceLevel() { return appliedServiceLevel; }

  protected String prefsRoot;
  protected final int epgSourceID;
  protected Wizard wiz;
  protected boolean enabled;
  protected String errorText = "";
  private int errTextPos;
  protected String name;
  protected boolean abort;
  protected String dumpDir;
  protected long providerID;

  private long lastRun;
  private long expandedUntil;
  private long scannedUntil;

  protected int appliedServiceLevel;
  protected boolean chanDownloadComplete;
  protected java.util.Set unavailStations;
  protected java.util.Set unavailChanNums;
  protected java.util.Map updateIDMap;
  protected boolean dataScanAllowed;
  protected boolean dataScanRequested;
  /**
   * Called when removing data source from the EPG
   */
  public void destroySelf() {
    // do nothing by default.
  }

  /**
   * wake up this data source to perform any background updates
   */
  public void kick() {
    // do nothing by default
  }
  /*
   * Get the type of Maintenance that Wizard should apply
   * based on this EPG update
   */
  public MaintenanceType getRequiredMaintenanceType() {
    // Use the default daily timing for full maintenance.
    return MaintenanceType.NONE;
  }
}
