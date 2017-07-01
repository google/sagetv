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

import sage.epg.sd.SDRipper;

public class WarlockRipper extends EPGDataSource
{
  // 601 debug epg raw data to file:epg.log
  static final boolean EPG_DEBUG = false;

  public static final String SOURCE_NAME = "WarlockRipper";

  private static final String SERVER_ADDRESS = "server_address";
  public static final int STANDARD_TIMEOUT = 120000; // two minutes

  private static final long SLEEP_DELAY = 200;
  private static final long SLEEP_MOD = 200;

  private static long lastServerConnectTime = Sage.time(); // for tracking duration of connection failures
  private static final long FAILURE_DUR_FOR_NOTIFICATION = 2*Sage.MILLIS_PER_DAY;

  public WarlockRipper(int inID)
  {
    super(inID);

    Sage.put(prefsRoot + EPG.EPG_CLASS, SOURCE_NAME);
  }

  @Override
  public void reset()
  {
    super.reset();
    updateIDMap.clear();
  }

  // Return is an array of String[2], [0] is the id and [1] is the 'name'
  public static String[][] getProviders(String zipCode) throws EPGServerException
  {
    if (Sage.time() - zipLocalCacheTime > Sage.MILLIS_PER_DAY)
      zipProviderCache.clear();
    else if (zipProviderCache.containsKey(zipCode))
    {
      String[][] rv = zipProviderCache.get(zipCode);
      if (rv != null && rv.length > 0)
        return rv;
    }
    if (!doesHaveEpgLicense()) {
      System.out.println("You do not have a SageTV license, you cannot download EPG data.");
      throw new EPGServerException(EPG.EPG_SERVER_NO_KEY);
    }
    ServerConnectInfo sci = null;
    String cleanZip = zipCode.replace(' ', '-');
    String submitInfoStr = getSubmitInfo(Wizard.getInstance(), cleanZip);
    try
    {
      sci = connectToServer(submitInfoStr);

      // Send the request to get the providers
      sci.outStream.write(("GET_PROVIDERS " + cleanZip + "\r\n").getBytes());
      sci.outStream.flush();
      int numProvs = Integer.parseInt(readLineBytes(sci.inStream));

      // 601
      java.io.Writer wr = null;
      if (EPG_DEBUG) wr = new java.io.FileWriter(new java.io.File("epg.log"), true);
      if (EPG_DEBUG) wr.write("zip=" + zipCode + " count=" + numProvs + "\r\n");

      if (Sage.DBG) System.out.println("WarlockRipper got " + numProvs + " providers");
      String[][] rv = new String[numProvs][2];
      for (int i = 0; i < numProvs; i++)
      {
        String tempString = readLineBytes(sci.inStream);

        // 601
        if (EPG_DEBUG) wr.write(tempString + "\r\n");

        int idx = tempString.indexOf(' ');
        rv[i][0] = tempString.substring(0, idx);
        rv[i][1] = tempString.substring(idx + 1);
      }

      // 601
      if (EPG_DEBUG) wr.close();

      zipProviderCache.put(zipCode, rv);
      zipLocalCacheTime = Sage.time();
      return rv;
    }
    catch (java.io.IOException e)
    {
      if (Sage.DBG) System.out.println("Repeated errors in communicating with server :" + e);
      throw new EPGServerException(EPG.EPG_SERVER_CONNECTION_FAILURE);
      //e.printStackTrace();
    }
    finally
    {
      if (sci != null)
      {
        sci.close();
      }
    }
  }

  private static ServerConnectInfo connectToServer(String submitInfoStr) throws java.io.IOException, EPGServerException
  {
    try
    {
      String theAddress = Sage.get("asdf", null);
      if (theAddress == null || theAddress.length() == 0)
      {
        theAddress = "warlock.freytechnologies.com";
      }

      java.net.Socket sock = new java.net.Socket();
      sock.connect(new java.net.InetSocketAddress(theAddress, 7760), 10000);
      lastServerConnectTime = Sage.time();
      return new ServerConnectInfo(sock, submitInfoStr);
    }
    catch (java.io.IOException e)
    {
      if (!EPG.getInstance().hasEPGPlugin() && Sage.time() - lastServerConnectTime > FAILURE_DUR_FOR_NOTIFICATION)
      {
        if (Sage.DBG) System.out.println("Have not been able to connect to the EPG server for at least 2 days; post a system message about it.");
        lastServerConnectTime = Sage.time();
        sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createEPGUpdateFailureMsg());
      }
      throw e;
    }
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
    return "OK";
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
    return "OK";
  }

  public static String[][] getLocalMarkets() throws EPGServerException
  {
    if (Sage.time() - zipLocalCacheTime < Sage.MILLIS_PER_DAY && localMarketCache != null)
      return localMarketCache;
    if (!doesHaveEpgLicense()) {
      System.out.println("You do not have a SageTV license, you cannot download EPG data.");
      throw new EPGServerException(EPG.EPG_SERVER_NO_KEY);
    }
    ServerConnectInfo sci = null;
    String submitInfoStr = getSubmitInfo(Wizard.getInstance(), "0");
    try
    {
      sci = connectToServer(submitInfoStr);

      // Send the request to get the providers
      sci.outStream.write(("GET_LOCALS\r\n").getBytes());
      sci.outStream.flush();
      int numProvs = Integer.parseInt(readLineBytes(sci.inStream));
      // 601
      java.io.Writer wr = null;
      if (EPG_DEBUG) wr = new java.io.FileWriter(new java.io.File("epg.log"), true);
      if (EPG_DEBUG) wr.write("count=" + numProvs + "\r\n");

      if (Sage.DBG) System.out.println("WarlockRipper got " + numProvs + " local markets");
      String[][] rv = new String[numProvs][2];
      int numValid = 0;
      for (int i = 0; i < numProvs; i++)
      {
        String tempString = readLineBytes(sci.inStream);
        // 601
        if (EPG_DEBUG) wr.write(tempString + "\r\n");

        int idx = tempString.indexOf(' ');
        rv[numValid][0] = tempString.substring(0, idx);
        rv[numValid][1] = tempString.substring(idx + 1);
        numValid++;
      }
      if (numValid != numProvs)
      {
        String[][] rv2 = new String[numValid][2];
        for (int i = 0; i < numValid; i++)
        {
          rv2[i][0] = rv[i][0];
          rv2[i][1] = rv[i][1];
        }
        rv = rv2;
      }
      // 601
      if (EPG_DEBUG) wr.close();

      localMarketCache = rv;
      zipLocalCacheTime = Sage.time();
      return rv;
    }
    catch (java.io.IOException e)
    {
      if (Sage.DBG) System.out.println("Repeated errors in communicating with server :" + e);
      throw new EPGServerException(EPG.EPG_SERVER_CONNECTION_FAILURE);
      //e.printStackTrace();
    }
    finally
    {
      if (sci != null)
      {
        sci.close();
      }
    }
  }

  private static String convertSB(StringBuffer sb)
  {
    byte[] chars = new byte[sb.length()];
    for (int i = 0; i < chars.length; i++)
    {
      chars[i] |= (sb.charAt(i) & 0xFF);
    }
    try
    {
      return new String(chars, Sage.BYTE_CHARSET);
    }
    catch (java.io.UnsupportedEncodingException e)
    {
      throw new InternalError(Sage.BYTE_CHARSET + " charset not supported!");
    }
  }

  public static String getEPGLicenseKey() {
    String rv = null;
    java.io.File activKeyFile = new java.io.File("activkey");
    // Allow using the 'activkey' file on any platform, that way we can set the license key
    // from the UI as well since we can't set startup environment variables that way easily.
    // We also need to read from it first in case they override the environment variable on
    // Windows with a license key entered in the UI.
    if (activKeyFile.isFile())
      rv = IOUtils.getFileAsString(new java.io.File("activkey")).trim();
    if (Sage.WINDOWS_OS && (rv == null || rv.length() == 0))
      rv = System.getProperty("USERKEY");
    return rv;
  }

  // This method is called from the STV, don't change it's signature
  public static boolean doesHaveEpgLicense() {
    // NOTE: This of course is not the only way EPG clients are being validated...we're not that
    // dumb, ya know?  This is just to avoid people hitting the server who don't have a license.
    String key = getEPGLicenseKey();
    return (key != null && key.length() > 0 && !key.equals("NOKEY"));
  }

  // This may take some time due to the diskspace calculation so do it before we connect to the server
  public static String getSubmitInfo(Wizard wiz, String providerID)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("SUBMIT_INFO ");
    sb.append(Carny.getInstance().getWatchCount());
    sb.append(' ');
    sb.append(wiz.getCurrWizID());
    sb.append(' ');
    sb.append(Sage.getLong(SageTV.UPTIME, 0) + (Sage.time() - SageTV.startTime));
    sb.append(' ');
    // This is where we extract the license key information and submit it to the server
    sb.append(Sage.getFileSystemIdentifier(Sage.WINDOWS_OS ? "C:\\" : "/"));
    sb.append(' ');
    String key = getEPGLicenseKey();
    if (key == null || key.length() == 0)
      key = "NOKEY";
    sb.append(key);
    sb.append(' ');
    sb.append(wiz.getSize(Wizard.WATCH_CODE));
    sb.append(' ');
    sb.append(wiz.getSize(Wizard.WASTED_CODE));
    sb.append(' ');
    sb.append(wiz.getSize(Wizard.MEDIAFILE_CODE));
    sb.append(' ');
    long[] usedAndTotalSpace = SeekerSelector.getInstance().getUsedAndTotalVideoDiskspace();
    sb.append(usedAndTotalSpace[1]);
    sb.append(' ');
    sb.append(usedAndTotalSpace[0]);
    sb.append(' ');
    if (EPG.getInstance().hasEPGPlugin())
      sb.append("P");
    sb.append(providerID);
    sb.append(' ');
    sb.append(UIManager.SAGE.replace(' ', '_'));
    sb.append(' ');
    String[] videoDevs = MMC.getInstance().getCaptureDeviceNames();
    if (videoDevs.length == 0)
      sb.append("None");
    else
    {
      sb.append(videoDevs[0].replace(' ', '_'));
      for (int i = 1; i < videoDevs.length; i++)
      {
        sb.append(',');
        sb.append(videoDevs[i].replace(' ', '_'));
      }
    }
    sb.append("\r\n");
    return sb.toString();
  }

  public static void submitInfo(java.io.DataInputStream inStream, java.io.DataOutputStream outStream,
      String infoString) throws java.io.IOException, EPGServerException
  {
    outStream.write(infoString.getBytes());
    outStream.flush();
    String tempString = readLineBytes(inStream);
    if (!"OK".equals(tempString)) {
      if ("INVALIDKEY".equals(tempString)) {
        System.out.println("EPG server indicated the license key is invalid");
        throw new EPGServerException(EPG.EPG_SERVER_INVALID_KEY);
      }
      throw new java.io.IOException("ERROR Server returned response:" + tempString);
    }
  }

  // This is so we can track everything from everywhere since it can't be disabled (unless they override the
  // resolution of warlock.freytechnologies.com)
  @Override
  protected boolean extractGuide(long inGuideTime)
  {
    if (!doesHaveEpgLicense()) {
      if (usesPlugin())
      {
        return EPG.getInstance().pluginExtractGuide(Long.toString(providerID));
      }
      System.out.println("You do not have a SageTV license or an EPG plugin installed, you cannot download EPG data.");
      sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createEPGServerNoKeyMsg());
      return false;
    }
    guideTime = Sage.time(); //inGuideTime;

    ServerConnectInfo sci = null;
    String submitInfoStr = getSubmitInfo(wiz, Long.toString(providerID));
    try
    {
      String theAddress = Sage.get("asdf", null);
      if (theAddress == null || theAddress.length() == 0)
        theAddress = "warlock.freytechnologies.com";

      sci = connectToServer(submitInfoStr);
      sci.sock.setSoTimeout(STANDARD_TIMEOUT);
      String tempString;

      // Check for any server forced property modifications
      sci.outStream.write("GET_PROPERTY_CHANGES\r\n".getBytes());
      sci.outStream.flush();
      int numProps = Integer.parseInt(readLineBytes(sci.inStream));
      for (int i = 0; i < numProps; i++)
      {
        tempString = readLineBytes(sci.inStream);
        int idx = tempString.indexOf('=');
        if (idx != -1)
        {
          Sage.put(tempString.substring(0, idx), tempString.substring(idx + 1));
        }
      }

      if (SageTV.getSyncSystemClock())
      {
        long beforeRequestTime = Sage.time();
        sci.outStream.write("GET_TIME\r\n".getBytes());
        sci.outStream.flush();
        try
        {
          long sysTime = Long.parseLong(readLineBytes(sci.inStream));
          long afterRequestTime = Sage.time();
          sysTime += (afterRequestTime - beforeRequestTime)/2;
          // 7/12/03 Adjust it no matter what, too many people have clock problems
          //if (Math.abs(Sage.time() - sysTime) < Sage.MILLIS_PER_HR)
          {
            if (Math.abs(Sage.time() - sysTime) > 100)
            {
              if (Sage.DBG) System.out.println("Setting the system clock to be " + Sage.df(sysTime));
              Sage.setSystemTime(sysTime);
            }
          }
        }
        catch(Exception e){}
      }

      if (usesPlugin())
      {
        if (sci != null)
        {
          sci.close();
        }
        return EPG.getInstance().pluginExtractGuide(Long.toString(providerID));
      }

      // Send the request to get the channels
      sci.outStream.write(("GET_MULTICHANNELS " + providerID + "\r\n").getBytes());
      sci.outStream.flush();
      int numChans = Integer.parseInt(readLineBytes(sci.inStream));
      if (Sage.DBG) System.out.println("WarlockRipper got " + numChans + " channels");

      // 601
      java.io.Writer wr = null;
      if (EPG_DEBUG) wr = new java.io.FileWriter(new java.io.File("epg.log"), true);
      if (EPG_DEBUG) wr.write("pid=" + providerID + " count=" + numChans + "\r\n");

      // Include any additional stations the the user has added to their lineup
      // NOTE: If the EPG server decides we have no channels then we fill this with
      // all of our channels
      java.util.Map<Integer, String[]> overrideMap;
      if (numChans > 0)
        overrideMap = EPG.getInstance().getOverrideMap(getProviderID());
      else
      {
        System.out.println("WARNING--EPG Server no longer has channels on this lineup. The channel list will no longer be updated!");
        overrideMap = EPG.getInstance().getLineup(getProviderID());
        sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createLineupLostMsg(this));
      }
      if (overrideMap == null)
        overrideMap = new java.util.HashMap<Integer, String[]>();
      int numExtraChans = overrideMap.size();

      int[] stations = new int[numChans + numExtraChans];

      java.util.Iterator<Integer> walker = overrideMap.keySet().iterator();
      int extraChanNum = 0;
      while (walker.hasNext())
      {
        Integer val = walker.next();
        if (val != null && (!Sage.getBoolean("wizard/remove_airings_on_unviewable_channels2", true) || EPG.getInstance().canViewStation(val.intValue())))
        {
          stations[numChans + extraChanNum++] = val.intValue();
        }
      }

      // Also include all channels from any other lineups that are not Zap2it lineups so they get downloaded somewhere.
      // This may end up being redundant...but so is our channel downloading across lineups anyways.
      // On embedded we also need to find any other channels that may have been enabled on other lineups and do them in our update here
      // JS 8/22/2016: Added skipping SDRipper since Schedules Direct will take care of it's own lineups.
      CaptureDeviceInput[] conInputs = MMC.getInstance().getConfiguredInputs();
      for (int i = 0; i < conInputs.length; i++)
      {
        if (conInputs[i].getProviderID() != getProviderID())
        {
          // Check if this is a non-Zap2it lineup
          EPGDataSource otherDS = EPG.getInstance().getSourceForProviderID(conInputs[i].getProviderID());
          if (!(otherDS instanceof WarlockRipper) && !(otherDS instanceof SDRipper))
          {
            int[] altStations = EPG.getInstance().getAllStations(conInputs[i].getProviderID());
            for (int j = 0; j < altStations.length; j++)
            {
              if (!EPG.getInstance().canViewStation(altStations[j]))
                altStations[j] = 0;
            }
            int[] newStations = new int[stations.length + altStations.length];
            System.arraycopy(stations, 0, newStations, 0, stations.length);
            System.arraycopy(altStations, 0, newStations, stations.length, altStations.length);
            stations = newStations;
          }
        }
      }

      java.util.Set logosToDownload = new java.util.HashSet();
      java.util.Map<Integer, String[]> lineMap = new java.util.HashMap<Integer, String[]>();
      java.util.Map<Integer, Integer> serviceMap = new java.util.HashMap<Integer, Integer>();
      java.util.Map<Integer, String[]> physicalMap = null;
      boolean[] caddrv = new boolean[1];
      for (int i = 0; i < numChans; i++)
      {
        currData = readLineBytes(sci.inStream);

        // 601
        if (EPG_DEBUG) wr.write(currData + "\r\n");

        currDataPos = -1;
        int stationID = 0;
        String chanName = "";
        String longName = "";
        String networkName = "";
        int serviceLevel = 0;
        int numDiffChans = 0;
        String[] chanNums = null;
        String dtvChan = null;
        int logoMask = 0;
        try
        {
          stationID = Integer.parseInt(nextData());
          chanName = nextData();
          longName = nextData();
          networkName = nextData();
          serviceLevel = Integer.parseInt(nextData());
          numDiffChans = Integer.parseInt(nextData());
          chanNums = new String[numDiffChans];
          for (int j = 0; j < numDiffChans; j++)
            chanNums[j] = nextData();
          dtvChan = nextData();
          logoMask = Integer.parseInt(nextData());
        }catch (NumberFormatException e){}

        // Preserve the first priority channel# for this station
        String[] prevChans = EPG.getInstance().getChannels(providerID, stationID);
        if (prevChans != null && prevChans.length > 0 && chanNums != null && chanNums.length > 0 &&
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

        lineMap.put(new Integer(stationID), chanNums);
        serviceMap.put(new Integer(stationID), new Integer(serviceLevel));
        /*
                if ((networkName != null) && (networkName.length() > 0) &&
                    (EPG.getInstance().getLogo(networkName) == null))
                {
                    // Download the logo image, disabled for now
                    //logosToDownload.add(networkName);
                }*/

        Channel freshChan = wiz.addChannel(chanName, longName, networkName, stationID, logoMask, caddrv);
        if (caddrv[0])
        {
          wiz.resetAirings(stationID);
          if (chanDownloadComplete && !Sage.getBoolean("epg/enable_newly_added_channels", true))
          {
            if (Sage.DBG) System.out.println("Disabling newly added channel:" + freshChan);
            setCanViewStation(stationID, false);
          }
        }
        else if (chanDownloadComplete && prevChans.length == 1 && prevChans[0].equals("") && !Sage.getBoolean("epg/enable_newly_added_channels", true))
        {
          if (Sage.DBG) System.out.println("Disabling newly added channel:" + freshChan);
          setCanViewStation(stationID, false);
        }
        if (chanDownloadComplete && (caddrv[0] || (prevChans.length == 1 && prevChans[0].equals(""))))
        {
          // Channel download already done on this lineup and this channel is either new in the DB
          // or it didn't have a mapping before on this lineup; either case means it's 'new' on the lineup
          String chanString = "";
          for (int x = 0; chanNums != null && x < chanNums.length; x++)
          {
            chanString += chanNums[x];
            if (x < chanNums.length - 1)
              chanString += ',';
          }
          if (Sage.DBG) System.out.println("Sending system message for new channel on lineup newAdd=" + caddrv[0] + " prevChans=" + java.util.Arrays.asList(prevChans) + " chan=" + freshChan);
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
          physicalMap.put(new Integer(stationID), new String[] { dtvChan});

          // See if we already had this channel in our lineup through an auto-generated channel, and that channel was enabled. If it was,
          // then we disable the old channel and enable the new one. But we need to worry about ManualRecords linked to that old station ID that we're
          // disabling...or even worse if that channel is currently being viewed. Maybe we should just leave that for the user to handle themselves.
          /*                  try
                    {
                        short dtvChanShort = Short.parseShort(dtvChan);
                        if (dtvChanShort != 0)
                        {
                            freshChan.majorMinorDTVChan = dtvChanShort;
                            wiz.logUpdate(freshChan, Wizard.CHANNEL_CODE);
                        }
                    }
                    catch (Exception e){}*/
        }
      }

      // 601
      if (EPG_DEBUG) wr.close();

      // Don't set the lineup if the server didn't give us one!
      if (numChans > 0)
      {
        EPG.getInstance().setLineup(providerID, lineMap);
        EPG.getInstance().setServiceLevels(providerID, serviceMap);
        if (physicalMap != null)
          EPG.getInstance().setPhysicalLineup(providerID, physicalMap);
      }

      Sage.putBoolean(prefsRoot + CHAN_DOWNLOAD_COMPLETE, chanDownloadComplete = true);
      /*walker = logosToDownload.iterator();
            while (walker.hasNext())
            {
                String network = (String) walker.next();
                outStream.write(("GET_LOGO " + network + "\r\n").getBytes());
                outStream.flush();

                try
                {
                    int numBytes = Integer.parseInt(readLineBytes(inStream));
                    byte[] data = new byte[numBytes];
                    while (numBytes > 0)
                    {
                        int foo = inStream.read(data, data.length - numBytes, numBytes);
                        if (foo == -1) break;
                        numBytes -= foo;
                    }
                    EPG.getInstance().addLogo(data, network);
                }
                catch (NumberFormatException e){}
            }*/

      if (abort || !enabled) return false;

      sci.outStream.write("GET_UPDATE_ID\r\n".getBytes());
      sci.outStream.flush();
      int newUpdateID = Integer.parseInt(readLineBytes(sci.inStream));
      Wizard wiz = Wizard.getInstance();
      int totalObjectAdds = 0;
      for (int i = 0; i < stations.length; i++)
      {
        if (abort || !enabled) return false;

        int stationID = stations[i];
        if (stationID < 10000) continue; // non-TMS station ID
        Airing[] statAirs = wiz.getAirings(stationID, 0, Long.MAX_VALUE, false);
        int numAirsInDBForStation = 0;
        for (int statAirIdx = 0; statAirIdx < statAirs.length; statAirIdx++)
        {
          if (!wiz.isNoShow(statAirs[statAirIdx].showID) && statAirs[statAirIdx].time >
          Sage.time() - 2*Sage.MILLIS_PER_DAY)
            numAirsInDBForStation++;
        }
        Long currUpdateID = updateIDMap.get(new Integer(stationID));
        long serverUpdateID = (currUpdateID != null) ? currUpdateID.longValue() : 0;
        sci.outStream.write(("GET_UPDATED_SHOWS3 " + stationID + " " + guideTime + " " +
            14*Sage.MILLIS_PER_DAY + " " + serverUpdateID + " " + numAirsInDBForStation + "\r\n").getBytes());
        sci.outStream.flush();

        int numShows = Integer.parseInt(readLineBytes(sci.inStream));
        for (int j = 0; j < numShows; j++)
        {
          currData = readLineBytes(sci.inStream);
          currDataPos = -1;
          if (currData.startsWith("ERROR") || currData.startsWith("NOUPDATE"))
            continue;
          else
          {
            long showDuration = Long.parseLong(nextData());
            String title = nextData();
            String episode = nextData();
            String desc = nextData();
            String category = nextData();
            String subCategory = nextData();
            int numPeople = Integer.parseInt(nextData());
            String[] people = new String[numPeople];
            byte[] roles = new byte[numPeople];
            for (int k = 0; k < numPeople; k++)
            {
              people[k] = nextData();
              roles[k] = Byte.parseByte(nextData());
            }
            String rated = nextData();
            int numERs = Integer.parseInt(nextData());
            String[] ers = new String[numERs];
            for (int k = 0; k < numERs; k++)
              ers[k] = nextData();
            String year = nextData();
            String pr = nextData();
            int numBonuses = Integer.parseInt(nextData());
            String[] bonus = new String[numBonuses];
            for (int k = 0; k < numBonuses; k++)
              bonus[k] = nextData();
            String primeTitle = nextData();
            String extID = nextData();
            String language = nextData();
            long originalAirDate = Long.parseLong(nextData());
            String tempStr = nextData();
            short seasonNum = (tempStr != null && tempStr.length() > 0) ? (short)Integer.parseInt(tempStr) : 0;
            tempStr = nextData();
            short episodeNum = (tempStr != null && tempStr.length() > 0) ? (short)Integer.parseInt(tempStr) : 0;
            int numExtraCats = Integer.parseInt(nextData());
            String[] categories = new String[numExtraCats + (category.length() > 0 ? 1 : 0) + (subCategory.length() > 0 ? 1 : 0)];
            int catIndex = 0;
            if (category.length() > 0)
              categories[catIndex++] = category;
            if (subCategory.length() > 0)
              categories[catIndex++] = subCategory;
            for (int k = 0; k < numExtraCats; k++)
              categories[catIndex++] = nextData();
            boolean forcedUnique = "1".equals(nextData());
            byte photoCountTall = (byte)(Integer.parseInt(nextData()) & 0xFF);
            byte photoCountWide = (byte)(Integer.parseInt(nextData()) & 0xFF);
            byte photoThumbCountTall = (byte)(Integer.parseInt(nextData()) & 0xFF);
            byte photoThumbCountWide = (byte)(Integer.parseInt(nextData()) & 0xFF);
            byte posterCountTall = (byte)(Integer.parseInt(nextData()) & 0xFF);
            byte posterCountWide = (byte)(Integer.parseInt(nextData()) & 0xFF);
            byte posterThumbCountTall = (byte)(Integer.parseInt(nextData()) & 0xFF);
            byte posterThumbCountWide = (byte)(Integer.parseInt(nextData()) & 0xFF);
            // The original air date needs to be translated forward one day to align
            // with the correct day for North America
            if (originalAirDate > 0)
              originalAirDate += 24*MILLIS_PER_HOUR;
            wiz.addShow((title != null && title.length() > 0) ? title : primeTitle, "", episode, desc, showDuration, categories, people,
                roles, rated, ers, year, pr, bonus, extID, language, originalAirDate, DBObject.MEDIA_MASK_TV,
                seasonNum, episodeNum, forcedUnique, photoCountTall, photoCountWide, photoThumbCountTall, photoThumbCountWide,
                posterCountTall, posterCountWide, posterThumbCountTall, posterThumbCountWide);
            totalObjectAdds++;
            if (totalObjectAdds % SLEEP_MOD == 0)
            {
              // NOTE: Put a delay in here so we don't overload the CPU
              try{Thread.sleep(SLEEP_DELAY);}catch(Exception e){}
            }
          }
        }

        if (abort || !enabled) return false;

        sci.outStream.write(("GET_UPDATED_AIRINGS2 " + stationID + " " + guideTime + " " +
            14*Sage.MILLIS_PER_DAY + " " + serverUpdateID + " " + numAirsInDBForStation + "\r\n").getBytes());
        sci.outStream.flush();

        int numAirs = Integer.parseInt(readLineBytes(sci.inStream));
        String[] airStrings = new String[numAirs];
        // Extract all of the airing information first before we download new Shows,
        // otherwise we'll interleave and screw up the communication.
        for (int j = 0; j < numAirs; j++)
          airStrings[j] = readLineBytes(sci.inStream);
        for (int j = 0; j < numAirs; j++)
        {
          currData = airStrings[j];
          currDataPos = -1;
          String extID = nextData();
          long startTime = Long.parseLong(nextData());
          long duration = Long.parseLong(nextData());
          byte partByte = Byte.parseByte(nextData());
          Byte.parseByte(nextData()); // old miscByte
          byte prByte = Byte.parseByte(nextData());
          int misc = Integer.parseInt(nextData());
          Show currShow = wiz.getShowForExternalID(extID);
          if (currShow == null) // this shouldn't happen because we just got all these!
          {
            sci.outStream.write(("GET_SHOW2 " + extID + "\r\n").getBytes());
            sci.outStream.flush();
            currData = readLineBytes(sci.inStream);
            if (currData.startsWith("ERROR") || currData.startsWith("NOUPDATE"))
              continue;
            else
            {
              currDataPos = -1;
              long showDuration = Long.parseLong(nextData());
              String title = nextData();
              String episode = nextData();
              String desc = nextData();
              String category = nextData();
              String subCategory = nextData();
              int numPeople = Integer.parseInt(nextData());
              String[] people = new String[numPeople];
              byte[] roles = new byte[numPeople];
              for (int k = 0; k < numPeople; k++)
              {
                people[k] = nextData();
                roles[k] = Byte.parseByte(nextData());
              }
              String rated = nextData();
              int numERs = Integer.parseInt(nextData());
              String[] ers = new String[numERs];
              for (int k = 0; k < numERs; k++)
                ers[k] = nextData();
              String year = nextData();
              String pr = nextData();
              int numBonuses = Integer.parseInt(nextData());
              String[] bonus = new String[numBonuses];
              for (int k = 0; k < numBonuses; k++)
                bonus[k] = nextData();
              String primeTitle = nextData();
              nextData(); // extID
              String language = nextData();
              long originalAirDate = Long.parseLong(nextData());
              String tempStr = nextData();
              short seasonNum = (tempStr != null && tempStr.length() > 0) ? (short)Integer.parseInt(tempStr) : 0;
              tempStr = nextData();
              short episodeNum = (tempStr != null && tempStr.length() > 0) ? (short)Integer.parseInt(tempStr) : 0;
              int numExtraCats = Integer.parseInt(nextData());
              String[] categories = new String[numExtraCats + (category.length() > 0 ? 1 : 0) + (subCategory.length() > 0 ? 1 : 0)];
              int catIndex = 0;
              if (category.length() > 0)
                categories[catIndex++] = category;
              if (subCategory.length() > 0)
                categories[catIndex++] = subCategory;
              for (int k = 0; k < numExtraCats; k++)
                categories[catIndex++] = nextData();
              boolean forcedUnique = "1".equals(nextData());
              byte photoCountTall = (byte)(Integer.parseInt(nextData()) & 0xFF);
              byte photoCountWide = (byte)(Integer.parseInt(nextData()) & 0xFF);
              byte photoThumbCountTall = (byte)(Integer.parseInt(nextData()) & 0xFF);
              byte photoThumbCountWide = (byte)(Integer.parseInt(nextData()) & 0xFF);
              byte posterCountTall = (byte)(Integer.parseInt(nextData()) & 0xFF);
              byte posterCountWide = (byte)(Integer.parseInt(nextData()) & 0xFF);
              byte posterThumbCountTall = (byte)(Integer.parseInt(nextData()) & 0xFF);
              byte posterThumbCountWide = (byte)(Integer.parseInt(nextData()) & 0xFF);
              // The original air date needs to be translated forward one day to align
              // with the correct day for North America
              if (originalAirDate > 0)
                originalAirDate += 24*MILLIS_PER_HOUR;
              currShow = wiz.addShow(title, primeTitle, episode, desc, showDuration, categories,
                  people, roles, rated, ers, year, pr, bonus, extID, language, originalAirDate, DBObject.MEDIA_MASK_TV,
                  seasonNum, episodeNum, forcedUnique, photoCountTall, photoCountWide, photoThumbCountTall, photoThumbCountWide,
                  posterCountTall, posterCountWide, posterThumbCountTall, posterThumbCountWide);
              totalObjectAdds++;
              if (totalObjectAdds % SLEEP_MOD == 0)
              {
                // NOTE: Put a delay in here so we don't overload the CPU
                try{Thread.sleep(SLEEP_DELAY);}catch(Exception e){}
              }
            }
          }
          wiz.addAiring(currShow, stationID, startTime, duration, partByte, misc, prByte, DBObject.MEDIA_MASK_TV);
          totalObjectAdds++;
          if (totalObjectAdds % SLEEP_MOD == 0)
          {
            // NOTE: Put a delay in here so we don't overload the CPU
            try{Thread.sleep(SLEEP_DELAY);}catch(Exception e){}
          }
        }

        updateIDMap.put(new Integer(stationID), new Long(newUpdateID));

        // Do this after each station; so then next time we don't redo stuff we've already done
        StringBuilder sb = new StringBuilder();
        java.util.Iterator<java.util.Map.Entry<Integer, Long>> walker2;
        synchronized (updateIDMap)
        {
          walker2 = updateIDMap.entrySet().iterator();
          while (walker.hasNext())
          {
            java.util.Map.Entry ent = walker2.next();
            sb.append(ent.getKey());
            sb.append(',');
            sb.append(ent.getValue());
            sb.append(';');
          }
        }
        Sage.put(prefsRoot + SERVER_UPDATE_ID, sb.toString());
        wiz.compressDBIfNeeded();
      }

      // Download best bets info
      sci.outStream.write("GET_BESTBETS\r\n".getBytes());
      sci.outStream.flush();
      int numBets = Integer.parseInt(readLineBytes(sci.inStream));
      for (int i = 0; i < numBets; i++)
      {
        currData = readLineBytes(sci.inStream);
        currDataPos = -1;
        String extID = nextData();
        String airDate = nextData();
        String title = nextData();
        String network = nextData();
        String desc = nextData();
        String imgurl = nextData();
        wiz.addEditorial(extID, title, airDate, network, desc, imgurl);
      }

      // Download show cards info
      long showCardUpdateID = Sage.getLong("epg/showcards_update_id", 0);
      if (showCardUpdateID == 0 || wiz.getAllSeriesInfo().length == 0)
        sci.outStream.write("GET_SHOWCARDS\r\n".getBytes());
      else
        sci.outStream.write(("GET_SHOWCARDS " + showCardUpdateID + "\r\n").getBytes());
      sci.outStream.flush();
      String countInfo = readLineBytes(sci.inStream);
      int spaceIdx = countInfo.indexOf(' ');
      int numCards = Integer.parseInt(countInfo.substring(0, spaceIdx));
      for (int i = 0; i < numCards; i++)
      {
        currData = readLineBytes(sci.inStream);
        currDataPos = -1;
        nextData(); // show card ID
        String seriesIDStr = nextData();
        int seriesID = 0;
        try
        {
          seriesID = Integer.parseInt(seriesIDStr);
        }
        catch (NumberFormatException e)
        {
          System.out.println("ERROR w/ EPG data formatting for series ID:" + e + " data=" + currData);
          continue;
        }
        String title = nextData();
        nextData(); // titleUC
        String network = nextData();
        String description = nextData();
        if (description != null)
          description = description.replaceAll("\\<\\#\\$\\>", "\r\n");
        String history = nextData();
        if (history != null)
          history = history.replaceAll("\\<\\#\\$\\>", "\r\n");
        String premiereDate = nextData();
        String finaleDate = nextData();
        String airDow = nextData();
        String airHrMin = nextData();
        // Remove leading zero from the airing hour
        if (airHrMin != null && airHrMin.startsWith("0"))
          airHrMin = airHrMin.substring(1);
        String imgUrl = nextData();
        // Now read all the character info that's there
        java.util.ArrayList peopleCharVec = new java.util.ArrayList();
        String nextLastName = nextData();
        while (currDataPos != -1)
        {
          String nextFirstMidName = nextData();
          String character = nextData();
          if (nextLastName.length() + nextFirstMidName.length() > 0)
          {
            if (nextFirstMidName.length() > 0 && nextLastName.length() > 0)
              peopleCharVec.add(nextFirstMidName + ' ' + nextLastName);
            else
              peopleCharVec.add(nextFirstMidName + nextLastName);
            peopleCharVec.add(character);
          }
          nextLastName = nextData();
        }
        String[] people = new String[peopleCharVec.size()/2];
        String[] characters = new String[peopleCharVec.size()/2];
        for (int j = 0; j < peopleCharVec.size()/2; j++)
        {
          people[j] = peopleCharVec.get(2*j).toString();
          characters[j] = peopleCharVec.get(2*j + 1).toString();
        }
        wiz.addSeriesInfo(seriesID, title, network, description, history, premiereDate,
            finaleDate, airDow, airHrMin, imgUrl, people, characters);
      }
      Sage.put("epg/showcards_update_id", countInfo.substring(spaceIdx + 1));
    }
    catch (java.io.IOException e)
    {
      if (usesPlugin())
      {
        // Don't error out if we can't connect to the server and they're using a plugin,
        // that'd give us away that we do this to easy...
        return EPG.getInstance().pluginExtractGuide(Long.toString(providerID));
      }
      errorText += "Repeated errors in communicating with server :" + e + '\n';
      //appendExceptionError(e);
      return false;
    }
    catch (EPGServerException e) {
      if (EPG.EPG_SERVER_NO_KEY.equals(e.getMessage())) {
        sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createEPGServerNoKeyMsg());
      } else if (EPG.EPG_SERVER_INVALID_KEY.equals(e.getMessage())) {
        sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createEPGServerInvalidKeyMsg());
      } else {
        errorText += "Runtime error in processing data:" + e + '\n';
        appendExceptionError(e);
      }
      return false;
    }
    catch (Throwable t)
    {
      errorText += "Runtime error in processing data:" + t + '\n';
      appendExceptionError(t);
      return false;
    }
    finally
    {
      if (sci != null)
      {
        sci.close();
      }
    }

    return true;
  }

  private String nextData()
  {
    int oldDataPos = currDataPos;
    currDataPos = currData.indexOf('|', oldDataPos + 1);
    if (currDataPos < 0)
      return currData.substring(oldDataPos + 1);
    else
      return currData.substring(oldDataPos + 1, currDataPos);
  }


  // Reads a line from a binary stream, terminates on '\r\n' or '\n'.
  // Yeah, it should also terminate on \r itself, but the transfer
  // protocol this is used for requires \r\n termination anyways.
  public static String readLineBytes(java.io.DataInputStream inStream)
      throws java.io.InterruptedIOException, java.io.IOException {

    synchronized (inStream)
    {
      StringBuffer result = new StringBuffer();
      byte currByte = inStream.readByte();
      currByte = (byte) ((((currByte & 0x0F) << 4) | ((currByte & 0xF0) >> 4)) ^ 0x9A);
      while (true)
      {
        if (currByte == '\r')
        {
          currByte = inStream.readByte();
          currByte = (byte) ((((currByte & 0x0F) << 4) | ((currByte & 0xF0) >> 4)) ^ 0x9A);
          if (currByte == '\n')
          {
            return convertSB(result);
          }
          result.append('\r');
        }
        else if (currByte == '\n')
        {
          return convertSB(result);
        }
        result.append((char)currByte);
        currByte = inStream.readByte();
        currByte = (byte) ((((currByte & 0x0F) << 4) | ((currByte & 0xF0) >> 4)) ^ 0x9A);
      }
    }
  }

  @Override
  protected long getGuideWidth() { return 24*MILLIS_PER_HOUR; }

  private long guideTime;

  private String currData;
  private int currDataPos;

  private static long zipLocalCacheTime;
  private static java.util.Map<String, String[][]> zipProviderCache = new java.util.HashMap<String, String[][]>();
  private static String[][] localMarketCache;

  private static class ServerConnectInfo
  {
    public ServerConnectInfo(java.net.Socket inSock, String submitInfoStr) throws java.io.IOException, EPGServerException
    {
      sock = inSock;
      sock.setSoTimeout(15000);
      outStream = new java.io.DataOutputStream(new java.io.BufferedOutputStream(sock.getOutputStream()))
      {
        public void write(byte[] b) throws java.io.IOException
        {
          write(b, 0, b.length);
        }
        public void write(byte[] b, int off, int len) throws java.io.IOException
        {
          for (int i = off; i < off + len; i++)
          {
            b[i] = (byte) ((((b[i] & 0x0F) << 4) | ((b[i] & 0xF0) >> 4)) ^ 0xA9);
          }
          super.write(b, off, len);
        }
      };
      inStream = new java.io.DataInputStream(new java.io.BufferedInputStream(sock.getInputStream()));

      // First send the password.
      outStream.write((EPG.getInstance().getAccessCode() + "\r\n").getBytes());
      outStream.flush();
      String tempString = readLineBytes(inStream);
      if (!tempString.equals("OK"))
        throw new java.io.IOException("ERROR Server returned response:" + tempString);

      if (Sage.DBG) System.out.println("WarlockRipper logged in OK.");
      submitInfo(inStream, outStream, submitInfoStr);
    }
    public void close()
    {
      if (sock != null)
      {
        try { sock.close(); }catch(Exception e){}
      }
      if (outStream != null)
      {
        try { outStream.close(); }catch(Exception e){}
      }
      if (inStream != null)
      {
        try { inStream.close(); }catch(Exception e){}
      }
    }
    public java.net.Socket sock;
    public java.io.DataOutputStream outStream;
    public java.io.DataInputStream inStream;
  }
}
