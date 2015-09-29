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

import sage.*;

/**
 * Favorites are objects which are used for repeated recording in SageTV
 */
public class FavoriteAPI {
  private FavoriteAPI() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Favorite", "IsFirstRunsAndReRuns", 1, new String[] { "Favorite" })
    {
      /**
       * Returns true if this Favorite is configured to record both first runs and reruns.
       * @param Favorite the Favorite object
       * @return true if this Favorite is configured to record both first runs AND reruns, false otherwise
       *
       * @declaration public boolean IsFirstRunsAndReRuns(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Agent a = (Agent) stack.pop();
        return Boolean.valueOf(a != null && a.firstRunsAndReruns());
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "IsFirstRunsOnly", 1, new String[] { "Favorite" })
    {
      /**
       * Returns true if this Favorite is configured to record first runs but not reruns.
       * @param Favorite the Favorite object
       * @return true if this Favorite is configured to record first runs only, false otherwise
       *
       * @declaration public boolean IsFirstRunsOnly(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(((Agent) stack.pop()).isFirstRunsOnly());
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "IsReRunsOnly", 1, new String[] { "Favorite" })
    {
      /**
       * Returns true if this Favorite is configured to record reruns but not first runs.
       * @param Favorite the Favorite object
       * @return true if this Favorite is configured to record reruns only, false otherwise
       *
       * @declaration public boolean IsReRunsOnly(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(((Agent) stack.pop()).isReRunsOnly());
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "IsFirstRuns", 1, new String[] { "Favorite" })
    {
      /**
       * Returns true if this Favorite is configured to record first runs (it may or may not record reruns)
       * @param Favorite the Favorite object
       * @return true if this Favorite is configured to record first runs, false otherwise
       *
       * @declaration public boolean IsFirstRuns(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(!((Agent) stack.pop()).isReRunsOnly());
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "IsReRuns", 1, new String[] { "Favorite" })
    {
      /**
       * Returns true if this Favorite is configured to record reruns (it may or may not record first runs)
       * @param Favorite the Favorite object
       * @return true if this Favorite is configured to record reruns, false otherwise
       *
       * @declaration public boolean IsReRuns(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(!((Agent) stack.pop()).isFirstRunsOnly());
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "IsAutoDelete", 1, new String[] { "Favorite" })
    {
      /**
       * Returns true if SageTV is allowed to automatically delete recordings of this
       * Favorite when it needs more disk space. If this is false, then SageTV will never automatically
       * delete files recorded for this Favorite; the user will have to delete the files themself.
       * @param Favorite the Favorite object
       * @return true if this Favorite is configured for auto delete, false otherwise
       *
       * @declaration public boolean IsAutoDelete(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(!((Agent) stack.pop()).testAgentFlag(Agent.DONT_AUTODELETE_FLAG));
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "IsDeleteAfterAutomaticConversion", 1, new String[] { "Favorite" })
    {
      /**
       * If an automatic conversion quality is set for this Favorite; then this will return true if
       * the original file will be deleted after that conversion is performed
       * @param Favorite the Favorite object
       * @return true if this Favorite is configured to delete the original file after automatic conversion
       * @since 7.0
       *
       * @declaration public boolean IsDeleteAfterAutomaticConversion(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(((Agent) stack.pop()).testAgentFlag(Agent.DELETE_AFTER_CONVERT_FLAG));
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "IsFavoriteEnabled", 1, new String[] { "Favorite" })
    {
      /**
       * Returns true if SageTV considers this favorite when performing scheduling.
       * @param Favorite the Favorite object
       * @return true if this Favorite is enabled, false otherwise
       * @since 9.0
       *
       * @declaration   public boolean IsFavoriteEnabled(Favorite Favorite);
       */
        @Override
        public Object runSafely(Catbert.FastStack stack) throws Exception{
            //Note here that the value of the test is negated
            return Boolean.valueOf(!((Agent) stack.pop()).testAgentFlag(Agent.DISABLED_FLAG));
        }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetKeepAtMost", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the maximum number of recordings that match this Favorite that should be kept on disk. If AutoDelete is
       * set to true then SageTV will continue to record new airings of this Favorite as they air, and delete the oldest
       * recording on disk if it hits the keep at most limit. If AutoDelete is false then SageTV will stop recording this Favorite
       * once it has this many recordings on disk
       * @param Favorite the Favorite object
       * @return the maximum number of recordings SageTV should keep on disk of this Favorite, 0 if it is unlimited
       *
       * @declaration public int GetKeepAtMost(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(((Agent) stack.pop()).getAgentFlag(Agent.KEEP_AT_MOST_MASK));
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteDescription", 1, new String[] { "Favorite" })
    {
      /**
       * Returns a String that describes this Favorite.
       * @param Favorite the Favorite object
       * @return a descriptive string for this Favorite
       *
       * @declaration public String GetFavoriteDescription(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Agent a = (Agent) stack.pop();
        return (a == null) ? "" : a.getCause();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoritePresentationName", 1, new String[] { "Favorite" })
    {
      /**
       * Returns a String that describes this Favorite in a 'clean' way. Should be used in conjunction with
       * GetFavoritePresentationPrefix to create a complete string for presenting a Favorite named uniquely.
       * @param Favorite the Favorite object
       * @return a descriptive string for this Favorite
       * @since 8.0
       *
       * @declaration public String GetFavoritePresentationName(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Agent a = (Agent) stack.pop();
        return (a == null) ? "" : a.getCause(true);
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoritePresentationPrefix", 1, new String[] { "Favorite" })
    {
      /**
       * Returns a String that describes this type of Favorite this is, such as Show, Keyword, Person, etc.
       * @param Favorite the Favorite object
       * @return a descriptive string for the type of Favorite
       * @since 8.0
       *
       * @declaration public String GetFavoritePresentationPrefix(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Agent a = (Agent) stack.pop();
        return (a == null) ? "" : a.getTypeName();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteTitle", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the title that an Airing must match to be included in this Favorite.
       * @param Favorite the Favorite object
       * @return the title that an Airing must match to be included in this Favorite, returns the empty string if the title isn't a field that needs to match
       *
       * @declaration public String GetFavoriteTitle(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Agent a = (Agent) stack.pop();
        return (a == null) ? "" : a.getTitle();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteCategory", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the category that an Airing must match to be included in this Favorite.
       * @param Favorite the Favorite object
       * @return the category that an Airing must match to be included in this Favorite, returns the empty string if category isn't a field that needs to match
       *
       * @declaration public String GetFavoriteCategory(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return ((Agent) stack.pop()).getCategory();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteSubCategory", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the subcategory that an Airing must match to be included in this Favorite.
       * @param Favorite the Favorite object
       * @return the subcategory that an Airing must match to be included in this Favorite, returns the empty string if subcategory isn't a field that needs to match
       *
       * @declaration public String GetFavoriteSubCategory(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return ((Agent) stack.pop()).getSubCategory();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoritePerson", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the person that an Airing must have to be included in this Favorite. The person may also be restricted by their role in the content.
       * @param Favorite the Favorite object
       * @return the person that an Airing must have to be included in this Favorite, returns the empty string if person isn't a field that needs to match
       *
       * @declaration public Object GetFavoritePerson(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Person rv = ((Agent) stack.pop()).getPersonObj();
        return (rv != null) ? ((Object) rv) : "";
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteRated", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the rating that an Airing must match to be included in this Favorite.
       * @param Favorite the Favorite object
       * @return the rating that an Airing must match to be included in this Favorite, returns the empty string if rating isn't a field that needs to match
       *
       * @declaration public String GetFavoriteRated(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return ((Agent) stack.pop()).getRated();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoritePersonRole", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the role that an Airing must have the Favorite Person in to be included in this Favorite. This only applies if a person is set for this Favorite.
       * @param Favorite the Favorite object
       * @return the role that the favorite person for an Airing must have to be included in this Favorite, returns the empty string if role doesn't matter
       *
       * @declaration public String GetFavoritePersonRole(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Show.getRoleString(((Agent) stack.pop()).getRole());
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteYear", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the year that an Airing must match to be included in this Favorite.
       * @param Favorite the Favorite object
       * @return the year that an Airing must match to be included in this Favorite, returns the empty string if year isn't a field that needs to match
       *
       * @declaration public String GetFavoriteYear(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return ((Agent) stack.pop()).getYear();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteParentalRating", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the parental rating that an Airing must match to be included in this Favorite.
       * @param Favorite the Favorite object
       * @return the parental rating that an Airing must match to be included in this Favorite, returns the empty string if parental rating isn't a field that needs to match
       *
       * @declaration public String GetFavoriteParentalRating(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return ((Agent) stack.pop()).getPR();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteChannel", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the channel name (call sign) that an Airing must be on to be included in this Favorite.
       * @param Favorite the Favorite object
       * @return the channel name that an Airing must be on to be included in this Favorite, returns the empty string if channel doesn't matter; for mult-channel favorites this will be a semicolon or comma-delimited list of channel names
       *
       * @declaration public String GetFavoriteChannel(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return ((Agent) stack.pop()).getChannelName();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteKeyword", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the keyword string that an Airing must match to be included in this Favorite. This is case insensitive. Double quotes
       * can be used to require word to be in a certain order. Otherwise; each individual word is searched for in the Airing's details and must
       * match a whole word for the match to succeed.  The * character can be used to match 0 or more characters. The ? character can be used
       * to match a single character. Regex is the matching language used for this so any other regex parsing characters besides * and ? can be used.
       * All of the fields of the object are searched as part of this.
       * @param Favorite the Favorite object
       * @return the keyword that an Airing must match to be included in this Favorite, returns the empty string if keyword doesn't matter
       *
       * @declaration public String GetFavoriteKeyword(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Agent a = (Agent) stack.pop();
        return (a == null) ? "" : a.getKeyword();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteNetwork", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the network name that an Airing must be on to be included in this Favorite.
       * @param Favorite the Favorite object
       * @return the network name that an Airing must be on to be included in this Favorite, returns the empty string if network doesn't matter
       *
       * @declaration public String GetFavoriteNetwork(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return ((Agent) stack.pop()).getNetwork();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteTimeslot", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the timeslot that an Airing must be in to be included in this Favorite. It just needs to overlap the timeslot.
       * @param Favorite the Favorite object
       * @return the timeslot that an Airing must be in to be included in this Favorite, returns the empty string if timeslot doesn't matter
       *
       * @declaration public String GetFavoriteTimeslot(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Agent a = (Agent) stack.pop();
        int slotType = a.getSlotType();
        int[] slots = a.getTimeslots();
        if (slotType == 0 || slots == null || slots.length == 0)
          return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < slots.length; i++)
        {
          if (i > 0)
            sb.append(';');
          sb.append(BigBrother.getTimeslotString(slotType, slots[i]));
        }
        return sb.toString();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetStartPadding", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the amount of time any recording for this Favorite should start before the actual Airing begins.
       * @param Favorite the Favorite object
       * @return the amount of time any recording for this Favorite should start before the actual Airing begins, in milliseconds
       *
       * @declaration public long GetStartPadding(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(((Agent) stack.pop()).getStartPadding());
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetStopPadding", 1, new String[] { "Favorite" })
    {
      /**
       * Returns the amount of time any recording for this Favorite should stop after the actual Airing ends.
       * @param Favorite the Favorite object
       * @return the amount of time any recording for this Favorite should stop after the actual Airing ends, in milliseconds
       *
       * @declaration public long GetStopPadding(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(((Agent) stack.pop()).getStopPadding());
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteQuality", 1, new String[] { "Favorite" })
    {
      /**
       * Gets the name of the recording quality that should be used when recording this Favorite.
       * @param Favorite the Favorite object
       * @return the name of the recording quality that should be used when recording this Favorite, the empty string if the default recording quality should be used
       *
       * @declaration public String GetFavoriteQuality(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return ((Agent) stack.pop()).getRecordingQuality();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteAutomaticConversionFormat", 1, new String[] { "Favorite" })
    {
      /**
       * Gets the name of the format that recordings of this Favorite will automatically be converted to when they have completed recording. It will return an empty
       * string if automatic conversion is disabled for this Favorite
       * @param Favorite the Favorite object
       * @return the name of the transcode format that should be used when converting this Favorite, the empty string if no automatic conversion should be done
       * @since 7.0
       *
       * @declaration public String GetFavoriteAutomaticConversionFormat(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return ((Agent) stack.pop()).getAutoConvertFormat();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteAutomaticConversionDestination", 1, new String[] { "Favorite" })
    {
      /**
       * Gets the directory that automatic conversions of this favorite should be saved to.
       * Returns the empty string when using the same directory the recording is in.
       * @param Favorite the Favorite object
       * @return the destination directory for the automatic conversions of this Favorite; or null if its the same as the recording directory
       * @since 7.0
       *
       * @declaration public java.io.File GetFavoriteAutomaticConversionDestination(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return ((Agent) stack.pop()).getAutoConvertDest();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetStartPadding", 2, new String[] { "Favorite", "StartPadding" }, true)
    {
      /**
       * Sets the amount of time any recording for this Favorite should start before the actual Airing begins.
       * @param Favorite the Favorite object
       * @param StartPadding the amount of time any recording for this Favorite should start before the actual Airing begins, in milliseconds
       *
       * @declaration public void SetStartPadding(Favorite Favorite, long StartPadding);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long q = ((Number) stack.pop()).longValue();
        Agent a = (Agent) stack.pop();
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
          Carny.getInstance().setStartPadding(a, q);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetStopPadding", 2, new String[] { "Favorite", "StopPadding" }, true)
    {
      /**
       * Sets the amount of time any recording for this Favorite should stop after the actual Airing ends.
       * @param Favorite the Favorite object
       * @param StopPadding the amount of time any recording for this Favorite should stop after the actual Airing ends, in milliseconds
       *
       * @declaration public void SetStopPadding(Favorite Favorite, long StopPadding);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long q = ((Number) stack.pop()).longValue();
        Agent a = (Agent) stack.pop();
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
          Carny.getInstance().setStopPadding(a, q);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteQuality", 2, new String[] { "Favorite", "Quality" }, true)
    {
      /**
       * Sets the name of the recording quality that should be used when recording this Favorite.
       * @param Favorite the Favorite object
       * @param Quality the name of the recording quality that should be used when recording this Favorite, the empty string if the default recording quality should be used
       *
       * @declaration public void SetFavoriteQuality(Favorite Favorite, String Quality);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String q = (String) stack.pop();
        Agent a = (Agent) stack.pop();
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Carny.getInstance().setRecordingQuality(a, q);
          Scheduler.getInstance().kick(false); // because it can change the encoders for it
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteAutomaticConversionFormat", 2, new String[] { "Favorite", "Format" }, true)
    {
      /**
       * Sets the name of the format to transcode this Favorite to after a recording has been completed. Format names should come from {@link TranscodeAPI#GetTranscodeFormats GetTranscodeFormats()}.
       * Use the empty string or null to disable automatic conversion for this Favorite.
       * @param Favorite the Favorite object
       * @param Format the name of the transcode format to use when converting recordings of this Favorite; the empty string or null to disable automatic conversion
       * @since 7.0
       *
       * @declaration public void SetFavoriteAutomaticConversionFormat(Favorite Favorite, String Format);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String q = (String) stack.pop();
        Agent a = (Agent) stack.pop();
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()) && Permissions.hasPermission(Permissions.PERMISSION_CONVERSION, stack.getUIMgr()))
          Carny.getInstance().setAutoConvertFormat(a, q);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteAutomaticConversionDestination", 2, new String[] { "Favorite", "Folder" }, true)
    {
      /**
       * Sets the directory that automatic conversions of this favorite should be saved to.
       * Use null to use the same directory the recording is in.
       * @param Favorite the Favorite object
       * @param Folder the destination directory for the automatic conversions of this Favorite; null to use the same directory as the recording directory
       * @since 7.0
       *
       * @declaration public void SetFavoriteAutomaticConversionDestination(Favorite Favorite, java.io.File Folder);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        Agent a = (Agent) stack.pop();
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()) && Permissions.hasPermission(Permissions.PERMISSION_CONVERSION, stack.getUIMgr()))
          a.setAutoConvertDest(f);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "CreateFavoritePriority", 2, new String[] { "HigherPriorityFavorite", "LowerPriorityFavorite" }, true)
    {
      /**
       * Establishes a priority of one Favorite over another. This will take undo any previous prioritization that it directly conflicts with.
       * Favorites with a higher priority will be recorded over ones with a lower priority if there's a case where both cannot be recorded at once.
       * @param HigherPriorityFavorite the Favorite object that you wish to make a higher priority than the other specified Favorite object
       * @param LowerPriorityFavorite the Favorite object that you wish to make a lower priority than the other specified Favorite object
       *
       * @declaration public void CreateFavoritePriority(Favorite HigherPriorityFavorite, Favorite LowerPriorityFavorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Agent q = (Agent) stack.pop();
        Agent a = (Agent) stack.pop();
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
          Carny.getInstance().createPriority(a, q);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "ConfirmManualRecordOverFavoritePriority", 2, new String[] { "ManualRecordAiring", "FavoriteAiring" }, true)
    {
      /**
       * Confirms that the user is aware that a manual recording they've selected creates a conflict with this favorite recording. This is purely
       * for notifcation purposes.
       * @param ManualRecordAiring the manual record Airing object to confirm the recording priority of
       * @param FavoriteAiring the Airing for the Favorite that won't be recorded due to the manual record
       *
       * @declaration public void ConfirmManualRecordOverFavoritePriority(Airing ManualRecordAiring, Airing FavoriteAiring);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing q = getAir(stack);
        Airing a = getAir(stack);
        if (a != null)
        {
          ManualRecord mr = Wizard.getInstance().getManualRecord(a);
          if (mr != null && q != null && Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
          {
            mr.bully(q);
            Scheduler.getInstance().kick(false);
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetDontAutodelete", 2, new String[] { "Favorite", "DontAutoDelete" }, true)
    {
      /**
       * Sets whether or not SageTV is allowed to automatically delete recordings of this
       * Favorite when it needs more disk space. If this is true, then SageTV will never automatically
       * delete files recorded for this Favorite; the user will have to delete the files themself.
       * @param Favorite the Favorite object
       * @param DontAutoDelete true if this Favorite is configured to NOT auto delete, false otherwise
       *
       * @declaration public void SetDontAutodelete(Favorite Favorite, boolean DontAutoDelete);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean b = evalBool(stack.pop());
        Agent a = (Agent) stack.pop();
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Carny.getInstance().setAgentFlags(a, Agent.DONT_AUTODELETE_FLAG, b?Agent.DONT_AUTODELETE_FLAG:0);
          Carny.getInstance().kick();
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetDeleteAfterAutomaticConversion", 2, new String[] { "Favorite", "DeleteAfterAutomaticConversion" }, true)
    {
      /**
       * Sets whether or not SageTV should delete the original file after doing an automatic conversion
       * for this Favorite
       * @param Favorite the Favorite object
       * @param DeleteAfterAutomaticConversion true if this Favorite is configured to delete the source file after automatic conversion
       * @since 7.0
       *
       * @declaration public void SetDeleteAfterAutomaticConversion(Favorite Favorite, boolean DeleteAfterAutomaticConversion);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean b = evalBool(stack.pop());
        Agent a = (Agent) stack.pop();
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()) && Permissions.hasPermission(Permissions.PERMISSION_CONVERSION, stack.getUIMgr()))
          Carny.getInstance().setAgentFlags(a, Agent.DELETE_AFTER_CONVERT_FLAG, b?Agent.DELETE_AFTER_CONVERT_FLAG:0);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetKeepAtMost", 2, new String[] { "Favorite", "NumberToKeep" }, true)
    {
      /**
       * Sets the maximum number of recordings that match this Favorite that should be kept on disk. If AutoDelete is
       * set to true then SageTV will continue to record new airings of this Favorite as they air, and delete the oldest
       * recording on disk if it hits the keep at most limit. If AutoDelete is false then SageTV will stop recording this Favorite
       * once it has this many recordings on disk
       * @param Favorite the Favorite object
       * @param NumberToKeep the maximum number of recordings SageTV should keep on disk of this Favorite, 0 if it is unlimited
       *
       * @declaration public void SetKeepAtMost(Favorite Favorite, int NumberToKeep);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int q = getInt(stack);
        q = Math.min(Math.max(0, q), 63); // this uses 6 bits to store itself
        Agent a = (Agent) stack.pop();
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Carny.getInstance().setAgentFlags(a, Agent.KEEP_AT_MOST_MASK, q);
          Carny.getInstance().kick();
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetRunStatus", 3, new String[] { "Favorite", "FirstRuns", "ReRuns" }, true)
    {
      /**
       * Sets whether first runs, reruns or both types of airings should be recorded for this Favorite. If both arguments
       * are false, SageTV will record both first runs and reruns.
       * @param Favorite the Favorite object
       * @param FirstRuns true if first runs should be recorded with this favorite, false otherwise
       * @param ReRuns true if reruns should be recorded with this Favorite, false otherwise
       * @return true if the change was successful, false if the change would alter this Favorite to be identical to an existing Favorite or if the user doesn't have permissions to make this change
       *
       * @declaration public boolean SetRunStatus(Favorite Favorite, boolean FirstRuns, boolean ReRuns);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean rb = evalBool(stack.pop());
        boolean fb = evalBool(stack.pop());
        Agent a = (Agent) stack.pop();
        int newMask = a.getAgentMask();
        if (rb && !fb)
        {
          newMask = newMask | Agent.RERUN_MASK;
          newMask = newMask & (~Agent.FIRSTRUN_MASK);
        }
        else if (!rb && fb)
        {
          newMask = newMask | Agent.FIRSTRUN_MASK;
          newMask = newMask & (~Agent.RERUN_MASK);
        }
        else
          newMask = newMask & ~(Agent.RERUN_MASK | Agent.FIRSTRUN_MASK);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent noob = Carny.getInstance().updateFavorite(a, newMask, a.getTitle(), a.getCategory(), a.getSubCategory(),
              a.getPersonObj(), a.getRole(), a.getRated(), a.getYear(), a.getPR(), a.getNetwork(), a.getChannelName(),
              a.getSlotType(), a.getTimeslots(), a.getKeyword());
          return (noob == a) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteTitle", 2, new String[] { "Favorite", "Title" }, true)
    {
      /**
       * Sets the title that an Airing must match to be included in this Favorite.
       * @param Favorite the Favorite object
       * @param Title the title that an Airing must match to be included in this Favorite, use the empty string or null if the title isn't a field that needs to match
       * @return true if the change was successful, false if the change would alter this Favorite to be identical to an existing Favorite or if the user doesn't have permissions to make this change
       *
       * @declaration public boolean SetFavoriteTitle(Favorite Favorite, String Title);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        Agent a = (Agent) stack.pop();
        int newMask = a.getAgentMask();
        if (s != null && s.length() > 0)
          newMask = newMask | Agent.TITLE_MASK;
        else
          newMask = newMask & (~Agent.TITLE_MASK);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent noob = Carny.getInstance().updateFavorite(a, newMask, s, a.getCategory(), a.getSubCategory(),
              a.getPersonObj(), a.getRole(), a.getRated(), a.getYear(), a.getPR(), a.getNetwork(), a.getChannelName(),
              a.getSlotType(), a.getTimeslots(), a.getKeyword());
          return (noob == a) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteCategories", 3, new String[] { "Favorite", "Category", "SubCategory" }, true)
    {
      /**
       * Sets the category and optionally the subcategory that an Airing must match to be included in this Favorite.
       * @param Favorite the Favorite object
       * @param Category the category that an Airing must match to be included in this Favorite, use the empty string or null if the category isn't a field that needs to match
       * @param SubCategory the category that an Airing must match to be included in this Favorite, use the empty string the subcategory doesn't need to match
       * @return true if the change was successful, false if the change would alter this Favorite to be identical to an existing Favorite or if the user doesn't have permissions to make this change
       *
       * @declaration public boolean SetFavoriteCategories(Favorite Favorite, String Category, String SubCategory);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String sub = getString(stack);
        String scat = getString(stack);
        Agent a = (Agent) stack.pop();
        int newMask = a.getAgentMask();
        if (scat != null && scat.length() > 0)
          newMask = newMask | Agent.CATEGORY_MASK;
        else
          newMask = newMask & (~Agent.CATEGORY_MASK);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent noob = Carny.getInstance().updateFavorite(a, newMask, a.getTitle(), scat, sub,
              a.getPersonObj(), a.getRole(), a.getRated(), a.getYear(), a.getPR(), a.getNetwork(), a.getChannelName(),
              a.getSlotType(), a.getTimeslots(), a.getKeyword());
          return (noob == a) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoritePerson", 3, new String[] { "Favorite", "Person", "Role" }, true)
    {
      /**
       * Sets a person (and optionally the role the person must appear in) that must be in an Airing for it to be included in this Favorite
       * @param Favorite the Favorite object
       * @param Person the name of the person that needs to be included in an Airing for it to match this Favorite, use the empty string or null if person doesn't need to match
       * @param Role the name of the role the corresponding person needs to be in, or the emptry string or null if role doesn't matter
       * @return true if the change was successful, false if the change would alter this Favorite to be identical to an existing Favorite or if the user doesn't have permissions to make this change
       *
       * @declaration public boolean SetFavoritePerson(Favorite Favorite, Person Person, String Role);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int role = Show.getRoleForString(getString(stack));
        Person p = getPerson(stack);
        Agent a = (Agent) stack.pop();
        int newMask = a.getAgentMask();
        if (p != null)
          newMask = newMask | Agent.ACTOR_MASK;
        else
          newMask = newMask & (~Agent.ACTOR_MASK);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent noob = Carny.getInstance().updateFavorite(a, newMask, a.getTitle(), a.getCategory(), a.getSubCategory(),
              p, role, a.getRated(), a.getYear(), a.getPR(), a.getNetwork(), a.getChannelName(),
              a.getSlotType(), a.getTimeslots(), a.getKeyword());
          return (noob == a) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteRated", 2, new String[] { "Favorite", "Rated" }, true)
    {
      /**
       * Sets the rating that an Airing must match to be included in this Favorite.
       * @param Favorite the Favorite object
       * @param Rated the rating that an Airing must match to be included in this Favorite, use the empty string or null if rating isn't a field that needs to match
       * @return true if the change was successful, false if the change would alter this Favorite to be identical to an existing Favorite or if the user doesn't have permissions to make this change
       *
       * @declaration public boolean SetFavoriteRated(Favorite Favorite, String Rated);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        Agent a = (Agent) stack.pop();
        int newMask = a.getAgentMask();
        if (s != null && s.length() > 0)
          newMask = newMask | Agent.RATED_MASK;
        else
          newMask = newMask & (~Agent.RATED_MASK);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent noob = Carny.getInstance().updateFavorite(a, newMask, a.getTitle(), a.getCategory(), a.getSubCategory(),
              a.getPersonObj(), a.getRole(), s, a.getYear(), a.getPR(), a.getNetwork(), a.getChannelName(),
              a.getSlotType(), a.getTimeslots(), a.getKeyword());
          return (noob == a) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteYear", 2, new String[] { "Favorite", "Year" }, true)
    {
      /**
       * Sets the year that an Airing must match to be included in this Favorite.
       * @param Favorite the Favorite object
       * @param Year the year that an Airing must match to be included in this Favorite, use the empty string or null if year isn't a field that needs to match
       * @return true if the change was successful, false if the change would alter this Favorite to be identical to an existing Favorite or if the user doesn't have permissions to make this change
       *
       * @declaration public boolean SetFavoriteYear(Favorite Favorite, String Year);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        Agent a = (Agent) stack.pop();
        int newMask = a.getAgentMask();
        if (s != null && s.length() > 0)
          newMask = newMask | Agent.YEAR_MASK;
        else
          newMask = newMask & (~Agent.YEAR_MASK);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent noob = Carny.getInstance().updateFavorite(a, newMask, a.getTitle(), a.getCategory(), a.getSubCategory(),
              a.getPersonObj(), a.getRole(), a.getRated(), s, a.getPR(), a.getNetwork(), a.getChannelName(),
              a.getSlotType(), a.getTimeslots(), a.getKeyword());
          return (noob == a) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteParentalRating", 2, new String[] { "Favorite", "ParentalRating" }, true)
    {
      /**
       * Sets the parental rating that an Airing must match to be included in this Favorite.
       * @param Favorite the Favorite object
       * @param ParentalRating the parental rating that an Airing must match to be included in this Favorite, use the empty string or null if parental rating isn't a field that needs to match
       * @return true if the change was successful, false if the change would alter this Favorite to be identical to an existing Favorite or if the user doesn't have permissions to make this change
       *
       * @declaration public boolean SetFavoriteParentalRating(Favorite Favorite, String ParentalRating);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        Agent a = (Agent) stack.pop();
        int newMask = a.getAgentMask();
        if (s != null && s.length() > 0)
          newMask = newMask | Agent.PR_MASK;
        else
          newMask = newMask & (~Agent.PR_MASK);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent noob = Carny.getInstance().updateFavorite(a, newMask, a.getTitle(), a.getCategory(), a.getSubCategory(),
              a.getPersonObj(), a.getRole(), a.getRated(), a.getYear(), s, a.getNetwork(), a.getChannelName(),
              a.getSlotType(), a.getTimeslots(), a.getKeyword());
          return (noob == a) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteKeyword", new String[] { "Favorite", "Keyword" }, true)
    {
      /**
       * Sets the keyword string that an Airing must match to be included in this Favorite. This is case insensitive. Double quotes
       * can be used to require word to be in a certain order. Otherwise; each individual word is searched for in the Airing's details and must
       * match a whole word for the match to succeed.  The * character can be used to match 0 or more characters. The ? character can be used
       * to match a single character. Regex is the matching language used for this so any other regex parsing characters besides * and ? can be used.
       * All of the fields of the object are searched as part of this.
       * @param Favorite the Favorite object
       * @param Keyword the keyword that an Airing must match to be included in this Favorite, use the empty string or null if keyword doesn't matter
       * @return true if the change was successful, false if the change would alter this Favorite to be identical to an existing Favorite or if the user doesn't have permissions to make this change
       *
       * @declaration public boolean SetFavoriteKeyword(Favorite Favorite, String Keyword);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        Agent a = (Agent) stack.pop();
        int newMask = a.getAgentMask();
        if (s != null && s.length() > 0)
          newMask = newMask | Agent.KEYWORD_MASK;
        else
          newMask = newMask & (~Agent.KEYWORD_MASK);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent noob = Carny.getInstance().updateFavorite(a, newMask, a.getTitle(), a.getCategory(), a.getSubCategory(),
              a.getPersonObj(), a.getRole(), a.getRated(), a.getYear(), a.getPR(), a.getNetwork(), a.getChannelName(),
              a.getSlotType(), a.getTimeslots(), s);
          return (noob == a) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteChannel", 2, new String[] { "Favorite", "Channel" }, true)
    {
      /**
       * Sets the channel name (call sign) that an Airing must be on to be included in this Favorite.
       * @param Favorite the Favorite object
       * @param Channel the channel name (String) or Channel object (Channel) that an Airing must be on to be included in this Favorite, use null or the empty string if channel doesn't matter; you may also use a semicolon or comma-delimited list of channel names for mult-channel favorites
       * @return true if the change was successful, false if the change would alter this Favorite to be identical to an existing Favorite or if the user doesn't have permissions to make this change
       *
       * @declaration public boolean SetFavoriteChannel(Favorite Favorite, Object Channel);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        String s;
        if (o instanceof Channel)
          s = ((Channel) o).getName();
        else if (o != null)
          s = o.toString();
        else
          s = null;
        Agent a = (Agent) stack.pop();
        int newMask = a.getAgentMask();
        if (s != null && s.length() > 0)
          newMask = newMask | Agent.CHANNEL_MASK;
        else
          newMask = newMask & (~Agent.CHANNEL_MASK);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent noob = Carny.getInstance().updateFavorite(a, newMask, a.getTitle(), a.getCategory(), a.getSubCategory(),
              a.getPersonObj(), a.getRole(), a.getRated(), a.getYear(), a.getPR(), a.getNetwork(), s,
              a.getSlotType(), a.getTimeslots(), a.getKeyword());
          return (noob == a) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteNetwork", 2, new String[] { "Favorite", "Network" }, true)
    {
      /**
       * Sets the network name that an Airing must be on to be included in this Favorite.
       * @param Favorite the Favorite object
       * @param Network the network name that an Airing must be on to be included in this Favorite, use null or the empty string if network doesn't matter
       * @return true if the change was successful, false if the change would alter this Favorite to be identical to an existing Favorite or if the user doesn't have permissions to make this change
       *
       * @declaration public boolean SetFavoriteNetwork(Favorite Favorite, String Network);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        Agent a = (Agent) stack.pop();
        int newMask = a.getAgentMask();
        if (s != null && s.length() > 0)
          newMask = newMask | Agent.NETWORK_MASK;
        else
          newMask = newMask & (~Agent.NETWORK_MASK);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent noob = Carny.getInstance().updateFavorite(a, newMask, a.getTitle(), a.getCategory(), a.getSubCategory(),
              a.getPersonObj(), a.getRole(), a.getRated(), a.getYear(), a.getPR(), s, a.getChannelName(),
              a.getSlotType(), a.getTimeslots(), a.getKeyword());
          return (noob == a) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteTimeslot", 2, new String[] { "Favorite", "Timeslot" }, true)
    {
      /**
       * Sets the timeslot that an Airing must be in to be included in this Favorite. It just needs to overlap the timeslot.
       * @param Favorite the Favorite object
       * @param Timeslot the timeslot that an Airing must be in to be included in this Favorite, use null or the empty string if timeslot doesn't matter.
       *         The timeslot should be in one of three formats: 1) Day Time, 2) Day, 3) Time. Where Day is a day of the week, and Time is an
       *         hour of the day such as 3pm or 8:00 (if the user's locale uses am/pm then it'll be like 3pm, otherwise it'll use like 8:00).
       *         Multiple timeslots are allowed and should be sent as a semicolon delimited list; an Airing only needs to overlap one of the timeslots to match the Favorite then.
       *		   Multiple timeslots must also all be of the same type (i.e. all DayTime, all Day or all Time).
       * @return true if the change was successful, false if the change would alter this Favorite to be identical to an existing Favorite or if the user doesn't have permissions to make this change
       *
       * @declaration public boolean SetFavoriteTimeslot(Favorite Favorite, String Timeslot);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String slotString = getString(stack);
        if (slotString == null) slotString = "";
        java.util.StringTokenizer toker = new java.util.StringTokenizer(slotString, ";");
        int slotType = 0;
        java.util.ArrayList slots = new java.util.ArrayList();
        Agent a = (Agent) stack.pop();
        while (toker.hasMoreTokens())
        {
          int[] ts = BigBrother.getTimeslotForString(toker.nextToken());
          if (slotType == 0)
            slotType = ts[0];
          else if (slotType != ts[0])
            throw new IllegalArgumentException("All timeslots passed into SetFavoriteTimeslot must be of the same type!");
          if (slotType != 0)
            slots.add(new Integer(ts[1]));
        }
        int newMask = a.getAgentMask();
        newMask = newMask & ~(Agent.DAYSLOT_MASK | Agent.TIMESLOT_MASK | Agent.FULLSLOT_MASK);
        if (slotType == BigBrother.DAY_ALIGN)
          newMask = newMask | Agent.DAYSLOT_MASK;
        else if (slotType == BigBrother.TIME_ALIGN)
          newMask = newMask | Agent.TIMESLOT_MASK;
        else if (slotType == BigBrother.FULL_ALIGN)
          newMask = newMask | Agent.FULLSLOT_MASK;
        int[] rawSlots = null;
        if (slots.size() > 0)
        {
          rawSlots = new int[slots.size()];
          for (int i = 0; i < slots.size(); i++)
            rawSlots[i] = ((Integer) slots.get(i)).intValue();
        }
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent noob = Carny.getInstance().updateFavorite(a, newMask, a.getTitle(), a.getCategory(), a.getSubCategory(),
              a.getPersonObj(), a.getRole(), a.getRated(), a.getYear(), a.getPR(), a.getNetwork(), a.getChannelName(),
              slotType, rawSlots, a.getKeyword());
          return (noob == a) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteEnabled", 2, new String[] { "Favorite", "Enabled" }, true)
    {
      /**
       * Sets whether or not SageTV will use this favorite when scheduling recordings
       * @param Favorite the Favorite object
       * @param Enabled true if this Favorite is to be used for scheduling, false otherwise
       * @since 9.0
       *
       * @declaration   public void SetFavoriteEnabled(Favorite Favorite, boolean Enabled);
       */
      @Override
      public Object runSafely(Catbert.FastStack stack) throws Exception{
          boolean b = evalBool(stack.pop());
          Agent a = (Agent) stack.pop();
          if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
          {
              Carny.getInstance().enableFavorite(a, b);
              Carny.getInstance().kick();
          }
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteForAiring", 1, new String[] { "Airing" })
    {
      /**
       * Gets the Favorite that matches this Airing if one exists.
       * @param Airing the Airing object to get a matching Favorite for
       * @return a Favorite object that matches this Airing or null if there is no such Favorite
       *
       * @declaration public Favorite GetFavoriteForAiring(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        Agent bond = Carny.getInstance().getCauseAgent(a);
        if (bond != null && bond.isFavorite())
          return bond;
        else
        {
          // Search through all of the Favorite objects to find the correct one
          Agent[] favs = Wizard.getInstance().getFavorites();
          StringBuffer sbCache = new StringBuffer();
          for (int i = 0; i < favs.length; i++)
          {
            if (favs[i].followsTrend(a, false, sbCache))
              return favs[i];
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoritesForAiring", 1, new String[] { "Airing" })
    {
      /**
       * Gets the list of all Favorites that match this Airing
       * @param Airing the Airing object to get the matching Favorites for
       * @return a Favorite array who's elements all match this Airing
       * @since 8.0
       *
       * @declaration public Favorite[] GetFavoritesForAiring(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        java.util.ArrayList rv = new java.util.ArrayList();
        // Search through all of the Favorite objects to find the correct one
        Agent[] favs = Wizard.getInstance().getFavorites();
        StringBuffer sbCache = new StringBuffer();
        for (int i = 0; i < favs.length; i++)
        {
          if (favs[i].followsTrend(a, false, sbCache))
            rv.add(favs[i]);
        }
        return (Agent[]) rv.toArray(new Agent[0]);
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "DoesFavoriteMatchAiring", new String[] { "Favorite", "Airing" })
    {
      /**
       * Returns true if the specified Favorite object matches the specified Airing object.
       * @param Favorite the Favorite object to test
       * @param Airing the Airing object to test
       * @return true if the specified Favorite matches the specified Airing, false otherwise
       *
       * @declaration public boolean DoesFavoriteMatchAiring(Favorite Favorite, Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        Agent bond = (Agent) stack.pop();
        return (a != null && bond != null && bond.followsTrend(a, false, null)) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "IsFavoriteObject", new String[] { "Favorite" })
    {
      /**
       * Returns true if the argument is a Favorite object, false otherwise
       * @param Favorite an object to test to see if its an instance of Favorite
       * @return true if the argument is an instance of a Favorite object, false otherwise
       *
       * @declaration public boolean IsFavoriteObject(Object Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof sage.vfs.MediaNode)
          o = ((sage.vfs.MediaNode) o).getDataObject();
        return Boolean.valueOf(o instanceof Agent);
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "UpdateFavorite", -1, new String[] {
        "Favorite", "Title", "FirstRuns", "Reruns", "Category", "SubCategory", "Person", "RoleForPerson",
        "Rated", "Year", "ParentalRating", "Network", "ChannelCallSign", "Timeslot", "Keyword"}, true)
    {
      /**
       * Updates all of the fields of a Favorite that can be used to match. The rules for the arguments are specified in the
       * individual methods that allow settting of the corresponding parameter.
       * @param Favorite the Favorite object to update
       * @param Title the title that an Airing must match to be included in this Favorite, use the empty string or null if the title isn't a field that needs to match
       * @param FirstRuns true if this Favorite should match First Runs, false otherwise
       * @param ReRuns true if this Favorite should match ReRuns, false otherwise
       * @param Category the category that an Airing must match to be included in this Favorite, use the empty string or null if category isn't a field that needs to match
       * @param SubCategory the subcategory that an Airing must match to be included in this Favorite, use the empty string or null if subcategory isn't a field that needs to match
       * @param Person the name of the person that needs to be included in an Airing for it to match this Favorite, use the empty string or null if person doesn't need to match
       * @param RoleForPerson the name of the role the corresponding person needs to be in, or the emptry string or null if role doesn't matter
       * @param Rated the rating that an Airing must match to be included in this Favorite, use the empty string or null if rating isn't a field that needs to match
       * @param Year the year that an Airing must match to be included in this Favorite, use the empty string or null if year isn't a field that needs to match
       * @param ParentalRating the parental rating that an Airing must match to be included in this Favorite, use the empty string or null if parental rating isn't a field that needs to match
       * @param Network the network name that an Airing must be on to be included in this Favorite, use null or the empty string if network doesn't matter
       * @param ChannelCallSign the channel name that an Airing must be on to be included in this Favorite, use null or the empty string if channel doesn't matter; you may also use a semicolon or comma-delimited list of channel names for mult-channel favorites
       * @param Timeslot the timeslot that an Airing must be in to be included in this Favorite, use null or the empty string if timeslot doesn't matter.
       *         The timeslot should be in one of three formats: 1) Day Time, 2) Day, 3) Time. Where Day is a day of the week, and Time is an
       *         hour of the day such as 3pm or 8:00 (if the user's locale uses am/pm then it'll be like 3pm, otherwise it'll use like 8:00)
       *         Multiple timeslots are allowed and should be sent as a semicolon delimited list; an Airing only needs to overlap one of the timeslots to match the Favorite then.
       *		   Multiple timeslots must also all be of the same type (i.e. all DayTime, all Day or all Time).
       * @param Keyword the keyword that an Airing must match to be included in this Favorite, use the empty string or null if keyword doesn't matter
       * @return the updated Favorite object, or if the changes would make this Favorite the same as an existing Favorite, the other existing Favorite is returned, null is returned if there was no valid criteria specified in the arguments
       *
       * @declaration public Favorite UpdateFavorite(Favorite Favorite, String Title, boolean FirstRuns, boolean ReRuns, String Category, String SubCategory, Person Person, String RoleForPerson, String Rated, String Year, String ParentalRating, String Network, String ChannelCallSign, String Timeslot, String Keyword);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String keyw;
        if (curNumberOfParameters == 14)
          keyw = null;
        else
          keyw = getString(stack);
        String slotString = getString(stack);
        if (slotString == null) slotString = "";
        java.util.StringTokenizer toker = new java.util.StringTokenizer(slotString, ";");
        int slotType = 0;
        java.util.ArrayList slots = new java.util.ArrayList();
        while (toker.hasMoreTokens())
        {
          int[] ts = BigBrother.getTimeslotForString(toker.nextToken());
          if (slotType == 0)
            slotType = ts[0];
          else if (slotType != ts[0])
            throw new IllegalArgumentException("All timeslots passed into SetFavoriteTimeslot must be of the same type!");
          if (slotType != 0)
            slots.add(new Integer(ts[1]));
        }
        int[] rawSlots = null;
        if (slots.size() > 0)
        {
          rawSlots = new int[slots.size()];
          for (int i = 0; i < slots.size(); i++)
            rawSlots[i] = ((Integer) slots.get(i)).intValue();
        }
        String c = getString(stack);
        String net = getString(stack);
        String pr = getString(stack);
        String year = getString(stack);
        String rated = getString(stack);
        int role = Show.getRoleForString(getString(stack));
        Person person = getPerson(stack);
        String sub = getString(stack);
        String cat = getString(stack);
        boolean rr = evalBool(stack.pop());
        boolean fr = evalBool(stack.pop());
        String tit = getString(stack);
        Agent fav = (Agent) stack.pop();
        int mask = Agent.LOVE_MASK;
        if (rr) mask = mask | Agent.RERUN_MASK;
        if (fr) mask = mask | Agent.FIRSTRUN_MASK;
        if (tit != null && tit.length() > 0) mask = mask | Agent.TITLE_MASK;
        if (cat != null && cat.length() > 0) mask = mask | Agent.CATEGORY_MASK;
        if (person != null) mask = mask | Agent.ACTOR_MASK;
        if (rated != null && rated.length() > 0) mask = mask | Agent.RATED_MASK;
        if (year != null && year.length() > 0) mask = mask | Agent.YEAR_MASK;
        if (pr != null && pr.length() > 0) mask = mask | Agent.PR_MASK;
        if (c != null && c.length() > 0) mask = mask | Agent.CHANNEL_MASK;
        if (net != null && net.length() > 0) mask = mask | Agent.NETWORK_MASK;
        if (slotType == BigBrother.DAY_ALIGN) mask = mask | Agent.DAYSLOT_MASK;
        if (slotType == BigBrother.TIME_ALIGN) mask = mask | Agent.TIMESLOT_MASK;
        if (slotType == BigBrother.FULL_ALIGN) mask = mask | Agent.FULLSLOT_MASK;
        if (keyw != null && keyw.length() > 0) mask = mask | Agent.KEYWORD_MASK;
        if (mask == Agent.LOVE_MASK)
          return null; // invalid Fav, it would match everything
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent rv = Carny.getInstance().updateFavorite(fav, mask, tit, cat, sub, person, role, rated, year, pr, net, c, slotType, rawSlots, keyw);
          return rv;
        }
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteAirings", new String[] { "Favorite" }, true)
    {
      /**
       * Returns a list of all of the Airings in the database that match this Favorite.
       * @param Favorite the Favorite object
       * @return the list of Airings in the DB that match this Favorite
       *
       * @declaration public Airing[] GetFavoriteAirings(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Agent fav = (Agent) stack.pop();
        if (fav == null) return null;
        return fav.getRelatedAirings(Wizard.getInstance().getRawAccess(Wizard.AIRING_CODE,
            Wizard.AIRINGS_BY_CT_CODE), true, false, new StringBuffer());
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteID", new String[] { "Favorite" })
    {
      /**
       * Gets a unique ID for this Favorite which can be used with {@link #GetFavoriteForID GetFavoriteForID()} for retrieving the object later.
       * @param Favorite the Favorite object
       * @return the unique ID for this Favorite
       *
       * @declaration public int GetFavoriteID(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Agent fav = (Agent) stack.pop();
        if (fav == null) return null;
        return new Integer(fav.getID());
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteForID", new String[] { "FavoriteID" })
    {
      /**
       * Gets the Favorite object with the corresponding ID from the database. Use with {@link #GetFavoriteID GetFavoriteID()}
       * @param FavoriteID the ID to look up in the DB for a Favorite object
       * @return the Favorite object with the specified ID if it exists, null otherwise
       *
       * @declaration public Favorite GetFavoriteForID(int FavoriteID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int i = getInt(stack);
        Agent a = Wizard.getInstance().getAgentForID(i);
        return (a == null || !a.isFavorite()) ? null : a;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavorites")
    {
      /**
       * Gets all of the Favorite objects from the database
       * @return all of the Favorite objects in the database
       *
       * @declaration public Favorite[] GetFavorites();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getFavorites();
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "RemoveFavorite", 1, new String[] {"Favorite"}, true)
    {
      /**
       * Removes a Favorite object from the database. Airings matching this Favorite will not necesarilly be automatically recorded anymore (intelligent recording may still record them)
       * @param Favorite the Favorite object to remove
       *
       * @declaration public void RemoveFavorite(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Agent a = (Agent) stack.pop();
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Carny.getInstance().removeFavorite(a);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "AddFavorite", -1, new String[] {
        "Title", "FirstRuns", "Reruns", "Category", "SubCategory", "Person", "RoleForPerson",
        "Rated", "Year", "ParentalRating", "Network", "ChannelCallSign", "Timeslot", "Keyword"}, true)
    {
      /**
       * Creates a new Favorite object in SageTV. Airings that match this Favorite will be recorded and saved into the SageTV Recordings.
       * @param Title the title that an Airing must match to be included in this Favorite, use the empty string or null if the title isn't a field that needs to match
       * @param FirstRuns true if this Favorite should match First Runs, false otherwise
       * @param ReRuns true if this Favorite should match ReRuns, false otherwise (if both FirstRuns and ReRuns are false, then it will match both)
       * @param Category the category that an Airing must match to be included in this Favorite, use the empty string or null if category isn't a field that needs to match
       * @param SubCategory the subcategory that an Airing must match to be included in this Favorite, use the empty string or null if subcategory isn't a field that needs to match
       * @param Person the name of the person that needs to be included in an Airing for it to match this Favorite, use the empty string or null if person doesn't need to match
       * @param RoleForPerson the name of the role the corresponding person needs to be in, or the emptry string or null if role doesn't matter
       * @param Rated the rating that an Airing must match to be included in this Favorite, use the empty string or null if rating isn't a field that needs to match
       * @param Year the year that an Airing must match to be included in this Favorite, use the empty string or null if year isn't a field that needs to match
       * @param ParentalRating the parental rating that an Airing must match to be included in this Favorite, use the empty string or null if parental rating isn't a field that needs to match
       * @param Network the network name that an Airing must be on to be included in this Favorite, use null or the empty string if network doesn't matter
       * @param ChannelCallSign the channel name that an Airing must be on to be included in this Favorite, use null or the empty string if channel doesn't matter; you may also use a semicolon or comma-delimited list of channel names for multi-channel favorites
       * @param Timeslot the timeslot that an Airing must be in to be included in this Favorite, use null or the empty string if timeslot doesn't matter.
       *         The timeslot should be in one of three formats: 1) Day Time, 2) Day, 3) Time. Where Day is a day of the week, and Time is an
       *         hour of the day such as 3pm or 8:00 (if the user's locale uses am/pm then it'll be like 3pm, otherwise it'll use like 8:00)
       *         Multiple timeslots are allowed and should be sent as a semicolon delimited list; an Airing only needs to overlap one of the timeslots to match the Favorite then.
       *		   Multiple timeslots must also all be of the same type (i.e. all DayTime, all Day or all Time).
       * @param Keyword the keyword that an Airing must match to be included in this Favorite, use the empty string or null if keyword doesn't matter
       * @return the newly created Favorite object, null is returned if there was no valid criteria specified in the arguments
       *
       * @declaration public Favorite AddFavorite(String Title, boolean FirstRuns, boolean ReRuns, String Category, String SubCategory, Person Person, String RoleForPerson, String Rated, String Year, String ParentalRating, String Network, String ChannelCallSign, String Timeslot, String Keyword);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String keyw;
        if (curNumberOfParameters == 13)
          keyw = null;
        else
          keyw = getString(stack);
        String slotString = getString(stack);
        if (slotString == null) slotString = "";
        java.util.StringTokenizer toker = new java.util.StringTokenizer(slotString, ";");
        int slotType = 0;
        java.util.ArrayList slots = new java.util.ArrayList();
        while (toker.hasMoreTokens())
        {
          int[] ts = BigBrother.getTimeslotForString(toker.nextToken());
          if (slotType == 0)
            slotType = ts[0];
          else if (slotType != ts[0])
            throw new IllegalArgumentException("All timeslots passed into SetFavoriteTimeslot must be of the same type!");
          if (slotType != 0)
            slots.add(new Integer(ts[1]));
        }
        int[] rawSlots = null;
        if (slots.size() > 0)
        {
          rawSlots = new int[slots.size()];
          for (int i = 0; i < slots.size(); i++)
            rawSlots[i] = ((Integer) slots.get(i)).intValue();
        }
        String c = getString(stack);
        String net = getString(stack);
        String pr = getString(stack);
        String year = getString(stack);
        String rated = getString(stack);
        int role = Show.getRoleForString(getString(stack));
        Person person = getPerson(stack);
        String sub = getString(stack);
        String cat = getString(stack);
        boolean rr = evalBool(stack.pop());
        boolean fr = evalBool(stack.pop());
        String tit = getString(stack);
        int mask = Agent.LOVE_MASK;
        // Since the masks signify only, we should not set both if both are wanted
        if (rr && fr)
          rr = fr = false;
        if (rr) mask = mask | Agent.RERUN_MASK;
        if (fr) mask = mask | Agent.FIRSTRUN_MASK;
        if (tit != null && tit.length() > 0) mask = mask | Agent.TITLE_MASK;
        if (cat != null && cat.length() > 0) mask = mask | Agent.CATEGORY_MASK;
        if (person != null) mask = mask | Agent.ACTOR_MASK;
        if (rated != null && rated.length() > 0) mask = mask | Agent.RATED_MASK;
        if (year != null && year.length() > 0) mask = mask | Agent.YEAR_MASK;
        if (pr != null && pr.length() > 0) mask = mask | Agent.PR_MASK;
        if (c != null && c.length() > 0) mask = mask | Agent.CHANNEL_MASK;
        if (net != null && net.length() > 0) mask = mask | Agent.NETWORK_MASK;
        if (slotType == BigBrother.DAY_ALIGN) mask = mask | Agent.DAYSLOT_MASK;
        if (slotType == BigBrother.TIME_ALIGN) mask = mask | Agent.TIMESLOT_MASK;
        if (slotType == BigBrother.FULL_ALIGN) mask = mask | Agent.FULLSLOT_MASK;
        if (keyw != null && keyw.length() > 0) mask = mask | Agent.KEYWORD_MASK;
        if (mask == Agent.LOVE_MASK)
          return null; // invalid Fav, it would match everything
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          Agent rv = Carny.getInstance().addFavorite(mask, tit, cat, sub, person, role, rated, year, pr, net, c, slotType, rawSlots, keyw);
          return rv;
        }
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteProperty", 2, new String[] { "Favorite", "PropertyName" })
    {
      /**
       * Returns a property value for a specified Favorite. This must have been set using SetFavoriteProperty.
       * Returns the empty string when the property is undefined.
       * @param Favorite the Favorite object
       * @param PropertyName the name of the property
       * @return the property value for the specified Favorite, or the empty string if it is not defined
       * @since 7.0
       *
       * @declaration public String GetFavoriteProperty(Favorite Favorite, String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String prop = getString(stack);
        return ((Agent) stack.pop()).getProperty(prop);
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "SetFavoriteProperty", 3, new String[] { "Favorite", "PropertyName", "PropertyValue" }, true)
    {
      /**
       * Sets a property for this Favorite. This can be any name/value combination (but the name cannot be null). If the value is null;
       * then the specified property will be removed from this Favorite. This only impacts the return values from GetFavoriteProperty and has no other side effects.
       * @param Favorite the Favorite object
       * @param PropertyName the name of the property
       * @param PropertyValue the value of the property
       * @since 7.0
       *
       * @declaration public void SetFavoriteProperty(Favorite Favorite, String PropertyName, String PropertyValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String propV = getString(stack);
        String propN = getString(stack);
        Agent fav = (Agent) stack.pop();
        if (Permissions.hasPermission(Permissions.PERMISSION_EDITMETADATA, stack.getUIMgr()))
        {
          fav.setProperty(propN, propV);
          sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.FAVORITE_MODIFIED,
              new Object[] { sage.plugin.PluginEventManager.VAR_FAVORITE, fav });
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Favorite", "GetFavoriteProperties", 1, new String[] { "Favorite" })
    {
      /**
       * Returns a java.util.Properties object that has all of the user-set properties for this Favorite in it.
       * @param Favorite the Favorite object
       * @return a java.util.Properties object that has all of the user-set properties for this Favorite in it; this is a copy of the original one so it is safe to modify it
       * @since 7.1
       *
       * @declaration public java.util.Properties GetFavoriteProperties(Favorite Favorite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return ((Agent) stack.pop()).getProperties();
      }});
  }
}
