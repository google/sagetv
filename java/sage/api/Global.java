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
package sage.api;

import sage.Airing;
import sage.BigBrother;
import sage.Burner;
import sage.Catbert;
import sage.Channel;
import sage.EPG;
import sage.EPGDataSource;
import sage.ExternalTuningManager;
import sage.FileDownloader;
import sage.FileTransfer;
import sage.SchedulerSelector;
import sage.SeekerSelector;
import sage.IOUtils;
import sage.LinuxUtils;
import sage.MMC;
import sage.ManualRecord;
import sage.MediaFile;
import sage.MiniClientSageRenderer;
import sage.NetworkClient;
import sage.NewStorageDeviceDetector;
import sage.Playlist;
import sage.Pooler;
import sage.PredefinedJEPFunction;
import sage.PseudoMenu;
import sage.Ripper;
import sage.SFIRTuner;
import sage.Sage;
import sage.SageConstants;
import sage.SageTV;
import sage.Scheduler;
import sage.Seeker;
import sage.UIClient;
import sage.UIManager;
import sage.UserEvent;
import sage.Widget;
import sage.Wizard;
import sage.ZComp;
import sage.ZDataTable;
import sage.ZLabel;
import sage.ZPseudoComp;

/**
 * System methods used for dealing with Sage specifics
 */
public class Global {
  private Global() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Global", "Refresh")
    {
      /**
       * Re-evaluates and redraws all UI elements on the current menu
       *
       * @declaration public void Refresh();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        if (stack.getUIMgrSafe() == null) return null;
        PseudoMenu currUI = stack.getUIMgrSafe().getCurrUI();
        if (currUI != null) currUI.refresh();
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "RefreshArea", new String[] {"WidgetName"})
    {
      /**
       * Finds the Widget on the current menu who's name matches the argument and then re-evaluates and redraws all UI elements, and their children for this Widget
       * @param WidgetName the name that a UI component's Widget must match, if null or zero length, then this is the same as calling {@link #Refresh Refresh()}
       *
       * @declaration public void RefreshArea(String WidgetName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        String s = getString(stack);
        PseudoMenu currUI = (stack.getUIMgrSafe() == null) ? null : stack.getUIMgrSafe().getCurrUI();
        if (currUI != null) currUI.refreshByWidgetName(s);
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "RefreshAreaForVariable", new String[] { "Name", "Value" })
    {
      /**
       * Searches all visible UI elements in the current menu finding any that have a variable with the specified name
       * matching the specified value. For any elements it finds, it will re-evaluate and redraws its UI elements, and its children
       * @param Name the name of the variable to match
       * @param Value the value of the variable to match on
       * @since 6.6
       *
       * @declaration public void RefreshAreaForVariable(String Name, Object Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object val = stack.pop();
        String name = getString(stack);
        PseudoMenu ui = (stack.getUIMgrSafe() == null) ? null : stack.getUIMgrSafe().getCurrUI();
        if (ui != null) ui.refreshByValue(name, val);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "Repaint")
    {
      /**
       * Redraws all UI elements on the current menu
       *
       * @declaration public void Repaint();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        if (stack.getUIMgrSafe() == null) return null;
        PseudoMenu currUI = stack.getUIMgrSafe().getCurrUI();
        if (currUI != null) currUI.repaint();
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "RepaintArea", new String[] {"WidgetName"})
    {
      /**
       * Finds the Widget on the current menu who's name matches the argument and then redraws all UI elements, and their children for this Widget
       * @param WidgetName the name that a UI component's Widget must match, if null or zero length, then this is the same as calling {@link #Repaint Repaint()}
       *
       * @declaration public void RepaintArea(String WidgetName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        String s = getString(stack);
        PseudoMenu currUI = (stack.getUIMgrSafe() == null) ? null : stack.getUIMgrSafe().getCurrUI();
        if (currUI != null) currUI.repaintByWidgetName(s);
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "AddStaticContext", 2, new String[]{"Name", "Value"})
    {
      /**
       * Sets the variable with the specified name to the specified value. This variable will
       * exist for the lifetime of the current Menu. When the next Menu transition occurs,
       * all values in the static context will be copied to the highest level context for the new Menu.
       * The static context is then cleared. This is the way you can pass an Object from one menu to
       * another, an example is showing the detailed info for an Airing by adding the Airing to the
       * static context and then transitioning to a Menu that will display the details for that Airing.
       *
       * @param Name the name to use for this 'static context' variable
       * @param Value the value to set this 'static context' variable to
       * @return the passed in Value
       *
       * @declaration public Object AddStaticContext(String Name, Object Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object rv = stack.pop();
        String varName = getString(stack);
        if (stack.getUIMgrSafe() != null)
          stack.getUIMgrSafe().getStaticContext().put(varName, rv);
        return rv;
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "AddGlobalContext", 2, new String[]{"Name", "Value"})
    {
      /**
       * Sets the variable with the specified name to the specified value. This variable
       * will exist for the lifetime of the SageTV application and is always in scope for all expression evaluation. This is a 'global variable'.
       * @param Name the name to use for this 'global context' variable
       * @param Value the value to set this 'global context' variable to
       * @return the passed in Value
       *
       * @declaration public Object AddGlobalContext(String Name, Object Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object rv = stack.pop();
        String varName = getString(stack);
        if (stack.getUIMgrSafe() != null)
          stack.getUIMgrSafe().getGlobalContext().put(varName, rv);
        return rv;
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "AreThereUnresolvedConflicts")
    {
      /**
       * Returns whether or not there are unresolved scheduling conflicts
       * @return true if there are unresolved scheduling conflicts, false otherwise
       *
       * @declaration public boolean AreThereUnresolvedConflicts();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(SchedulerSelector.getInstance().areThereDontKnows());
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsAsleep")
    {
      /**
       * Returns true if SageTV is in the Sleep state.
       * @return true if SageTV is in the Sleep state
       *
       * @declaration public boolean IsAsleep();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().isAsleep());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetTotalDiskspaceAvailable", true)
    {
      /**
       * Gets the total diskspace available for television recording by SageTV. This is the unused space in the video directories.
       * @return the total diskspace available for television recording by SageTV (in bytes)
       *
       * @declaration public long GetTotalDiskspaceAvailable();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(SeekerSelector.getInstance().getAvailVideoDiskspace());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetTotalLibraryDuration", true)
    {
      /**
       * Returns the total duration of all of the content in the media library
       * @return the total duration of all of the content in the media library in milliseconds
       *
       * @declaration public long GetTotalLibraryDuration();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(SeekerSelector.getInstance().getTotalImportedLibraryDuration());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetTotalVideoDuration", true)
    {
      /**
       * Returns the total duration of all of the recorded television content
       * @return the total duration of all of the recorded television content in milliseconds
       *
       * @declaration public long GetTotalVideoDuration();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(SeekerSelector.getInstance().getTotalVideoDuration());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUsedLibraryDiskspace", true)
    {
      /**
       * Returns the total number of bytes on disk used by the content in the imported video, picturees and music libraries
       * @return the total number of bytes on disk used by the content in the imported video, picturees and music libraries
       *
       * @declaration public long GetUsedLibraryDiskspace();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(SeekerSelector.getInstance().getUsedImportedLibraryDiskspace());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUsedVideoDiskspace", true)
    {
      /**
       * Returns the total number of bytes on disk used by recorded television content
       * @return the total number of bytes on disk used by recorded television content
       *
       * @declaration public long GetUsedVideoDiskspace();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(SeekerSelector.getInstance().getUsedVideoDiskspace());
      }});
    rft.put(new PredefinedJEPFunction("Global", "AreAiringsSameShow", 2, new String[] {"Airing1", "Airing2"})
    {
      /**
       * Returns true if two Airings represent the same Show. This means that they both are the same content (i.e. one is a repeat of the other)
       * Just because two Airings use the same Show object (in terms of object reference) doesn't mean this call will return true.
       * @param Airing1 one of the two Airing objects to compare
       * @param Airing2 the other Airing object to compare
       * @return true if two Airings represent the same Show, false otherwise
       *
       * @declaration public boolean AreAiringsSameShow(Airing Airing1, Airing Airing2);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(BigBrother.areSameShow(getAir(stack), getAir(stack), false));
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetLastEPGDownloadTime", true)
    {
      /**
       * Returns the last time that SageTV did an EPG update. The returned value uses the same units as java.lang.System.currentTimeMillis()
       * @return the last time that SageTV did an EPG update
       *
       * @declaration public long GetLastEPGDownloadTime();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(EPG.getInstance().getLastRun());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetLogo", 1, new String[] { "LogoName" })
    {
      /**
       * Gets an Image object that represents the specified name. This is normally used for Channel Logos.
       * @param LogoName the name to use to lookup the logo image
       * @return the image object that corresponds to the specified name if one exists, the null image otherwise
       *
       * @declaration public MetaImage GetLogo(String LogoName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return EPG.getInstance().getLogo(getString(stack), stack.getUIComponent());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetTimeUntilNextEPGDownload", true)
    {
      /**
       * The amount of time in milliseconds until SageTV will perform an EPG update again. Zero if no update is planned.
       * @return the amount of time in milliseconds until SageTV will perform an EPG update again. Zero if no update is planned
       *
       * @declaration public long GetTimeUntilNextEPGDownload();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(EPG.getInstance().getTimeTillUpdate());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetAllLineups")
    {
      /**
       * Returns the name of all the EPG lineups in the system
       * @return a list of all the names of the EPG lineups in the system
       *
       * @declaration public String[] GetAllLineups();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return EPG.getInstance().getAllProviderNames();
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsChannelDownloadComplete", new String[] { "Lineup" }, true)
    {
      /**
       * Returns true if the channel download has been completed on the specified lineup
       * @param Lineup the name of the Lineup
       * @return true if the channel download has been completed on the specified lineup, false otherwise
       *
       * @declaration public boolean IsChannelDownloadComplete(String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String lineup = getString(stack);
        return Boolean.valueOf(EPG.getInstance().getEPGDSForEPGDSName(lineup).isChanDownloadComplete());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetEPGProperty", new String[]{"EPGDataSource", "Property", "Parameter"}, true)
    {
      /**
       * Gets a property from a specific EPG data source with an optional parameter
       * @param EPGDataSource the name of the EPG data source
       * @param Property the property name to get
       * @param Parameter optional parameter
       * @return the value of the requested property
       * @since 9.0
       *
       * @declaration public Object GetEPGProperty(String EPGDataSource, String Property, String Parameter);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String parameter = getString(stack);
        String property = getString(stack);
        String dataSource = getString(stack);

        try
        {
          return EPG.getProperty(dataSource, property, parameter);
        }
        catch (sage.EPGServerException e)
        {
          return e.getMessage();
        }
      }});
    rft.put(new PredefinedJEPFunction("Global", "SetEPGProperty", new String[]{"EPGDataSource", "Property", "Value"}, true)
    {
      /**
       * Sets a property for a specific EPG data source to the provided value
       * @param EPGDataSource the name of the EPG data source
       * @param Property the property name to set
       * @param Value the value to set
       * @return result of setting the property
       * @since 9.0
       *
       * @declaration public Object SetEPGProperty(String EPGDataSource, String Property, String Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String value = getString(stack);
        String property = getString(stack);
        String dataSource = getString(stack);

        try
        {
          return EPG.setProperty(dataSource, property, value);
        }
        catch (sage.EPGServerException e)
        {
          return e.getMessage();
        }
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetLocalMarketsFromEPGServer", true)
    {
      /**
       * Gets a list of all the possible United States local broadcast markets from the EPG server
       * @return a String[] of all the possible United States local broadcast markets from the EPG server or a String error message of "NO_KEY", "INVALID_KEY", or "CONNECTION_FAILURE"
       *
       * @declaration public Object GetLocalMarketsFromEPGServer();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        try {
          return EPG.getInstance().getLocalMarketsAndCacheNames();
        } catch (sage.EPGServerException e) {
          System.out.println("ERROR communicating with EPG server of: " + e);
          return e.getMessage();
        }
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetLineupsForZipCodeFromEPGServer", 1, new String[]{"ZipCode"}, true)
    {
      /**
       * Gets a list from the EPG server of all the possible EPG Lineups that are available in a given zip code
       * @param ZipCode the zip code to search for EPG lineups in
       * @return a String[] from the EPG server of all the possible EPG lineups in the specified zip code or a String error message of "NO_KEY", "INVALID_KEY", or "CONNECTION_FAILURE"
       *
       * @declaration public Object GetLineupsForZipCodeFromEPGServer(String ZipCode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        try {
          return EPG.getInstance().getProvidersAndCacheNames(getString(stack));
        } catch (sage.EPGServerException e) {
          System.out.println("ERROR communicating with EPG server of: " + e);
          return e.getMessage();
        }
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetCurrentlyRecordingMediaFiles", true)
    {
      /**
       * Returns a list of all of the files that SageTV is currently recording
       * @return a list of all of the files that SageTV is currently recording
       *
       * @declaration public MediaFile[] GetCurrentlyRecordingMediaFiles();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getCurrRecordFiles();
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetSuggestedIntelligentRecordings", true)
    {
      /**
       * Gets a list of all of the Airings that SageTV would record if Intelligent Recording was enabled.
       * This does not include Manual Recordings or Favorites.
       * @return a list of all of the Airings that SageTV would record if Intelligent Recording was enabled.
       * @since 4.1
       *
       * @declaration public Airing[] GetSuggestedIntelligentRecordings();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getIRScheduledAirings();
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetScheduledRecordings", true)
    {
      /**
       * Gets a list of all of the Airings that SageTV is planning to record in the future
       * @return a list of all of the Airings that SageTV is planning to record in the future
       *
       * @declaration public Airing[] GetScheduledRecordings();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getInterleavedScheduledAirings();
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetScheduledRecordingsForDevice", 1, new String[] { "CaptureDevice"}, true)
    {
      /**
       * Gets a list of all of the Airings that SageTV is planning to record in the future on the specified CaptureDevice
       * @param CaptureDevice the name of a CaptureDevice for SageTV to get the scheduled recordings for
       * @return a list of all of the Airings that SageTV is planning to record in the future on the specified CaptureDevice
       *
       * @declaration public Airing[] GetScheduledRecordingsForDevice(String CaptureDevice);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getScheduledAiringsForSource(getString(stack));
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetScheduledRecordingsForTime", 2, new String[] { "StartTime", "StopTime" }, true)
    {
      /**
       * Gets a list of all of the Airings that SageTV is planning on recording during the specified time span
       * @param StartTime the starting time to get all of the scheduled recordings for
       * @param StopTime the ending time to get all of the scheduled recordings for
       * @return a list of all of the Airings that SageTV is planning on recording during the specified start-stop time window
       *
       * @declaration public Airing[] GetScheduledRecordingsForTime(long StartTime, long StopTime);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long stop = getLong(stack);
        return SeekerSelector.getInstance().getInterleavedScheduledAirings(getLong(stack), stop);
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetScheduledRecordingsForDeviceForTime", 3, new String[] { "CaptureDevice", "StartTime", "StopTime"}, true)
    {
      /**
       * Gets a list of all of the Airings that SageTV is planning on recording during the specified time span on a specified CaptureDevice
       * @param CaptureDevice the name of a CaptureDevice for SageTV to get the scheduled recordings for
       * @param StartTime the starting time to get all of the scheduled recordings for
       * @param StopTime the ending time to get all of the scheduled recordings for
       * @return a list of all of the Airings that SageTV is planning on recording during the specified start-stop time window on the specified CaptureDevice
       *
       * @declaration public Airing[] GetScheduledRecordingsForDeviceForTime(String CaptureDevice, long StartTime, long StopTime);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long stop = getLong(stack);
        long start = getLong(stack);
        return SeekerSelector.getInstance().getScheduledAiringsForSource(getString(stack), start, stop);
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetRecentlyWatched", 1, new String[] {"DurationToLookBack"}, true)
    {
      /**
       * Gets a list of all of the Airings that have been watched within the specified amount of time
       * @param DurationToLookBack tha amount of time in milliseconds that should be searched for watched Airings
       * @return a list of all of the Airings that have been watched within the specified amount of time
       *
       * @declaration public Airing[] GetRecentlyWatched(long DurationToLookBack);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getRecentWatches(Sage.time() - getLong(stack));
      }});
    rft.put(new PredefinedJEPFunction("Global", "RunLibraryImportScan", 1, new String[] {"WaitUntilDone"}, true)
    {
      /**
       * Tells SageTV to run a library import scan now. This will scan all of the library import directories for new content.
       * @param WaitUntilDone if this parameter is true then this call will not return until SageTV has finished the import scan, otherwise it returns immediately
       *
       * @declaration public void RunLibraryImportScan(boolean WaitUntilDone);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeekerSelector.getInstance().scanLibrary(Catbert.evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "Exit")
    {
      /**
       * Causes the SageTV application to terminate. If this is called from the non-primary UI session then it will terminate the UI session it is called from.
       *
       * @declaration public void Exit();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgrSafe();
        if (uiMgr.getLocalUIClientName().equals(Seeker.LOCAL_PROCESS_CLIENT))
          SageTV.exit();
        else
          uiMgr.goodbye();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "SageCommand", -1, new String[]{"Command"})
    {
      /**
       * Causes the corresponding Sage Command to be executed just like it would be if the user performed it.
       * These are always done asynchronously with the exception of "Back" and "Forward" since those effect
       * the current UI and may be used to override a menu transition.
       * @param Command the name of the Sage Command to execute
       *
       * @declaration public void SageCommand(String Command);
       */

      /**
       * Causes the corresponding Sage Command to be executed just like it would be if the user performed it.
       * These are always done asynchronously with the exception of "Back" and "Forward" since those effect
       * the current UI and may be used to override a menu transition. The payload will be passed along and
       * be accessible in the listener object in the STV.
       * @param Command the name of the Sage Command to execute
       * @param Payload an object to pass along with the event
       * @since 8.1
       *
       * @declaration public void SageCommand(String Command, Object Payload);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object payload = null;
        if (curNumberOfParameters == 2)
          payload = stack.pop();
        String cmd = getString(stack);
        stack.getUIMgrSafe().doUserEvent2(UserEvent.getEvtCodeForName(cmd), payload); return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "RemoveUnusedLineups")
    {
      /**
       * Removes all of the lineups from SageTV's configuration that are no longer in use by a CaptureDevice
       * @return true if any lineups were removed, false otherwise
       *
       * @declaration public boolean RemoveUnusedLineups();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        // Execute this on the server if we're the client; but then clean us up locally since removals
        // aren't propogated in the property exchange. But do it first so anything the server has that we don't
        // gets propogated back down correctly
        boolean killedAny = false;
        EPGDataSource[] allDS = EPG.getInstance().getDataSources();
        for (int i = 0; i < allDS.length; i++)
        {
          if (!MMC.getInstance().isProviderUsed(allDS[i].getProviderID()))
          {
            EPG.getInstance().removeDataSource(allDS[i]);
            killedAny = true;
          }
        }
        EPG.getInstance().kick();
        SchedulerSelector.getInstance().kick(false);
        if (Sage.client)
        {
          return makeNetworkedCall(stack);
        }
        return Boolean.valueOf(killedAny);
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetApplicationLaunchTime")
    {
      /**
       * Returns the time that the SageTV application was instantiated.
       * @return the time that the SageTV application was instantiated
       *
       * @declaration public long GetApplicationLaunchTime();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(SageTV.getInstanceStartTime());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetFocusContext")
    {
      /**
       * Copies all variables from the currently focused UI element's variable context to the current context
       * of the calling Action. Any variables that are in a common parent hierarchy are not copied as part of this.
       * @return true if there was a focused element and it's context was copied, false if there was no focused element found
       *
       * @declaration public boolean GetFocusContext();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        throw new IllegalArgumentException("GetFocusContext() can only be used by itself");
      }});
    rft.put(new PredefinedJEPFunction("Global", "Fork")
    {
      /**
       * Spawns a new thread of execution that will be used for further processing of Widget chain. This is analagous to 'forking' a thread
       * to continue execution of a widget chain in parallel to the current system execution.
       *
       * @declaration public void Fork();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        throw new IllegalArgumentException("Fork() can only be used by itself");
      }});
    rft.put(new PredefinedJEPFunction("Global", "TransmitCommandUsingInfraredTuningPlugin", new String[] {"TuningPlugin","TuningPluginPort","RemoteName", "CommandName", "RepeatFactor"}, true)
    {
      /**
       * Causes SageTV to instruct the specified tuning plugin to send a command
       * @param TuningPlugin the name of the tuning plugin that should send the command
       * @param TuningPluginPort the name of the port the specified tuning plugin is on
       * @param RemoteName the name of the 'Remote Control' that should be used to send the command
       * @param CommandName the name of the command to be sent
       * @param RepeatFactor the 'repeat factor' to use for sending the infrared command, 2 is the default
       *
       * @declaration public void TransmitCommandUsingInfraredTuningPlugin(String TuningPlugin, int TuningPluginPort, String RemoteName, String CommandName, int RepeatFactor);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int rep = getInt(stack);
        String cmd = getString(stack);
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
          tun.playCommand(remoteName, cmd, rep, false);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "DebugLog", 1, new String[] {"DebugString"})
    {
      /**
       * Prints out a message to SageTV's debug log
       * @param DebugString the string to print out
       *
       * @declaration public void DebugLog(String DebugString);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        if (Sage.DBG) System.out.println(s);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "CloseOptionsMenu", -1, new String[]{"WidgetName", "WaitForClose"})
    {
      /**
       * Closes the last OptionsMenu that was shown and continues execution of the Action
       * chain that spawned that OptionsMenu at the sibling after this OptionsMenu. The
       * feature of continuing execution can be used to prompt the user with a question
       * through an OptionsMenu before continuing on execution of an Action chain.
       * An example is confirming something they just did before it actually gets done.
       *
       * @declaration public void CloseOptionsMenu();
       */

      /**
       * Closes the OptionsMenu who's Widget name (or themed Widget name) matches that of the argument and continues execution of the Action
       * chain that spawned that OptionsMenu at the sibling after this OptionsMenu. The
       * feature of continuing execution can be used to prompt the user with a question
       * through an OptionsMenu before continuing on execution of an Action chain.
       * An example is confirming something they just did before it actually gets done.
       * If this OptionsMenu is not on top, then it cannot be closed until it becomes the one on top.
       * @param WidgetName the name of the Widget for the OptionsMenu or the themed OptionsMenu source for that Widget that should be closed
       * @param WaitForClose if true then this call will not return until the specified OptionsMenu target is closed
       * @return returns true if the target OptionsMenu is not open or if it was closed as a result of this call, will only return false if waitForClose is true and the target OptionsMenu is not on top
       * @since 7.1
       *
       * @declaration public boolean CloseOptionsMenu(String WidgetName, boolean WaitForClose);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 2)
        {
          boolean waitForClose = getBool(stack);
          String widgName = getString(stack);
          return stack.getUIMgrSafe().removePopup(widgName, waitForClose) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
        {
          stack.getUIMgrSafe().removePopup(); return null;
        }
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsOptionsMenuOpen")
    {
      /**
       * Returns true if an options menu is currently open in the UI.
       * @return true if an options menu is currently open in the UI, false otherwise
       * @since 7.1
       *
       * @declaration public boolean IsOptionsMenuOpen();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr != null)
        {
          PseudoMenu currUI = uiMgr.getCurrUI();
          return (currUI != null && currUI.hasPopup()) ? Boolean.TRUE : Boolean.FALSE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetSageCommandNames")
    {
      /**
       * Gets the names of all of the SageTV commands that are available in the system
       * @return a list of the names of all of the SageTV commands that are available in the system
       *
       * @declaration public java.util.Vector GetSageCommandNames();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.util.Vector rv = new java.util.Vector(java.util.Arrays.asList(UserEvent.PRETTY_UENAMES));
        rv.remove(0); // ?
        rv.remove(0); // ?
        rv.remove(rv.size() - 1); // Anything
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Global", "ApplyServiceLevelToLineup", 2, new String[] { "Lineup", "ServiceLevel" }, true)
    {
      /**
       * Applies a service level to a given lineup. This is specific to the EPG data source that is being used.
       * @param Lineup the name of the EPG lineup to modify
       * @param ServiceLevel the service level to apply to the lineup
       *
       * @declaration public void ApplyServiceLevelToLineup(String Lineup, int ServiceLevel);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int lev = getInt(stack);
        EPGDataSource ds = EPG.getInstance().getEPGDSForEPGDSName(getString(stack));
        if (ds != null)
          ds.applyServiceLevel(lev);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "SetFocusForVariable", new String[] { "Name", "Value" })
    {
      /**
       * Searches all UI elements in the current menu until it finds one that has a variable with the specified name
       * matching the specified value. Once it finds this UI element, it gives it the focus.  If scrolling of a table
       * or panel is necessary to make it visible, that scrolling will occur.
       * @param Name the name of the variable to match
       * @param Value the value of the variable to match on
       * @return true if the focus was set
       *
       * @declaration public boolean SetFocusForVariable(String Name, Object Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object val = stack.pop();
        String name = getString(stack);
        PseudoMenu ui = stack.getUIMgrSafe().getCurrUI();
        return Boolean.valueOf(ui.setFocusByValue(name, val));
      }});
    rft.put(new PredefinedJEPFunction("Global", "EnsureVisibilityForVariable", new String[] { "Name", "Value", "DisplayIndex" })
    {
      /**
       * Searches the tables in the current menu for a cell that matches the passed in name/value pair;
       * if found it will ensure that it is currently visible in the UI at the specified visual index of the table.
       * The name should match the name of the TableComponent-Cell. For example if the TableComponent with a Cell
       * subtype had a name of "File" and listed all of the MediaFiles; then calling
       * EnsureVisibilityForVariable("File", MyMediaFile, 1) would cause the table to scroll so that the second row
       * showed the cell whose File value corresponded to MyMediaFile.  This does NOT change the focus,
       * if you wish to scroll a table and shift the focus then use SetFocusForVariable
       *
       * @param Name the name of the variable to match on
       * @param Value the value of the variable to match
       * @param DisplayIndex 0-based value signifying which visually displayed row or column should show the corresonding cell
       * @return true if there was a matching variable found
       *
       * @declaration public boolean EnsureVisibilityForVariable(String Name, Object Value, int DisplayIndex);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int dispIndex = getInt(stack);
        Object val = stack.pop();
        String name = getString(stack);
        PseudoMenu ui = stack.getUIMgrSafe().getCurrUI();
        return Boolean.valueOf(ui.ensureVisbilityForValue(name, val, dispIndex));
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetVariableFromContext", new String[] { "MatchName", "MatchValue", "LookupName" })
    {
      /**
       * Searches all UI elements in the current menu until it finds one that has a variable with the specified name
       * matching the specified value. Once it finds this UI element, it then does a lookup of the other
       * specified variable in that UI element's context and returns that value
       * @param MatchName the name of the variable to match
       * @param MatchValue the value of the variable to match on
       * @param LookupName the variable to lookup in the matching context
       * @return the value of the variable
       * @since 7.0
       *
       * @declaration public Object GetVariableFromContext(String MatchName, Object MatchValue, String LookupName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String lookupName = getString(stack);
        Object val = stack.pop();
        String name = getString(stack);
        PseudoMenu ui = stack.getUIMgrSafe().getCurrUI();
        // Look for a visible one first; if we don't find it, then also accept non-visible ones as well
        if (ui != null)
        {
          ZPseudoComp rvContext = ui.getCompForVariable(name, val, stack.getUIComponent());
          if (rvContext != null)
          {
            if ("Focused".equals(lookupName))
              return Boolean.valueOf(rvContext.isFocused());
            else if ("FocusedChild".equals(lookupName))
              return Boolean.valueOf(rvContext.doesHierarchyHaveFocus());
            else
              return rvContext.getRelatedContext().get(lookupName);
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetVisibilityForVariable", new String[] { "MatchName", "MatchValue" })
    {
      /**
       * Searches all UI elements in the current menu until it finds one that has a variable with the specified name
       * matching the specified value. Once it finds this UI element, it will return true if that UI element is currently
       * visible and false otherwise
       * @param MatchName the name of the variable to match
       * @param MatchValue the value of the variable to match on
       * @return true if the found component is visible, false otherwise
       * @since 7.0
       *
       * @declaration public boolean GetVisibilityForVariable(String MatchName, Object MatchValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        /*			 Object val = stack.pop();
			 String name = getString(stack);
			 PseudoMenu ui = stack.getUIMgrSafe().getCurrUI();
			 return Boolean.valueOf(ui.getPassesConditionalForVariable(name, val));*/
        Object val = stack.pop();
        String name = getString(stack);
        if (stack.getUIMgr() == null) return null;
        PseudoMenu ui = stack.getUIMgr().getCurrUI();
        // Look for a visible one first; if we don't find it, then also accept non-visible ones as well
        if (ui != null)
        {
          ZPseudoComp uiComp = ui.getCompForVariable(name, val, stack.getUIComponent());
          return (uiComp != null && uiComp.passesUpwardConditional() && uiComp.getNumKids() > 0) ? Boolean.TRUE : Boolean.FALSE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIComponentForVariable", new String[] { "MatchName", "MatchValue" })
    {
      /**
       * Searches all UI elements in the current menu until it finds one that has a variable with the specified name
       * matching the specified value. It then returns on Object representing that UI component. The search method
       * prefers visible components over hidden ones.
       * @param MatchName the name of the variable to match
       * @param MatchValue the value of the variable to match on
       * @return the UI component with the specified variable set
       * @since 7.0
       *
       * @declaration public Object GetUIComponentForVariable(String MatchName, Object MatchValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object val = stack.pop();
        String name = getString(stack);
        if (stack.getUIMgr() == null) return null;
        PseudoMenu ui = stack.getUIMgr().getCurrUI();
        // Look for a visible one first; if we don't find it, then also accept non-visible ones as well
        if (ui != null)
        {
          return ui.getCompForVariable(name, val, stack.getUIComponent());
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIComponentContext")
    {
      /**
       * Returns the UI component that this execution originated from. For
       * 'green' process chains; this will correspond to the UI component that received the event. For 'blue'
       * UI chains; this will correspond to the UI component who's conditionality is being determined or who's data
       * is being evaluated. This will be null if there is no UI context; such as for non-UI hooks and calls made from
       * Java directly.
       * @return the UIComponent that corresponds to the UI context used for the current evaluation, null if there is no context
       * @since 7.0
       *
       * @declaration public Object GetUIComponentContext();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIComponent();
      }});
    rft.put(new PredefinedJEPFunction("Global", "SetFocusToUIComponent", new String[] { "UIComponent" })
    {
      /**
       * Sets focus in the specified UI component. The UIComponent must be focusable for this to succeed
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return true if setting the focus in the specified component succeeded, false otherwise
       * @since 7.0
       *
       * @declaration public boolean SetFocusToUIComponent(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null && uiComp.isFocusable())
        {
          return uiComp.setFocus() ? Boolean.TRUE : Boolean.FALSE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetTableFocusedVisibleRow", new String[] { "UIComponent" })
    {
      /**
       * Gets the row number that currently has focus in the specified table. The argument may be the table itself; or a component between
       * the table and the focused component.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the row number (1-based) which is currently focused in the table, -1 is returned if the value can't be resolved
       * @since 7.0
       *
       * @declaration public int GetTableFocusedVisibleRow(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          // Now get the child focused component if it exists
          //				 if (uiComp.doesHierarchyHaveFocus())
          {
            ZComp focusOwner = uiComp.getLastFocusedChild();
            if (focusOwner == null)
              return new Integer(-1);
            if (focusOwner instanceof ZPseudoComp)
            {
              ZPseudoComp focusComp = (ZPseudoComp) focusOwner;
              // This depends upon the orientation of the table
              ZDataTable tabParent = focusComp.getTableParent();
              boolean vert = true;
              if (tabParent != null && tabParent.getTableDimensions() == ZDataTable.HORIZONTAL_DIMENSION)
                vert = false;
              // Now calculate its visible row position
              Object o1 = focusComp.getRelatedContext().safeLookup("TableRow");
              Object o2 = focusComp.getRelatedContext().safeLookup("NumColsPerPage");
              Object o3 = focusComp.getRelatedContext().safeLookup("VScrollIndex");
              Object o4 = focusComp.getRelatedContext().safeLookup("NumRowsPerPage");
              Object o5 = focusComp.getRelatedContext().safeLookup("HScrollIndex");
              int tablePosition = o1 != null ? (Integer.parseInt(o1.toString()) - 1) : 0;
              int colsPerPage = o2 != null ? Math.max(1, Integer.parseInt(o2.toString())) : 1;
              int vscrollIndex = o3 != null ? (Integer.parseInt(o3.toString()) - 1) : 0;
              int rowsPerPage = o4 != null ? Math.max(1, Integer.parseInt(o4.toString())) : 1;
              int hscrollIndex = o5 != null ? (Integer.parseInt(o5.toString()) - 1) : 0;
              if (vert)
              {
                Object o7 = focusComp.getRelatedContext().safeLookup("NumRows");
                int numRows = o7 != null ? Integer.parseInt(o7.toString()) : 1;
                return new Integer(1 + (((tablePosition - colsPerPage * vscrollIndex - rowsPerPage * hscrollIndex + numRows) % numRows) / colsPerPage));
              }
              else
              {
                Object o6 = focusComp.getRelatedContext().safeLookup("NumCols");
                int numCols = o6 != null ? Integer.parseInt(o6.toString()) : 1;
                return new Integer(1 + (((tablePosition - colsPerPage * vscrollIndex - rowsPerPage * hscrollIndex + numCols) % numCols) % rowsPerPage));
              }
            }
          }
        }
        return new Integer(-1);
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetTableFocusedVisibleColumn", new String[] { "UIComponent" })
    {
      /**
       * Gets the column number that currently has focus in the specified table. The argument may be the table itself; or a component between
       * the table and the focused component.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the column number (1-based) which is currently focused in the table, -1 is returned if the value can't be resolved
       * @since 7.0
       *
       * @declaration public int GetTableFocusedVisibleColumn(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          // Now get the child focused component if it exists
          //				 if (uiComp.doesHierarchyHaveFocus())
          {
            ZComp focusOwner = uiComp.getLastFocusedChild();
            if (focusOwner == null)
              return new Integer(-1);
            if (focusOwner instanceof ZPseudoComp)
            {
              ZPseudoComp focusComp = (ZPseudoComp) focusOwner;
              // This depends upon the orientation of the table
              ZDataTable tabParent = focusComp.getTableParent();
              boolean vert = true;
              if (tabParent != null && tabParent.getTableDimensions() == ZDataTable.HORIZONTAL_DIMENSION)
                vert = false;
              // Now calculate its visible column position
              Object o1 = focusComp.getRelatedContext().safeLookup("TableRow");
              Object o2 = focusComp.getRelatedContext().safeLookup("NumColsPerPage");
              Object o3 = focusComp.getRelatedContext().safeLookup("VScrollIndex");
              Object o4 = focusComp.getRelatedContext().safeLookup("NumRowsPerPage");
              Object o5 = focusComp.getRelatedContext().safeLookup("HScrollIndex");
              int tablePosition = o1 != null ? (Integer.parseInt(o1.toString()) - 1) : 0;
              int colsPerPage = o2 != null ? Math.max(1, Integer.parseInt(o2.toString())) : 1;
              int vscrollIndex = o3 != null ? (Integer.parseInt(o3.toString()) - 1) : 0;
              int rowsPerPage = o4 != null ? Math.max(1, Integer.parseInt(o4.toString())) : 1;
              int hscrollIndex = o5 != null ? (Integer.parseInt(o5.toString()) - 1) : 0;
              //						 return new Integer(1 + (tablePosition / rowsPerPage) - hscrollIndex);
              if (vert)
              {
                Object o7 = focusComp.getRelatedContext().safeLookup("NumRows");
                int numRows = o7 != null ? Integer.parseInt(o7.toString()) : 1;
                return new Integer(1 + (((tablePosition - colsPerPage * vscrollIndex - rowsPerPage * hscrollIndex + numRows) % numRows) % colsPerPage));
              }
              else
              {
                Object o6 = focusComp.getRelatedContext().safeLookup("NumCols");
                int numCols = o6 != null ? Integer.parseInt(o6.toString()) : 1;
                return new Integer(1 + (((tablePosition - colsPerPage * vscrollIndex - rowsPerPage * hscrollIndex + numCols) % numCols) / rowsPerPage));
              }
            }
          }
        }
        return new Integer(-1);
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetTableFocusedVisiblePosition", new String[] { "UIComponent" })
    {
      /**
       * Gets the <b>visible</b> table position that currently has focus in the specified table. The argument may be the table itself; or a component between
       * the table and the focused component. This starts counting from the first component which is currently visible in the table.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the visible position (1-based) which is currently focused in the table, -1 is returned if the value can't be resolved
       * @since 7.0
       *
       * @declaration public int GetTableFocusedVisiblePosition(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          //				 if (uiComp.doesHierarchyHaveFocus())
          {
            ZComp focusOwner = uiComp.getLastFocusedChild();
            if (focusOwner == null)
              return new Integer(-1);
            if (focusOwner instanceof ZPseudoComp)
            {
              // Now calculate its visible position
              ZPseudoComp focusComp = (ZPseudoComp) focusOwner;
              Object o1 = focusComp.getRelatedContext().safeLookup("TableRow");
              Object o2 = focusComp.getRelatedContext().safeLookup("NumColsPerPage");
              Object o3 = focusComp.getRelatedContext().safeLookup("VScrollIndex");
              Object o4 = focusComp.getRelatedContext().safeLookup("NumRowsPerPage");
              Object o5 = focusComp.getRelatedContext().safeLookup("HScrollIndex");
              Object o6 = focusComp.getRelatedContext().safeLookup("NumCols");
              Object o7 = focusComp.getRelatedContext().safeLookup("NumRows");
              int tablePosition = o1 != null ? (Integer.parseInt(o1.toString()) - 1) : 0;
              int colsPerPage = o2 != null ? Math.max(1, Integer.parseInt(o2.toString())) : 1;
              int vscrollIndex = o3 != null ? (Integer.parseInt(o3.toString()) - 1) : 0;
              int rowsPerPage = o4 != null ? Math.max(1, Integer.parseInt(o4.toString())) : 1;
              int hscrollIndex = o5 != null ? (Integer.parseInt(o5.toString()) - 1) : 0;
              int numCols = o6 != null ? Integer.parseInt(o6.toString()) : 1;
              int numRows = o7 != null ? Integer.parseInt(o7.toString()) : 1;
              return new Integer(1 + (tablePosition - colsPerPage * vscrollIndex - rowsPerPage * hscrollIndex + numRows*numCols) % (numRows * numCols));
            }
          }
        }
        return new Integer(-1);
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetTableFocusedPosition", new String[] { "UIComponent" })
    {
      /**
       * Gets the table position that currently has focus in the specified table. The argument may be the table itself; or a component between
       * the table and the focused component. This is the equivalent of the "TableRow" variable for the currently focused table cell; which is
       * the cell's index in the data backing the table.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the position (1-based) which is currently focused in the table, -1 is returned if the value can't be resolved
       * @since 7.0
       *
       * @declaration public int GetTableFocusedPosition(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          ZComp focusOwner = uiComp.getLastFocusedChild();
          if (focusOwner == null)
            return new Integer(-1);
          if (focusOwner instanceof ZPseudoComp)
          {
            // Now calculate its visible position
            ZPseudoComp focusComp = (ZPseudoComp) focusOwner;
            Object o1 = focusComp.getRelatedContext().safeLookup("TableRow");
            int tablePosition = o1 != null ? (Integer.parseInt(o1.toString()) - 1) : 0;
            return new Integer(1 + tablePosition);
          }
        }
        return new Integer(-1);
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetDataFromTableFocusedOffset", new String[] { "UIComponent", "Offset", "WrapIndex" })
    {
      /**
       * Gets the data value for the table that is used for the cell at the specified position relative to what is currently
       * <b>focused</b> for the table. The UIComponent should either be the table itself; or it should have the desired table as an ancestor of it.
       * Position values are zero-based; and may be negative. If WrapIndex is false then positions greater then the size of the table's data or
       * less than zero will return null; otherwise they will be mod'd to return a value within the range of the data.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @param Offset the offset of the cell who's table data should be returned; relative to the current focused position (last focused if not currently focused)
       * @param WrapIndex true if offsets that fall outside of the size of the data should be wrapped so that they return a data value
       * @return the value of the table data at the specified offset
       * @since 7.0
       *
       * @declaration public Object GetDataFromTableFocusedOffset(Object UIComponent, int Offset, boolean WrapIndex);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean wrapIndex = getBool(stack);
        int offset = getInt(stack);
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          // Now get the child focused component if it exists
          ZComp focusOwner = uiComp.getLastFocusedChild();
          if (focusOwner instanceof ZPseudoComp)
          {
            // Now calculate its visible position
            ZPseudoComp focusComp = (ZPseudoComp) focusOwner;
            ZDataTable tabParent = focusComp.getTableParent();
            if (tabParent != null)
            {
              Object o1 = focusComp.getRelatedContext().safeLookup("TableRow");
              int tablePosition = o1 != null ? (Integer.parseInt(o1.toString()) - 1) : 0;
              return tabParent.getTableDataFromOffset(tablePosition + offset, wrapIndex);
            }
          }
          else
          {
            // Get the one at the first position instead
            ZDataTable tabParent = uiComp.getTableParent();
            if (tabParent != null)
            {
              return tabParent.getTableDataFromOffset(offset, wrapIndex);
            }
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetDataFromTableVisiblePosition", new String[] { "UIComponent", "Offset", "WrapIndex" })
    {
      /**
       * Gets the data value for the table that is used for the cell at the specified position relative to what is currently
       * <b>visible</b> for the table. The UIComponent should either be the table itself; or it should have the desired table as an ancestor of it.
       * Offset values are zero-based; and may be negative. If WrapIndex is false then positions greater then the size of the table's data or
       * less than zero will return null; otherwise they will be mod'd to return a value within the range of the data.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @param Offset the offset of the cell who's table data should be returned; relative to the first visible position
       * @param WrapIndex true if offsets that fall outside of the size of the data should be wrapped so that they return a data value
       * @return the value of the table data at the specified offset
       * @since 7.0
       *
       * @declaration public Object GetDataFromTableVisiblePosition(Object UIComponent, int Offset, boolean WrapIndex);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean wrapIndex = getBool(stack);
        int position = getInt(stack);
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          ZDataTable tabParent = uiComp.getTableParent();
          if (tabParent == null)
            return null;
          int colsPerPage = Math.max(1, tabParent.getNumColsPerPage());
          int vscrollIndex = tabParent.getVScrollIndex();
          int rowsPerPage = Math.max(1, tabParent.getNumRowsPerPage());
          int hscrollIndex = tabParent.getHScrollIndex();
          int visPos = position + colsPerPage * vscrollIndex + rowsPerPage * hscrollIndex;
          return tabParent.getTableDataFromOffset(visPos, wrapIndex);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsTableTransitionToNext", new String[] { "UIComponent" })
    {
      /**
       * Returns true if the specified table argument (or the first table parent of the argument) is on the leading edge of a transition
       * to a cell after the current one. It could be a page event, or a moving a single unit. Right and Down directionality is considered to be 'next'.
       * This can be used to trigger effects that relate to navigation occurring in a table. This will only be true for a single rendering pass on the
       * leading edge of the transition. Focus will be moved before this becomes true. If effect animations are disabled; this will always return false.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return true if the table is currently transitioning in the next direction
       * @since 7.0
       *
       * @declaration public boolean IsTableTransitionToNext(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (stack.getUIComponent() != null)
          stack.getUIComponent().setNextTransitionListen();
        if (uiComp != null && uiComp.getUIMgr().areEffectsEnabled())
        {
          ZDataTable tabParent = uiComp.getTableParent();
          if (tabParent != null)
          {
            return tabParent.isDoingNextTransition() ? Boolean.TRUE : Boolean.FALSE;
          }
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsTableTransitionToPrevious", new String[] { "UIComponent" })
    {
      /**
       * Returns true if the specified table argument (or the first table parent of the argument) is on the leading edge of a transition
       * to a cell before the current one. It could be a page event, or a moving a single unit. Left and Up directionality is considered to be 'previous'.
       * This can be used to trigger effects that relate to navigation occurring in a table. This will only be true for a single rendering pass on the
       * leading edge of the transition. Focus will be moved before this becomes true. If effect animations are disabled; this will always return false.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return true if the table is currently transitioning in the previous direction
       * @since 7.0
       *
       * @declaration public boolean IsTableTransitionToPrevious(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (stack.getUIComponent() != null)
          stack.getUIComponent().setPrevTransitionListen();
        if (uiComp != null && uiComp.getUIMgr().areEffectsEnabled())
        {
          ZDataTable tabParent = uiComp.getTableParent();
          if (tabParent != null)
          {
            return tabParent.isDoingPrevTransition() ? Boolean.TRUE : Boolean.FALSE;
          }
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsTransitioningToMenu", new String[] { "MenuName" })
    {
      /**
       * Returns true if the specified menu name matches the name of the menu widget that the UI is in the process
       * of transitioning to. This will only be true during the processing of MenuUnloaded effects. If a null argument is given; then this is
       * true in the case that any menu transition is about to occur. The name match is case-insensitive.
       * @param MenuName the name of the menu to test to see if we're transitioning to; null if it matches any menu name
       * @return true if the specified menu name matches the name of the menu widget that the UI is in the process of transitioning to, false otherwise
       * @since 7.0
       *
       * @declaration public boolean IsTransitioningToMenu(String MenuName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String menuName = getString(stack);
        UIManager uiMgr = stack.getUIMgrSafe();
        if (uiMgr != null)
        {
          Widget nextUI = uiMgr.getMenuTransitionTarget();
          return (nextUI != null && (menuName == null || nextUI.getName().equalsIgnoreCase(menuName))) ? Boolean.TRUE : Boolean.FALSE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsTransitioningFromMenu", new String[] { "MenuName" })
    {
      /**
       * Returns true if the specified menu name matches the name of the menu widget for the previously loaded menu. The name match is case-insensitive.
       * Unlike <b>IsTransitioningToMenu</b>, this is true even after the transition is complete. A null argument always returns false.
       * @param MenuName the name of the menu to test to see if it was the previously loaded menu
       * @return true if the specified menu name matches the name of the menu widget for the previously loaded menu, false otherwise
       * @since 7.0
       *
       * @declaration public boolean IsTransitioningFromMenu(String MenuName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String menuName = getString(stack);
        UIManager uiMgr = stack.getUIMgrSafe();
        if (uiMgr != null)
        {
          Widget nextUI = uiMgr.getMenuTransitionSource();
          PseudoMenu currUI = uiMgr.getCurrUI();
          return (nextUI != null && currUI != null && currUI.getUI() != null &&
              /*currUI.getUI().isMenuLoadingState() &&*/ nextUI.getName().equalsIgnoreCase(menuName)) ? Boolean.TRUE : Boolean.FALSE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsTransitioningToMenus", new String[] { "MenuNames" })
    {
      /**
       * Returns true if one of the specified menu name matches the name of the menu widget that the UI is in the process
       * of transitioning to. This will only be true during the processing of MenuUnloaded effects. If a null argument or empty list is given; then this is
       * true in the case that any menu transition is about to occur. The name match is case-insensitive.
       * @param MenuNames the names of the menu to test to see if we're transitioning to; null or empty list if it matches any menu name
       * @return true if one of the specified menu name matches the name of the menu widget that the UI is in the process of transitioning to, false otherwise
       * @since 8.0
       *
       * @declaration public boolean IsTransitioningToMenus(String[] MenuNames);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] menuNames = getStringList(stack);
        UIManager uiMgr = stack.getUIMgrSafe();
        if (uiMgr != null)
        {
          Widget nextUI = uiMgr.getMenuTransitionTarget();
          if (nextUI == null)
            return Boolean.FALSE;
          if (menuNames == null || menuNames.length == 0)
            return Boolean.TRUE;
          for (int i = 0; i < menuNames.length; i++)
            if (menuNames[i] != null && nextUI.getName().equalsIgnoreCase(menuNames[i]))
              return Boolean.TRUE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsTransitioningFromMenus", new String[] { "MenuNames" })
    {
      /**
       * Returns true if one of the specified menu names matches the name of the menu widget for the previously loaded menu. The name match is case-insensitive.
       * Unlike <b>IsTransitioningToMenus</b>, this is true even after the transition is complete. A null argument or empty list always returns false.
       * @param MenuNames the names of the menus to test to see if it was the previously loaded menu
       * @return true if one of the specified menu names matches the name of the menu widget for the previously loaded menu, false otherwise
       * @since 8.0
       *
       * @declaration public boolean IsTransitioningFromMenus(String[] MenuNames);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] menuNames = getStringList(stack);
        UIManager uiMgr = stack.getUIMgrSafe();
        if (uiMgr != null)
        {
          Widget nextUI = uiMgr.getMenuTransitionSource();
          PseudoMenu currUI = uiMgr.getCurrUI();
          if (nextUI == null || currUI == null || currUI.getUI() == null)
            return Boolean.FALSE;
          if (menuNames == null || menuNames.length == 0)
            return Boolean.FALSE;
          for (int i = 0; i < menuNames.length; i++)
            if (menuNames[i] != null && nextUI.getName().equalsIgnoreCase(menuNames[i]))
              return Boolean.TRUE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetTextForUIComponent", new String[] { "UIComponent" })
    {
      /**
       * Gets the value displayed for the textual component of the specified UI object. If its not a Text component; then
       * its children will be searched depth-first until one is found and then that will be used for the return value.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the text value displayed by the argument itself; or if its not a text component then the value displayed by the first text child of the component; null if neither condition is met
       * @since 7.0
       *
       * @declaration public String GetTextForUIComponent(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          // Check to make sure its a TEXT widget, and if so then just return the string stored in its ZLabel child
          ZLabel label = uiComp.findFirstLabelChild();
          if (label != null)
            return label.getText();
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIComponentLastFocusedChild", new String[] { "UIComponent" })
    {
      /**
       * If the specified component currently has a focused child; that child's UI object will
       * be returned. If not, then the last focused child for this component will be returned.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the currently focused child UI object for the specified component if it exists; otherwise the last focused child UI object for the specified component
       * @since 7.0
       *
       * @declaration public Object GetUIComponentLastFocusedChild(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          ZComp rv = uiComp.getLastFocusedChild();
          if (rv instanceof ZPseudoComp)
            return rv;
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIComponentPositionX", new String[] { "UIComponent" })
    {
      /**
       * Returns the X position in pixels in the UI of the specified UI component. This will be a value relative to the overall UI; not its parent.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the X position in pixels relative to the overall UI of the specified component
       * @since 7.0
       *
       * @declaration public int GetUIComponentPositionX(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          return new Integer(uiComp.getTrueX());
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIComponentPositionY", new String[] { "UIComponent" })
    {
      /**
       * Returns the Y position in pixels in the UI of the specified UI component. This will be a value relative to the overall UI; not its parent.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the Y position in pixels relative to the overall UI of the specified component
       * @since 7.0
       *
       * @declaration public int GetUIComponentPositionY(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          return new Integer(uiComp.getTrueY());
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIComponentWidth", new String[] { "UIComponent" })
    {
      /**
       * Returns the width in pixels in the UI of the specified UI component.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the width in pixels of the specified component
       * @since 7.0
       *
       * @declaration public int GetUIComponentWidth(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          return new Integer(uiComp.getWidth());
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIComponentHeight", new String[] { "UIComponent" })
    {
      /**
       * Returns the height in pixels in the UI of the specified UI component.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the height in pixels of the specified component
       * @since 7.0
       *
       * @declaration public int GetUIComponentHeight(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          return new Integer(uiComp.getHeight());
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIComponentPositionXf", new String[] { "UIComponent" })
    {
      /**
       * Returns the X position in the UI of the specified UI component. This will be a value relative to the overall UI; not its parent.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the X position relative to the overall UI width of the specified component, this will be a floating point number
       * @since 8.0
       *
       * @declaration public float GetUIComponentPositionXf(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          return new Float(uiComp.getTrueXf() / uiComp.getReality().getWidth());
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIComponentPositionYf", new String[] { "UIComponent" })
    {
      /**
       * Returns the Y position in the UI of the specified UI component. This will be a value relative to the overall UI; not its parent.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the Y position relative to the overall UI height of the specified component, this will be a floating point number
       * @since 8.0
       *
       * @declaration public float GetUIComponentPositionYf(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          return new Float(uiComp.getTrueYf() / uiComp.getReality().getHeight());
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIComponentWidthf", new String[] { "UIComponent" })
    {
      /**
       * Returns the width in the UI of the specified UI component relative to the entire UI width.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the width of the specified component relative to the entire UI width, this will be a floating point number
       * @since 8.0
       *
       * @declaration public int GetUIComponentWidthf(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          return new Float(uiComp.getWidthf() / uiComp.getReality().getWidth());
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIComponentHeightf", new String[] { "UIComponent" })
    {
      /**
       * Returns the height in the UI of the specified UI component relative to the entire UI height.
       * @param UIComponent the specified UIComponent, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @return the height of the specified component relative to the entire UI height, this will be a floating point number
       * @since 8.0
       *
       * @declaration public float GetUIComponentHeightf(Object UIComponent);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          return new Float(uiComp.getHeightf() / uiComp.getReality().getHeight());
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "SendEventToUIComponent", new String[] { "UIComponent", "Command", "RepeatCount" })
    {
      /**
       * Sends an event to a specific UI component for processing. Similar to using the {@link #SageCommand SageCommand()} API call, but this
       * version is targeted at a specific component. Normal event processing of cursor navigation, selection, entry events will continue up the hierarchy from the target component if it
       * does not consume it; it will stop at the menu level though and any menu-level default handling for the event will not occur (i.e. media player control, menu navigation, power, etc.).
       * @param UIComponent the UIComponent object to target the event at, can be retrieved using {@link #GetUIComponentForVariable GetUIComponentForVariable} API call
       * @param Command the name of the command to send
       * @param RepeatCount the number of times to send the command
       * @return true if the event was consumed by the target component's hierarchy
       * @since 7.0
       *
       * @declaration public Object SendEventToUIComponent(Object UIComponent, String Command, int RepeatCount);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int numTimes = getInt(stack);
        int evtCode = UserEvent.getEvtCodeForName(getString(stack));
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          boolean rv = false;
          for (int i = 0; i < numTimes; i++)
            rv = uiComp.action(new UserEvent(uiComp.getUIMgr().getRouter().getEventCreationTime(), evtCode, -1));
          return Boolean.valueOf(rv);
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetChildUIComponentForVariable", new String[] { "UIComponent", "MatchName", "MatchValue" })
    {
      /**
       * Searches all child UI elements in of the specified UIComponent until it finds one that has a variable with the specified name
       * matching the specified value. It then returns on Object representing that UI component
       * @param UIComponent the UI component who's children should be searched
       * @param MatchName the name of the variable to match
       * @param MatchValue the value of the variable to match on
       * @return the UI component with the specified variable set
       * @since 7.0
       *
       * @declaration public Object GetChildUIComponentForVariable(Object UIComponent, String MatchName, Object MatchValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object val = stack.pop();
        String name = getString(stack);
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          Object[] rvHolder = new Object[2];
          uiComp.getCompForVariable(name, val, rvHolder, true);
          return (rvHolder[0] != null) ? ((ZPseudoComp) rvHolder[0]) : ((ZPseudoComp) rvHolder[1]);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetVariableFromUIComponent", new String[] { "UIComponent", "VarName" })
    {
      /**
       * Retrieves the variable with the specified name from the context of the passed in UIComponent.
       * @param UIComponent the UI component who's variable should be returned
       * @param VarName the name of the variable to lookup
       * @return the value of the variable
       * @since 7.0
       *
       * @declaration public Object GetVariableFromUIComponent(Object UIComponent, String VarName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String name = getString(stack);
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          if ("Focused".equals(name))
            return Boolean.valueOf(uiComp.isFocused());
          else if ("FocusedChild".equals(name))
            return Boolean.valueOf(uiComp.doesHierarchyHaveFocus());
          else
            return uiComp.getRelatedContext().safeLookup(name);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "SetVariableForUIComponent", new String[] { "UIComponent", "VarName", "VarValue" })
    {
      /**
       * Sets the variable with the specified name in the context of the passed in UIComponent. This behaves the same way as
       * if an assignment was done to that variable within the passed in UIComponent context within the Studio. This may result
       * in setting the value at a higher level in the hierarchy if the variable is defined above the specified component.
       * @param UIComponent the UI component who's context should be used when setting the variable
       * @param VarName the name of the variable to set
       * @param VarValue the value to set the variable to
       * @since 7.1
       *
       * @declaration public void SetVariableForUIComponent(Object UIComponent, String VarName, Object VarValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object value = stack.pop();
        String name = getString(stack);
        ZPseudoComp uiComp = getUIComp(stack);
        if (uiComp != null)
        {
          uiComp.getRelatedContext().set(name, value);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "PassiveListen")
    {
      /**
       * Should only be used in the Action chain from a Listener Widget. Normally SageTV will stop processing Listeners
       * for an event once the first one is reached. If you use PassiveListen() in the action chain for a
       * Listener then SageTV will not stop processing at the current Listener.
       *
       * @declaration public void PassiveListen();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        throw new IllegalArgumentException("PassiveListen() can only be used by itself");
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetAiringsThatWontBeRecorded", new String[] { "OnlyUnresolved" }, true)
    {
      /**
       * Returns all of the Airings that the user has requested to record that SageTV will not be recording. This would be due to
       * scheduling conflicts.
       * @param OnlyUnresolved if true then only unresolved scheduling conflicts will be returned, if false then all conflicts will be returned
       * @return the list of Airings that will not be recorded due to scheduling conflicts
       *
       * @declaration public java.util.Vector GetAiringsThatWontBeRecorded(boolean OnlyUnresolved);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.util.Map conMap = Catbert.evalBool(stack.pop()) ? SchedulerSelector.getInstance().getUnresolvedConflictsMap() :
          SchedulerSelector.getInstance().getConflictsMap();
        java.util.Vector rv = new java.util.Vector();
        java.util.Iterator walker = conMap.values().iterator();
        while (walker.hasNext())
        {
          rv.addAll((java.util.List) walker.next());
        }
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsClient")
    {
      /**
       * Returns true if this is an instance of SageTV Client
       * @return true if this is an instance of SageTV Client, false otherwise
       *
       * @declaration public boolean IsClient();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(Sage.isTrueClient());
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsRemoteUI")
    {
      /**
       * Returns true if this UI is being remoted onto another device such as a media extender. This also returns
       * true for Placeshifter clients.
       * @return true if this UI is being remoted onto another device such as a media extender, false otherwise
       *
       * @since 4.1
       * @declaration public boolean IsRemoteUI();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI);
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsDesktopUI")
    {
      /**
       * Returns true if this UI is being run in a desktop environment. It may or may not be remoted. This is
       * intended to distinguish Placeshifter clients from Media Extenders since both return true for IsRemoteUI().
       * @return true if this UI is being run in a desktop environment, false otherwise
       *
       * @since 4.1.10
       * @declaration public boolean IsDesktopUI();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() == null) // if we have no UI, we're not a desktop
          return Boolean.FALSE;
        int uiType = stack.getUIMgr().getUIClientType();
        // If we're a SageTVClient or a full SageTV UI instance on windows or Mac then we have a desktop
        if ((Sage.WINDOWS_OS || Sage.MAC_OS_X) && (uiType == UIClient.LOCAL || uiType == UIClient.REMOTE_CLIENT))
          return Boolean.TRUE;
        // If we're a Remote UI with a mouse then we're a desktop
        if (uiType == UIClient.REMOTE_UI)
        {
          if (stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
          {
            String ipdp = ((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).getInputDevsProp();
            if (ipdp != null && ipdp.indexOf("MOUSE") != -1)
              return Boolean.TRUE;
          }
        }
        return Boolean.FALSE; // this means we're a full SageTV UI running under Linux
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsTouchUI")
    {
      /**
       * Returns true if this UI is being run in a touch environment, such as a phone or tablet.
       * @return true if this UI is being run in a touch environment, false otherwise
       *
       * @since 9.0.4
       * @declaration public boolean IsTouchUI();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() == null) // if we have no UI, we're not touch enabled
          return Boolean.FALSE;
        int uiType = stack.getUIMgr().getUIClientType();
        // If we're a Remote UI with a TOUCH then we're a touch enabled UI
        if (uiType == UIClient.REMOTE_UI)
        {
          if (stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
          {
            String ipdp = ((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).getInputDevsProp();
            if (ipdp != null && ipdp.indexOf("TOUCH") != -1)
              return Boolean.TRUE;
          }
        }
        return Boolean.FALSE; // Touch is not enabled on this UI
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsServerUI")
    {
      /**
       * Returns true if this UI is being run on the same system as the SageTV Server it's connected to.
       * Also returns true for standalone SageTV applications. This indicates the UI should have the ability
       * to configure all 'server' based options. For SageTVClient, you must be connected to the loopback address
       * for this to return true.
       * @return true if this is the main UI for a SageTV Server
       * @since 5.0.5
       *
       * @declaration public boolean IsServerUI();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null) // if we have no UI, we're not a desktop
          return Boolean.FALSE;
        return uiMgr.isServerUI() ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetConnectedClients", true)
    {
      /**
       * Returns a list of all the clients that are currently connected to this server.
       * @return a list of all the clients that are currently connected to this server.
       *
       * @declaration public String[] GetConnectedClients();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return NetworkClient.getConnectedClients();
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIContextNames")
    {
      /**
       * Returns a list of the names of the different UI contexts that are available. One will be the local context
       * which is the UI for the SageTV app (which doesn't exist in service mode). The other contexts can be UIs that
       * are for remote devices such as media extenders. This context name can then be passed back in using
       * the sage.SageTV.apiUI(String uiContextName, String methodName, Object[] args) call in order to execute an API call
       * from Java within a specific UI context.  For media extenders, these names generally correspond to the MAC address
       * of the media extender.
       * @return a list of the UI context names available
       * @since 4.1
       *
       * @declaration public String[] GetUIContextNames();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.util.ArrayList rv = new java.util.ArrayList();
        java.util.Iterator walker = UIManager.getUIIterator();
        while (walker.hasNext())
        {
          UIManager foo = (UIManager) walker.next();
          // If we're in service mode then don't put the local UI in here.
          if (Sage.isHeadless() && Seeker.LOCAL_PROCESS_CLIENT.equals(foo.getLocalUIClientName()))
            continue;
          rv.add(foo.getLocalUIClientName());
        }
        return rv.toArray(Pooler.EMPTY_STRING_ARRAY);
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetUIContextName")
    {
      /**
       * Returns the name of the UI context that makes the API call. See {@link #GetUIContextNames GetUIContextNames()}
       * for more information.
       * @return the UI context name that made this API call, null if there is no UI context
       * @since 5.1
       *
       * @declaration public String GetUIContextName();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr != null)
          return uiMgr.getLocalUIClientName();
        else
          return sage.Catbert.uiContextThreadNames.get();
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetRemoteClientVersion")
    {
      /**
       * Returns the version string for this connected remote client
       * @return the version string for this connected remote client, or the empty string if it's undefined or not a remote client
       * @since 6.4
       *
       * @declaration public String GetRemoteClientVersion();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr != null && uiMgr.getUIClientType() == UIClient.REMOTE_UI)
        {
          return ((MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine()).getRemoteVersion();
        }
        return "";
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetRemoteUIType")
    {
      /**
       * Returns the type of client that is connected on this remote interface
       * @return the type of client that is connected on this remote interface; this can be one of "SD Media Extender", "HD Media Extender", "HD Media Player", "Placeshifter" or "Local"
       * @since 6.4
       *
       * @declaration public String GetRemoteUIType();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr != null && uiMgr.getUIClientType() == UIClient.REMOTE_UI)
        {
          MiniClientSageRenderer mcsr = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
          if (mcsr.isMediaExtender())
          {
            if (mcsr.isSupportedVideoCodec("MPEG2-VIDEO@HL"))
              return Sage.rez(mcsr.isStandaloneMediaPlayer() ? "HD_Media_Player" : "HD_Media_Extender");
            else
              return Sage.rez("SD_Media_Extender");
          }
          else
            return Sage.rez("Placeshifter");
        }
        return Sage.rez("Local");
      }});
    rft.put(new PredefinedJEPFunction("Global", "CreateTimedRecording", new String[] { "Channel", "StartTime", "StopTime", "Recurrence" })
    {
      /**
       * Creates a new time based recording for SageTV.
       * @param Channel the Channel object that this recording should be performed on
       * @param StartTime the time the recording should begin
       * @param StopTime the time the recording should end
       * @param Recurrence the name of the recurrence to use; this can be either Once, Daily, Weekly or Continuous (Continuous as of V6.6)
       *               (or a localized version of one of those); OR it can be a combination of any of the following strings to indicate specified
       *                days: Su, Mo, Tu, We, Th, Fr, Sa (for example, MoTuWe to do Mondays, Tuesdays and Wednesdays)
       * @return true if the creation of the timed recording succeeded, otherwise a localized error string is returned
       *
       * @declaration public Object CreateTimedRecording(Channel Channel, long StartTime, long StopTime, String Recurrence);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        final int recurCode = ManualRecord.getRecurrenceCodeForName(getString(stack));
        final long stop = getLong(stack);
        final long start = getLong(stack);
        final Channel c = getChannel(stack);
        final Object taskID = Catbert.getNewTaskID();
        final UIClient uiClient = Sage.client ? stack.getUIMgrSafe() : stack.getUIMgr();
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            Object rv;
            Airing baseAir = null;
            /* // This causes problems since you can't link more than one timed record to the same airing
					if (Sage.getBoolean("link_airing_to_timed_record", true))
					{
						Airing[] overlapAirs = Wizard.getInstance().getAirings(c.stationID, start, stop, false);
						if (overlapAirs != null && overlapAirs.length > 0)
						{
							long mostOverlap = 0;
							for (int i = 0; i < overlapAirs.length; i++)
							{
								long currOverlap = Math.min(stop, overlapAirs[i].getEndTime()) -
									Math.max(start, overlapAirs[i].getStartTime());
								if (currOverlap > mostOverlap)
								{
									mostOverlap = currOverlap;
									baseAir = overlapAirs[i];
								}
							}
						}
					}*/
            rv = Catbert.getRecordFailureObject(SeekerSelector.getInstance().timedRecord(start, stop, c.getStationID(), recurCode, baseAir, uiClient));
            Catbert.asyncTaskComplete(taskID, rv);
          }
        }, "AsyncCreateTimedRecording");
        return taskID;
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsFullScreen")
    {
      /**
       * Returns whether or not SageTV is in full screen mode
       * @return true if SageTV is in full screen mode, false otherwise
       *
       * @declaration public boolean IsFullScreen();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().isFullScreen());
      }});
    rft.put(new PredefinedJEPFunction("Global", "SetFullScreen", new String[] { "FullScreen" })
    {
      /**
       * Sets SageTV to be in full or non-full screen mode
       * @param FullScreen true if SageTV should be put into full screen mode, false if it should be put into windowed mode
       *
       * @declaration public void SetFullScreen(boolean FullScreen);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().setFullScreen(evalBool(stack.pop()));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetServerAddress")
    {
      /**
       * Gets the hostname of the SageTV server if this is a client, otherwise it returns the name of the host SageTV is running on
       * @return the hostname of the SageTV server if this is a client, otherwise it returns the name of the host SageTV is running on
       *
       * @declaration public String GetServerAddress();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.client ? Sage.preferredServer : SageTV.hostname;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetOS")
    {
      /**
       * Gets the name of the operating system that is being used.
       * @return the name of the operating system that is being used
       *
       * @declaration public String GetOS();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return System.getProperty("os.name");
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsWindowsOS")
    {
      /**
       * Returns true if SageTV is currently running on a Windows operating system.
       * @return true if SageTV is currently running on a Windows operating system, false otherwise
       *
       * @declaration public boolean IsWindowsOS();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.WINDOWS_OS ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsLinuxOS")
    {
      /**
       * Returns true if SageTV is currently running on a Linux operating system.
       * @return true if SageTV is currently running on a Linux operating system, false otherwise
       *
       * @since 6.0.20
       *
       * @declaration public boolean IsLinuxOS();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.LINUX_OS ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsMacOS")
    {
      /**
       * Returns true if SageTV is currently running on a Macintosh operating system.
       * @return true if SageTV is currently running on a Macintosh operating system, false otherwise
       *
       * @since 6.0.20
       *
       * @declaration public boolean IsMacOS();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.MAC_OS_X ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "DVDBurnTheBurnList", new String[] { "BurnList" }, true)
    {
      /**
       * Instructs the DVD burning engine inside SageTV to start prepping the specified Playlist to be burned to a DVD, and then perform the actual burn.
       * NOTE: Currently this is only supported on the Linux operating system
       * @param BurnList the Playlist of all of the video files to burn to the DVD
       * @return true if the burn process was successfully started, otherwise a localized error message string is returned
       *
       * @declaration public Object DVDBurnTheBurnList(Playlist BurnList);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Playlist p = (Playlist) stack.pop();
        // Convert the Playlist to a list of File objects
        MediaFile[] burnFiles = p.getMediaFiles();
        java.util.ArrayList rawFiles = new java.util.ArrayList();
        for (int i = 0; i < burnFiles.length; i++)
          rawFiles.addAll(java.util.Arrays.asList(burnFiles[i].getFiles()));
        return Burner.getGlobalBurner().burnFilesToDVD((java.io.File[]) rawFiles.toArray(new java.io.File[0]));
      }});
    rft.put(new PredefinedJEPFunction("Global", "DVDCancelBurn", true)
    {
      /**
       * Cancels a previous request that was made to perform DVD burning.
       *
       * @declaration public void DVDCancelBurn();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Burner.getGlobalBurner().cancel();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "DVDGetCurrentBurnStatus", true)
    {
      /**
       * Gets the current status of a previously invoked DVD burning process.
       * @return true if the DVD burning process is completed and was a success, "Error" if it completed and was a failure,
       *          otherwise a localized status message indicating progress is returned
       *
       * @declaration public Object DVDGetCurrentBurnStatus();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Burner.getGlobalBurner().isComplete())
        {
          if (Burner.getGlobalBurner().wasSuccessful())
            return Boolean.TRUE;
          else
            return "Error";
        }
        else
          return Burner.getGlobalBurner().getStatusMessage();
      }});
    rft.put(new PredefinedJEPFunction("Global", "CDBurnTheBurnList", new String[] { "BurnList" }, true)
    {
      /**
       * Instructs the CD burning engine inside SageTV to start prepping the specified Playlist to be burned to a CD, and then perform the actual burn.
       * NOTE: Currently this is only supported on the Linux operating system
       * @param BurnList the Playlist of all of the music files to burn to the CD
       * @return true if the burn process was successfully started, otherwise a localized error message string is returned
       *
       * @declaration public Object CDBurnTheBurnList(Playlist BurnList);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Playlist p = (Playlist) stack.pop();
        // Convert the Playlist to a list of File objects
        MediaFile[] burnFiles = p.getMediaFiles();
        return Burner.getGlobalBurner().burnFilesToCD(burnFiles);
      }});
    rft.put(new PredefinedJEPFunction("Global", "CDCancelBurn", true)
    {
      /**
       * Cancels a previous request that was made to perform CD burning.
       *
       * @declaration public void CDCancelBurn();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Burner.getGlobalBurner().cancel();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "CDGetCurrentBurnStatus", true)
    {
      /**
       * Gets the current status of a previously invoked CD burning process.
       * @return true if the CD burning process is completed and was a success, "Error" if it completed and was a failure,
       *          otherwise a localized status message indicating progress is returned
       *
       * @declaration public Object CDGetCurrentBurnStatus();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Burner.getGlobalBurner().isComplete())
        {
          if (Burner.getGlobalBurner().wasSuccessful())
            return Boolean.TRUE;
          else
            return "Error";
        }
        else
          return Burner.getGlobalBurner().getStatusMessage();
      }});
    rft.put(new PredefinedJEPFunction("Global", "CDRipToLibrary", new String[] { "LibraryDir", "BitrateKbps" }, true)
    {
      /**
       * Instructs the CD ripping engine to rip the contents of a CD and encode it in MP3 format and then store it in the music library.
       * NOTE: This is currently only supported on the Linux operating system
       * @param LibraryDir the directory that the ripped files should be stored in, if the space on this disk is managed by SageTV it will make room for the files that are to be ripped
       * @param BitrateKbps the bitrate to use for the audio encoding in kilobits per second
       * @return true if the ripping process was successfully started, otherwise a localized error message string is returned
       *
       * @declaration public Object CDRipToLibrary(java.io.File LibraryDir, String BitrateKbps);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String bitrate = getString(stack);
        java.io.File folder = getFile(stack);
        return Ripper.getGlobalRipper().ripFilesFromCD(folder, bitrate);
      }});
    rft.put(new PredefinedJEPFunction("Global", "CDCancelRip", true)
    {
      /**
       * Cancels a previous request that was made to perform CD ripping
       *
       * @declaration public void CDCancelRip();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Ripper.getGlobalRipper().cancel();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "CDGetCurrentRipStatus", true)
    {
      /**
       * Gets the current status of a previously invoked CD ripping process.
       * @return true if the CD ripping process is completed and was a success, "Error" if it completed and was a failure,
       *          otherwise a localized status message indicating progress is returned
       *
       * @declaration public Object CDGetCurrentRipStatus();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Ripper.getGlobalRipper().isComplete())
        {
          if (Ripper.getGlobalRipper().wasSuccessful())
            return Boolean.TRUE;
          else
            return "Error";
        }
        else
          return Ripper.getGlobalRipper().getStatusMessage();
      }});
    rft.put(new PredefinedJEPFunction("Global", "StartFileCopy", new String[] { "Filename", "SourceDirectory", "DestDirectory" })
    {
      /**
       * Instructs the file transfer engine to copy the specified file(s) from the source directory to the destination directory.
       * If there is no filename specified then the contents of the directory are copied recursively. If the destination directory
       * is within the path of SageTV managed diskspace then the appropriate free space will be cleared on the disk in order for the file copy to succeed.
       * This also works for uploading files from a SageTV client to a SageTV server.
       * @param Filename the name of the file in the SourceDirectory to copy, or null if the whole directory should be copied
       * @param SourceDirectory the source directory for the file copy (smb:// paths are OK)
       * @param DestDirectory the destination directory for the file copy
       * @return true if the copy process was successfully started, false if the file copy is unable to be performed
       *
       * @declaration public Object StartFileCopy(String Filename, String SourceDirectory, java.io.File DestDirectory);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File dest = getFile(stack);
        String src = getString(stack);
        String filename = getString(stack);
        if (filename != null && filename.length() > 0)
        {
          if (src.endsWith(java.io.File.separator))
            src = src + filename;
          else
            src = src + java.io.File.separator + filename;
        }
        if (Sage.DBG) System.out.println("Requested file copy of " + src + " to " + dest);
        return FileTransfer.getFileTransfer(stack.getUIMgrSafe()).transferFile(src, dest);
      }});
    rft.put(new PredefinedJEPFunction("Global", "CancelFileCopy")
    {
      /**
       * Cancels a previous request that was made to perform a file copy
       *
       * @declaration public void CancelFileCopy();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Sage.DBG) System.out.println("Cancelling current file copy operation.");
        FileTransfer.getFileTransfer(stack.getUIMgrSafe()).cancel();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetFileCopyStatus")
    {
      /**
       * Gets the current status of a previously invoked file copy process.
       * @return true if the file copy process is completed and was a success, "Error" will be the prefix if it was a failure,
       *          otherwise a localized status message indicating progress is returned
       *
       * @declaration public Object GetFileCopyStatus();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (FileTransfer.getFileTransfer(stack.getUIMgrSafe()).isComplete())
        {
          if (FileTransfer.getFileTransfer(stack.getUIMgrSafe()).wasSuccessful())
            return Boolean.TRUE;
          else
            return "Error: " + FileTransfer.getFileTransfer(stack.getUIMgrSafe()).getStatusMessage();
        }
        else
          return FileTransfer.getFileTransfer(stack.getUIMgrSafe()).getStatusMessage();
      }});
    rft.put(new PredefinedJEPFunction("Global", "StartFileDownload", -1, new String[] { "ServerAddress", "SourceFile", "DestFile" })
    {
      /**
       * Instructs the file transfer engine to download the specified file from the server to the local destination file. You may also
       * download from remote http:// or ftp:// addresses; in that case just specify the URL in the ServerAddress argument and leave sourceFile as null.
       * When downloading from http or ftp addresses, the target will be the server's filesystem for remote clients; otherwise it is the local filesystem.
       * When smb:// URLs are specified; they will be access from the server's network for remote clients, otherwise the source will be from the local network.
       * smb:// URLs target download will be the local filesystem. Only one download process is allowed per-UI using the 'FileDownload' API calls. If you wish
       * to handle multiple downloads; then use the 'BackgroundFileDownload' API calls.
       * @param ServerAddress the address of the SageTV server to download from, or null if you're using SageTVClient and you want to download from the server you're connected to, or a valid smb, http or ftp URL
       * @param SourceFile the file path on the server you want to download
       * @param DestFile the destination file for the file download
       * @return true if the copy process was successfully started, false if the file doesn't exist on the server or it couldn't be contacted
       *
       * @declaration public boolean StartFileDownload(String ServerAddress, String SourceFile, java.io.File DestFile);
       */

      /**
       * Instructs the file transfer engine to download the specified file from the server to the local destination file. You may also
       * download from remote http:// or ftp:// addresses; in that case just specify the URL in the ServerAddress argument and leave sourceFile as null.
       * When downloading from http or ftp addresses, the target will be the server's filesystem for remote clients; otherwise it is the local filesystem.
       * When smb:// URLs are specified; they will be access from the server's network for remote clients, otherwise the source will be from the local network.
       * smb:// URLs target download will be the local filesystem. Only one download process is allowed per-UI using the 'FileDownload' API calls. If you wish
       * to handle multiple downloads; then use the 'BackgroundFileDownload' API calls.
       * @param ServerAddress the address of the SageTV server to download from, or null if you're using SageTVClient and you want to download from the server you're connected to, or a valid smb, http or ftp URL
       * @param SourceFile the file path on the server you want to download
       * @param DestFile the destination file for the file download
       * @param RequestProperties a Properties object that specifies the request properties to use in an HTTP download request, can be null
       * @return true if the copy process was successfully started, false if the file doesn't exist on the server or it couldn't be contacted
       * @since 9.0
       *
       * @declaration public boolean StartFileDownload(String ServerAddress, String SourceFile, java.io.File DestFile, java.util.Properties RequestProperties);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.util.Properties props = null;
        if (curNumberOfParameters == 4)
          props = (java.util.Properties) stack.pop();
        java.io.File dest = getFile(stack);
        String src = getString(stack);
        String serverName = getString(stack);
        return FileDownloader.getFileDownloader(stack.getUIMgrSafe()).downloadFile(serverName, src, dest, false, props);
      }});
    rft.put(new PredefinedJEPFunction("Global", "StartBackgroundFileDownload", new String[] { "ServerAddress", "SourceFile", "DestFile", "RequestProperties" })
    {
      /**
       * Instructs the file transfer engine to download the specified file from the server to the local destination file. You may also
       * download from remote http:// or ftp:// addresses; in that case just specify the URL in the ServerAddress argument and leave sourceFile as null.
       * When downloading from http or ftp addresses, the target will be the server's filesystem for remote clients; otherwise it is the local filesystem.
       * When smb:// URLs are specified; they will be access from the server's network for remote clients, otherwise the source will be from the local network.
       * smb:// URLs target download will be the local filesystem. This call is different then the regular StartFileDownload API call because it allows for multiple
       * downloads to occur at the same time. The 'key' for the downloads are the DestFile objects.
       * @param ServerAddress the address of the SageTV server to download from, or null if you're using SageTVClient and you want to download from the server you're connected to, or a valid smb, http or ftp URL
       * @param SourceFile the file path on the server you want to download
       * @param DestFile the destination file for the file download
       * @param RequestProperties a Properties object that specifies the request properties to use in an HTTP download request, can be null
       * @return true if the copy process was successfully started, false if the file doesn't exist on the server or it couldn't be contacted
       * @since 7.0
       *
       * @declaration public boolean StartBackgroundFileDownload(String ServerAddress, String SourceFile, java.io.File DestFile, java.util.Properties RequestProperties);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.util.Properties props = (java.util.Properties) stack.pop();
        java.io.File dest = getFile(stack);
        String src = getString(stack);
        String serverName = getString(stack);
        return new FileDownloader(null).downloadFile(serverName, src, dest, false, props);
      }});
    rft.put(new PredefinedJEPFunction("Global", "StartCircularFileDownload", -1, new String[] { "ServerAddress", "SourceFile", "DestFile" })
    {
      /**
       * Instructs the file transfer engine to download the specified file from the server to the local destination file. You may also
       * download from remote http:// or ftp:// addresses; in that case just specify the URL in the ServerAddress argument and leave sourceFile as null.
       * When downloading from http or ftp addresses, the target will be the server's filesystem for remote clients; otherwise it is the local filesystem.
       * When smb:// URLs are specified; they will be access from the server's network for remote clients, otherwise the source will be from the local network.
       * smb:// URLs target download will be the local filesystem.
       * The 'Circular' version of this API call will write to a temporary circular file; this is designed for systems with limited storage capacity.
       * This version of the API call may not be used by SageTVClient (if it is; then it will internally switch to the non-circular file method)
       * @param ServerAddress the address of the SageTV server to download from, or null if you're using SageTVClient and you want to download from the server you're connected to, or a valid smb, http or ftp URL
       * @param SourceFile the file path on the server you want to download
       * @param DestFile the destination file for the file download
       * @return true if the copy process was successfully started, false if the file doesn't exist on the server or it couldn't be contacted
       * @since 6.4
       *
       * @declaration public boolean StartCircularFileDownload(String ServerAddress, String SourceFile, java.io.File DestFile);
       */

      /**
       * Instructs the file transfer engine to download the specified file from the server to the local destination file. You may also
       * download from remote http:// or ftp:// addresses; in that case just specify the URL in the ServerAddress argument and leave sourceFile as null.
       * When downloading from http or ftp addresses, the target will be the server's filesystem for remote clients; otherwise it is the local filesystem.
       * When smb:// URLs are specified; they will be access from the server's network for remote clients, otherwise the source will be from the local network.
       * smb:// URLs target download will be the local filesystem.
       * The 'Circular' version of this API call will write to a temporary circular file; this is designed for systems with limited storage capacity.
       * This version of the API call may not be used by SageTVClient (if it is; then it will internally switch to the non-circular file method)
       * @param ServerAddress the address of the SageTV server to download from, or null if you're using SageTVClient and you want to download from the server you're connected to, or a valid smb, http or ftp URL
       * @param SourceFile the file path on the server you want to download
       * @param DestFile the destination file for the file download
       * @param RequestProperties a Properties object that specifies the request properties to use in an HTTP download request, can be null
       * @return true if the copy process was successfully started, false if the file doesn't exist on the server or it couldn't be contacted
       * @since 9.0
       *
       * @declaration public boolean StartCircularFileDownload(String ServerAddress, String SourceFile, java.io.File DestFile, java.util.Properties RequestProperties);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.util.Properties props = null;
        if (curNumberOfParameters == 4)
          props = (java.util.Properties) stack.pop();
        java.io.File dest = getFile(stack);
        String src = getString(stack);
        String serverName = getString(stack);
        return FileDownloader.getFileDownloader(stack.getUIMgrSafe()).downloadFile(serverName, src, dest, true, props);
      }});
    rft.put(new PredefinedJEPFunction("Global", "CancelFileDownload")
    {
      /**
       * Cancels a previous request that was made to perform a file download
       *
       * @declaration public void CancelFileDownload();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Sage.DBG) System.out.println("Cancelling current file download operation.");
        FileDownloader.getFileDownloader(stack.getUIMgrSafe()).cancel();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "CancelBackgroundFileDownload", new String[] { "DestFile" })
    {
      /**
       * Cancels a previous request that was made to perform a background file download.
       * @param DestFile the destination file specified in the original download request
       * @since 7.0
       *
       * @declaration public void CancelBackgroundFileDownload(java.io.File DestFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File dFile = getFile(stack);
        if (Sage.DBG) System.out.println("Cancelling current file download operation for:" + dFile);
        FileDownloader downer = FileDownloader.getFileDownloader(dFile);
        if (downer != null)
          downer.cancel();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetFileDownloadStatus")
    {
      /**
       * Gets the current status of a previously invoked file download process.
       * @return true if the file download process is completed and was a success, "Error" will be the prefix if it was a failure,
       *          otherwise a localized status message indicating progress is returned
       *
       * @declaration public Object GetFileDownloadStatus();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (FileDownloader.getFileDownloader(stack.getUIMgrSafe()).isComplete())
        {
          if (FileDownloader.getFileDownloader(stack.getUIMgrSafe()).wasSuccessful())
            return Boolean.TRUE;
          else
            return "Error: " + FileDownloader.getFileDownloader(stack.getUIMgrSafe()).getStatusMessage();
        }
        else
          return FileDownloader.getFileDownloader(stack.getUIMgrSafe()).getStatusMessage();
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetBackgroundFileDownloadStatus", new String[] { "DestFile" })
    {
      /**
       * Gets the current status of a previously invoked file background download process.
       * @param DestFile the destination file specified in the original download request
       * @return true if the file download process is completed and was a success, "Error" will be the prefix if it was a failure,
       *          otherwise a localized status message indicating progress is returned
       * @since 7.0
       *
       * @declaration public Object GetBackgroundFileDownloadStatus(java.io.File DestFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File dFile = getFile(stack);
        FileDownloader downer = FileDownloader.getFileDownloader(dFile);
        if (downer != null)
        {
          if (downer.isComplete())
          {
            if (downer.wasSuccessful())
              return Boolean.TRUE;
            else
              return "Error: " + downer.getStatusMessage();
          }
          else
            return downer.getStatusMessage();
        }
        else
        {
          Object rv = FileDownloader.getBGDownloadResult(dFile);
          if (rv == null)
            return Sage.rez("Error: Invalid Download Target Specified");
          else
            return rv;
        }
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetFileDownloadStreamTime")
    {
      /**
       * Gets the current stream time of a previously invoked file download process. This is only valid
       * for when the file being downloaded is an FLV, MP3, MP4 or Quicktime file
       * @return the stream time in milliseconds of the current download; or zero if it doesn't know
       * @since 6.3
       *
       * @declaration public long GetFileDownloadStreamTime();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(FileDownloader.getFileDownloader(stack.getUIMgrSafe()).getLastDownloadTimestamp());
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsFileDownloadProgressivePlay")
    {
      /**
       * Returns whether or not the current file being downloaded can be played back while being downloaded. This is only valid
       * for when the file being downloaded is an FLV, MP3, MP4 or Quicktime file. It will always be true for MP3 &amp; FLV files;
       * and for MP4/Quicktime files it'll be true if the sample section is before the movie data in the file. If this is true
       * and the download stream time is greater than zero; then it's OK to start progressive playback of downloaded media.
       * @return true if the current file being downloaded can potentially be played back while being downloaded, false if it definitely cannot be
       * @since 6.3
       *
       * @declaration public boolean IsFileDownloadProgressivePlay();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(FileDownloader.getFileDownloader(stack.getUIMgrSafe()).isProgressivePlay());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetBackgroundFileDownloadStreamTime", new String[] { "DestFile" })
    {
      /**
       * Gets the current stream time of a previously invoked file download process. This is only valid
       * for when the file being downloaded is an FLV, MP3, MP4 or Quicktime file
       * @param DestFile the destination file specified in the original download request
       * @return the stream time in milliseconds of the current download; or zero if it doesn't know or the download is completed
       * @since 7.0
       *
       * @declaration public long GetBackgroundFileDownloadStreamTime(java.io.File DestFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File dFile = getFile(stack);
        FileDownloader downer = FileDownloader.getFileDownloader(dFile);
        if (downer != null)
          return new Long(downer.getLastDownloadTimestamp());
        else
          return new Long(0);
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsBackgroundFileDownloadProgressivePlay", new String[] { "DestFile" })
    {
      /**
       * Returns whether or not the current file being downloaded can be played back while being downloaded. This is only valid
       * for when the file being downloaded is an FLV, MP3, MP4 or Quicktime file. It will always be true for MP3 &amp; FLV files;
       * and for MP4/Quicktime files it'll be true if the sample section is before the movie data in the file. If this is true
       * and the download stream time is greater than zero; then it's OK to start progressive playback of downloaded media.
       * @param DestFile the destination file specified in the original download request
       * @return true if the current file being downloaded can potentially be played back while being downloaded, false if it definitely cannot be or is an invalid or completed download
       * @since 7.0
       *
       * @declaration public boolean IsBackgroundFileDownloadProgressivePlay(java.io.File DestFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File dFile = getFile(stack);
        FileDownloader downer = FileDownloader.getFileDownloader(dFile);
        if (downer != null)
          return Boolean.valueOf(downer.isProgressivePlay());
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetBackgroundFileDownloadTotalSize", new String[] { "DestFile" })
    {
      /**
       * Gets the total size of the specified background download target. This may not always be known in cases
       * where the server does not indicate the specific length of the file.
       * @param DestFile the destination file specified in the original download request
       * @return the size in bytes of the remote file being downloaded; zero if the download specified is complete or invalid
       * @since 7.0
       *
       * @declaration public long GetBackgroundFileDownloadTotalSize(java.io.File DestFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File dFile = getFile(stack);
        FileDownloader downer = FileDownloader.getFileDownloader(dFile);
        if (downer != null)
          return new Long(downer.getRemoteSize());
        else
          return new Long(0);
      }});
    rft.put(new PredefinedJEPFunction("Global", "SetRemoteEventEncryptionEnabled", new String[] { "EnableEncryption" })
    {
      /**
       * This is used to enable/disable encryption on the event channel for the SageTV MiniClient. The MiniClient is used
       * for the media extenders and for placeshifting. When using a non-local MiniClient connection, or if local MiniClient
       * connections are configured to require authentication; the event channel between the two will start off in an encrypted
       * mode so that a password can be exchanged.  This API call can then be used with a false argument to disable encryption
       * of the event channel in order to increase performance. It can also be used to re-enable encryption at a later time if
       * sensitive information is going to be transmitted again.
       *
       * @param EnableEncryption true if the MiniClient event channel should start encrypting events, false if it should stop
       * @return true if the MiniClient supports encryption and the operation should succeed, the connection will terminate if it fails
       * @since 4.1.7
       *
       * @declaration public boolean SetRemoteEventEncryptionEnabled(boolean EnableEncryption);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean b = Catbert.evalBool(stack.pop());
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          Object rv = new Boolean(((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).setEventEncryption(b));
          // Force a UI refresh so that the event gets sent to the client
          // Fixes a bug where local FS operations would hang if encryption was on; but even if you turned it off it
          // might still be on because a UI update had not been sent yet. So this refresh fixes that.
          PseudoMenu currUI = stack.getUIMgrSafe().getCurrUI();
          if (currUI != null) currUI.refresh();
          return rv;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "CachePlaceshifterLogin")
    {
      /**
       * This is used to store an authentication block on the current client that it can use in the future to automatically
       * connect to this server w/out having to login. The channel must be encrypted when this call is made. See the API call
       * SetRemoteEventEncryptionEnabled for more details on that.
       *
       * @since 6.6
       *
       * @declaration public void CachePlaceshifterLogin();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).cacheAuthenticationNow();
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "CanCachePlaceshifterLogin")
    {
      /**
       * This indicates whether the server and the client both support caching of placeshifter logins.
       * @return true if both the server and client support caching of placeshifter login information
       * @since 6.6
       *
       * @declaration public boolean CanCachePlaceshifterLogin();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          return Boolean.valueOf(((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).canCacheAuthentication());
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "ReloadSystemHooks")
    {
      /**
       * This is a Windows only API call which tells SageTV to unload and then reload any system hooks it has installed.
       * This allows reconfiguration of parameters in the UI and then realization of those changes on the fly.
       *
       * @since 4.1.13
       *
       * @declaration public void ReloadSystemHooks();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager ui = stack.getUIMgr();
        if (ui != null)
          ui.reloadSystemHooks();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "UpdateLocatorServer")
    {
      /**
       * This API call can be used to tell SageTV to do an update with the Locator server right now.
       * It's useful for when you change external network configuration stuff like ports or IPs.
       *
       * @since 4.1.13
       *
       * @declaration public void UpdateLocatorServer();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SageTV.forceLocatorUpdate();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetFullUIWidth")
    {
      /**
       * Returns the width in pixels of the user interface for the calling SageTV UI context.
       * @return the width in pixels of the user interface for the calling SageTV UI context
       *
       * @since 5.1
       *
       * @declaration public int GetFullUIWidth();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null)
          return new Integer(0);
        else
          return new Integer(uiMgr.getRootPanel().getWidth());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetFullUIHeight")
    {
      /**
       * Returns the height in pixels of the user interface for the calling SageTV UI context.
       * @return the height in pixels of the user interface for the calling SageTV UI context
       *
       * @since 5.1
       *
       * @declaration public int GetFullUIHeight();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null)
          return new Integer(0);
        else
          return new Integer(uiMgr.getRootPanel().getHeight());
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetDisplayResolutionWidth")
    {
      /**
       * Returns the width in pixels of the current display resolution set
       * @return the width in pixels of the current display resolution set
       *
       * @since 5.1
       *
       * @declaration public int GetDisplayResolutionWidth();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null)
          return new Integer(0);
        else
        {
          java.awt.Dimension d = uiMgr.getUIDisplayResolution();
          return (d == null) ? new Integer(720) : new Integer(d.width);
        }
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetDisplayResolutionHeight")
    {
      /**
       * Returns the height in pixels of the current display resolution set
       * @return the height in pixels of the current display resolution set
       *
       * @since 5.1
       *
       * @declaration public int GetDisplayResolutionHeight();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null)
          return new Integer(0);
        else
        {
          java.awt.Dimension d = uiMgr.getUIDisplayResolution();
          return (d == null) ? new Integer(480) : new Integer(d.height);
        }
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetDisplayResolution")
    {
      /**
       * Returns a string describing the current display resolution set
       * @return a string describing the current display resolution set
       *
       * @since 6.3
       *
       * @declaration public String GetDisplayResolution();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null)
          return "";
        else
        {
          sage.media.format.VideoFormat d = uiMgr.getDisplayResolution();
          return (d == null) ? "" : d.getFormatName();
        }
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetDisplayResolutionOptions")
    {
      /**
       * Returns a list of the possible display resolutions. This is currently only used
       * on media extender devices that have adjustable output resolution
       * @return an array of the display resolution names that can be used, null if this change is not supported
       *
       * @since 6.0
       *
       * @declaration public String[] GetDisplayResolutionOptions();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          return ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).getResolutionOptions();
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetDisplayResolutionDetails", new String[] { "Resolution" })
    {
      /**
       * Returns the detailed format description for the specific resolution returned from GetDisplayResolutionOptions()
       * NOTE: This API call is only valid on embedded platforms.
       * @param Resolution the resolution to get the details for
       * @return a the detailed format description for a specific Resolution
       *
       * @since 6.4
       *
       * @declaration public String GetDisplayResolutionDetails(String Resolution);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        getString(stack);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetPreferredDisplayResolutions")
    {
      /**
       * Returns a list of the preferred display resolutions. This is currently only used
       * on media extender devices that have adjustable output resolution. This information is obtained from the HDMI/DVI connector
       * @return a list of the preferred display resolutions
       *
       * @since 6.3
       *
       * @declaration public String[] GetPreferredDisplayResolutions();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          return ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).getPreferredResolutionOptions();
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "SetDisplayResolution", new String[] { "Resolution" })
    {
      /**
       * Sets the current output display resolution. This is currently only used
       * on media extender devices that have adjustable output resolution.
       * @param Resolution this must be a value from {@link #GetDisplayResolutionOptions GetDisplayResolutionOptions()} and should be the new desired output resolution
       *
       * @since 6.0
       *
       * @declaration public void SetDisplayResolution(String Resolution);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String newRes = getString(stack);
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).pushResolutionChange(newRes);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "DiscoverSageTVServers", new String[] { "Timeout" })
    {
      /**
       * Returns a list of the SageTV servers on the network. Each item will be "name;IP address"
       * @param Timeout the timeout for the discovery process in milliseconds
       * @return an array of the SageTV servers on the network. Each item will be "name;IP address"
       *
       * @since 6.3
       *
       * @declaration public String[] DiscoverSageTVServers(long Timeout);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long timeout = getLong(stack);
        java.util.ArrayList servers = new java.util.ArrayList();
        if (Sage.DBG) System.out.println("Sending out discovery packets to find SageTVPlaceshifter/Extender Servers...");
        java.net.DatagramSocket sock = null;
        try
        {
          // Try on the encoder discovery port which is less likely to be in use
          try
          {
            sock = new java.net.DatagramSocket(8271);
          }
          catch (java.net.BindException be2)
          {
            // Just create it wherever
            sock = new java.net.DatagramSocket();
          }
          java.net.DatagramPacket pack = new java.net.DatagramPacket(new byte[512], 512);
          byte[] data = pack.getData();
          data[0] = 'S';
          data[1] = 'T';
          data[2] = 'V';
          data[3] = 1;
          pack.setLength(32);
          sock.setBroadcast(true);
          // Find the broadcast address for this subnet.
          pack.setAddress(java.net.InetAddress.getByName("255.255.255.255"));
          pack.setPort(31100);
          sock.send(pack);
          long startTime = System.currentTimeMillis();
          do
          {
            int currTimeout = (int)Math.max(1, (startTime + timeout) - System.currentTimeMillis());
            sock.setSoTimeout(currTimeout);
            sock.receive(pack);
            if (pack.getLength() >= 4)
            {
              if (Sage.DBG) System.out.println("Discovery packet received:" + pack);
              if (data[0] == 'S' && data[1] == 'T' && data[2] == 'V' && data[3] == 2)
              {
                // Make sure it's not us
                String had = pack.getAddress().getHostAddress();
                if (!IOUtils.isLocalhostAddress(pack.getAddress()))
                {
                  StringBuffer si = new StringBuffer();
                  String theName = pack.getAddress().getHostName();

                  si.append(theName == null ? had : theName);
                  si.append(";");
                  si.append(had);
                  if (data[3] == 3)
                  {
                    // This is an embedded server so we would use the FAT client mode here
                    si.append(":" + SageTV.DEFAULT_SAGETV_PORT);
                  }
                  //if (pack.getLength() >= 15) // it also has the port in it
                  {
                    //	si.port = ((data[13] & 0xFF) << 8) | (data[14] & 0xFF);
                  }
                  if (Sage.DBG) System.out.println("Added server info:" + si);
                  servers.add(si.toString());
                }
              }
            }
          } while (true);//(startTime + timeout > System.currentTimeMillis());
        }
        catch (Exception e)
        {
          //System.out.println("Error discovering servers:" + e);
        }
        finally
        {
          if (sock != null)
          {
            try
            {
              sock.close();
            }catch (Exception e){}
            sock = null;
          }
        }
        return servers.toArray(Pooler.EMPTY_STRING_ARRAY);
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsEmbeddedSystem")
    {
      /**
       * Returns true if running on an 'embedded' platform. This will be true for the standalone environment on products like the HD300
       * and false otherwise. Used for making STVs that have different options based on whether they're on a PC vs. embedded system.
       * @return true if running on an embedded system, false otherwise
       * @since 7.0
       *
       * @declaration public boolean IsEmbeddedSystem();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        return Boolean.FALSE;
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "IsEmbeddedServer", true)
    {
      /**
       * Returns true if running on an 'embedded' platform in standalone mode or running as a SageTV Client that is connected to a server running on an embedded platform.
       * This will be true for the standalone environment on products like the HD300
       * and false otherwise. Used for making STVs that have different options based on whether they're on a PC vs. embedded system.
       * @return true if running against an embedded server, false otherwise
       * @since 7.1
       *
       * @declaration public boolean IsEmbeddedServer();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        return Boolean.FALSE;
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "IsPVR", true)
    {
      boolean forcedPVRMode = true;
      {
        String res = Sage.get("forced_pvr_mode", null);
        if (res != null)
          forcedPVRMode = "true".equalsIgnoreCase(res);
      }
      /**
       * Returns true if running on a system or connected to a server that has PVR functionality. Used for making STVs that have different options based on whether
       * or not PVR functionality is available. Even if no capture devices are configured and the system is PVR capable, this will still return true.
       * @return true if PVR is available, false otherwise
       * @since 7.1
       *
       * @declaration public boolean IsPVR();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        return forcedPVRMode ? Boolean.TRUE : Boolean.FALSE;
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "GetHotplugStorageMap")
    {
      /**
       * Returns a map of name-&gt;file for any hotplug storage devies on the system. The
       * names are user-presentable and the path is the java.io.File root which corresponds to the root of that device.
       * NOTE: This is only valid on embedded platforms.
       * @return a Map of name-&gt;file for hotplugged storage devices
       *
       * @since 6.4
       *
       * @declaration public java.util.Map GetHotplugStorageMap();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return NewStorageDeviceDetector.getInstance().getDeviceMap();
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "IsDoingLibraryImportScan", true)
    {
      /**
       * Returns true if the server is currently scanning the import directories for new files
       * @return true if the server is currently scanning the import directories for new files
       *
       * @since 6.6
       *
       * @declaration public boolean IsDoingLibraryImportScan();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().isDoingImportScan() ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "SwitchEmbeddedModeTo", new String[] { "NewMode" })
    {
      /**
       * Executes a predefined application in the context of an embedded client. This is used for launching things
       * like Netflix, YouTube Leanback and also specifying alternate modes the embedded client can exit into.
       * @param NewMode the name of the application
       *
       * @since 8.0
       *
       * @declaration public void SwitchEmbeddedModeTo(String NewMode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String newMode = getString(stack);
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          if ("netflix".equals(newMode))
          {
            // Specify that we launched Netflix from a list of Apps since that's how we do it in the UI
            newMode = "netflix --ui-query-string=source_type=2";
          }
          else if (newMode.startsWith("youtube"))
          {
            if (!Sage.getBoolean("enable_youtube_leanback", false))
            {
              if (Sage.DBG) System.out.println("ERROR: Requested to launch youtube, but it is disabled!");
              return null;
            }
            if (Sage.DBG) System.out.println("Testing YouTube connectivity before launching app...");
            try
            {
              java.net.InetSocketAddress sa = new java.net.InetSocketAddress("www.youtube.com", 80);
              java.net.Socket ytTest = new java.net.Socket();
              ytTest.connect(sa, 10000);
              if (Sage.DBG) System.out.println("Connection succeeded!");
              ytTest.close();
            }
            catch (Exception e)
            {
              // Display an error in the UI about this
              Catbert.processUISpecificHook("MediaPlayerError", new Object[] { Sage.rez("GENERAL_ERROR_TITLE"), Sage.rez("YT_CONNECTION_ERROR") },
                  stack.getUIMgr(), true);
              return null;
            }
          }
          ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).switchAppMode(newMode);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "PrepareForFirmwareLoad", true)
    {
      /**
       * Prepares the device for a firmware download &amp; flash. This internally will stop the library importing process,
       * empty all of the database tables, clear the UI caches, and then force a complete garbage collection.
       * NOTE: This is only valid on embedded platforms.
       *
       * @since 7.0
       *
       * @declaration public void PrepareForFirmwareLoad();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Global", "AreScreenshotsSupported")
    {
      /**
       * Returns true if it's possible to take screenshots of the UI using the API on this UI client
       * @return true if it's possible to take screenshots of the UI using the API on this UI client
       * @since 8.0
       *
       * @declaration public boolean AreScreenshotsSupported();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr != null && uiMgr.getUIClientType() == UIClient.REMOTE_UI)
        {
          return ((MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine()).supportsScreenshots() ? Boolean.TRUE : Boolean.FALSE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "SaveScreenshotToFile", new String[] { "FilePath" })
    {
      /**
       * Takes a screenshot of the current UI and saves it to the specified file in PNG format. This is only supported
       * on clients that return true to the API call {@link #AreScreenshotsSupported AreScreenshotsSupported()}.
       * @param FilePath the file path to save the screenshot to (should have a .png file extension)
       * @return true if the screenshot was successfully saved to the specified file, false otherwise
       * @since 8.0
       *
       * @declaration public boolean SaveScreenshotToFile(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        java.io.File f = getFile(stack);
        if (uiMgr != null && uiMgr.getUIClientType() == UIClient.REMOTE_UI)
        {
          return ((MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine()).saveScreenshotToFile(f) ? Boolean.TRUE : Boolean.FALSE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Global", "GetEmbeddedPanel")
    {
      /**
       * Gets a java.awt.Panel which can be used for embedding Java UI elements directly into SageTV
       * @return a java.awt.Panel object which can be sized, made visible and have children added to it
       *
       * @declaration public java.awt.Panel GetEmbeddedPanel();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        return stack.getUIMgrSafe().getEmbeddedPanel();
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "SetEmbeddedPanelBounds", new String[] { "x", "y", "width", "height"})
    {
      /**
       * Sets the bounding region to be used for the embedded panel. These are floating point numbers relative to the total size of the UI.
       * @param x the relative X position (0.0-1.0)
       * @param y the relative Y position (0.0-1.0)
       * @param width the relative width (0.0-1.0)
       * @param height the relative height (0.0-1.0)
       * @since 6.4
       *
       * @declaration public void SetEmbeddedPanelBounds(float x, float y, float width, float height);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        float h = getFloat(stack);
        float w = getFloat(stack);
        float y = getFloat(stack);
        float x = getFloat(stack);
        stack.getUIMgrSafe().setEmbeddedBounds(x, y, w, h);
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "IsClientDisconnected")
    {
      /**
       * Returns true if this is an instance of SageTVClient (or a remote client connected via SageTVClient)
       * and there is currently not a connection to the server. This should be used during startup to go to the
       * server connection lost menu for remote clients connected through SageTVClient (SageTVClient itself will do it automatically).
       * @return true if the SageTVClient connection is currently disconnected
       * @since 8.0
       *
       * @declaration public boolean IsClientDisconnected();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        if (!Sage.client || SageTV.neddy == null)
          return Boolean.FALSE;
        return SageTV.neddy.isClientConnected() ? Boolean.FALSE : Boolean.TRUE;
      }
    });
    rft.put(new PredefinedJEPFunction("Global", "GetClientServerConnectionProgress")
    {
      /**
       * Returns a string describing the state of the client/server connection process.
       * NOTE: This is only useful on embedded with a fat client setup.
       * @return a string describing the state of the client/server connection process, null if this cannot be determined properly
       * @since 8.0
       *
       * @declaration public String GetClientServerConnectionProgress();
       */

      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return null;
      }});

    /**
     * Returns a string describing the state of the epg system.
     * @return a string describing the Epg state one of: [Idle, Maintenance, Updating]
     * @since 8.1
     *
     * @declaration public String GetEPGUpdateState();
     */

    rft.put(new PredefinedJEPFunction("Global", "GetEPGUpdateState", true) {
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return EPG.getInstance().getEpgStateString();
      }});
    rft.put(new PredefinedJEPFunction("Global", "IsSDEPGServiceAvailable", true)
    {
      /**
       * Returns true if the Schedules Direct EPG service is available.
       * This is determined by trying to communicate with Schedules Direct if an unexpired token is
       * present. If no token currently exists or it is expired, a new token will be acquired. If
       * the token is unable to be obtained for any reason, the service is considered unavailable.
       * @return true if the Schedules Direct EPG service is configured and able to authenticate, false otherwise
       * @since 9.0
       *
       * @declaration public boolean IsSDEPGServiceAvailable();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return sage.epg.sd.SDRipper.isAvailable();
      }});
    /*
		rft.put(new PredefinedJEPFunction("Global", "", -1)
		{public Object runSafely(Catbert.FastStack stack) throws Exception{

			}});
     */
  }
}
