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

import sage.UIClient;

import sage.*;

/**
 * Airing is the 'meta' object used to access content.
 * <p>
 * An Airing can represent a specific time and Channel that a television Show is broadcast on.
 * Uniquely identified by its time-Channel overlap because only one thing can be broadcast on a Channel at any given time.
 * Airing's also represent the metadata that identify individual files.
 *<p>
 * SageTV will automatically convert the following types to Airing if used for a parameter that requires the Airing type:<p>
 * MediaFile - the Airing that represents the content is used<p>
 * java.io.File - the corresponding MediaFile (if it exists) is resolved and then its Airing is used
 */
public class AiringAPI{
  private AiringAPI() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Airing", "SetRecordingName", new String[] { "Airing", "Name" })
    {
      /**
       * Sets the name for this recording. For Timed Recordings this will effect the title &amp; associated attributes.
       * For ManualRecordings this will not have any side effects at all.
       * @param Airing the ManualRecord to set the name for
       * @param Name the name to set
       *
       * @declaration public void SetRecordingName(Airing Airing, String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String name = getString(stack);
        Airing a = getAir(stack);
        if (a != null)
        {
          ManualRecord mr = Wizard.getInstance().getManualRecord(a);
          if (mr != null)
          {
            mr.setName(name);
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetRecordingName", new String[] { "Airing" })
    {
      /**
       * Gets the name for this recording that was set via {@link #SetRecordingName SetRecordingName(Airing, String)}
       * @param Airing the ManualRecord to get the name for
       * @return the name of the ManualRecord or the empty string if the argument was not a manual record
       *
       * @declaration public String GetRecordingName(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        if (a != null)
        {
          ManualRecord mr = Wizard.getInstance().getManualRecord(a);
          if (mr != null)
          {
            return mr.getName();
          }
        }
        return "";
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetChannel", 1, new String[] { "Airing" })
    {
      /**
       * Gets the Channel that this Airing is on
       * @param Airing the Airing object
       * @return the Channel that this Airing is on
       *
       * @declaration public Channel GetChannel(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return (a == null) ? null : a.getChannel();
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringChannelName", 1, new String[] { "Airing" })
    {
      /**
       * Gets the name of the Channel that this Airing is on
       * @param Airing the Airing object
       * @return the name of the Channel that this Airing is on
       *
       * @declaration public String GetAiringChannelName(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return (a == null) ? "" : a.getChannelName();
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringChannelNumber", 1, new String[] { "Airing" })
    {
      /**
       * Gets the channel number that this Airing is on
       * @param Airing the Airing object
       * @return the channel number that this Airing is on
       *
       * @declaration public String GetAiringChannelNumber(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return (a == null) ? "" : a.getChannelNum(0);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringDuration", 1, new String[] { "Airing" })
    {
      /**
       * Gets the duration of this Airing in milliseconds
       * @param Airing the Airing object
       * @return the duration of this Airing in milliseconds
       *
       * @declaration public long GetAiringDuration(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return new Long(a == null ? 0 : a.getDuration());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringStartTime", 1, new String[] { "Airing" })
    {
      /**
       * Gets the start time of this Airing. The time is in Java time units, which are milliseconds since Jan 1, 1970 GMT
       * @param Airing the Airing object
       * @return the start time of this Airing
       *
       * @declaration public long GetAiringStartTime(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return new Long(a == null ? 0 : a.getStartTime());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringEndTime", 1, new String[] { "Airing" })
    {
      /**
       * Gets the end time of this Airing. The time is in Java time units, which are milliseconds since Jan 1, 1970 GMT
       * @param Airing the Airing object
       * @return the end time of this Airing
       *
       * @declaration public long GetAiringEndTime(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return new Long(a == null ? 0 : a.getEndTime());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetShow", 1, new String[] { "Airing" })
    {
      /**
       * Gets the Show object for this Airing which describes it in further detail (Show contains the title, actors, category, description, etc.)
       * @param Airing the Airing object
       * @return the Show object for this Airing
       *
       * @declaration public Show GetShow(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return a == null ? null : a.getShow();
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringRatings", 1, new String[] { "Airing" })
    {
      /**
       * Gets the list of the field values which correspond to parental ratings control for this Airing
       * @param Airing the Airing object
       * @return the list of the field values which correspond to parental ratings control for this Airing
       * @declaration public String[] GetAiringRatings(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return a == null ? Pooler.EMPTY_STRING_ARRAY : a.getRatingRestrictables();
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetScheduleStartTime", 1, new String[] { "Airing" })
    {
      /**
       * Get the start time for an airing accounting for any adjustments made due to Manual Recording stop/start time adjustments or adjustments due to favorite padding.
       * @param Airing the Airing object
       * @return the scheduling end time of the Airing
       *
       * @declaration public long GetScheduleStartTime(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return new Long(a == null ? 0 : a.getSchedulingStart());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetScheduleEndTime", 1, new String[] { "Airing" })
    {
      /**
       * Get the end time for an airing accounting for any adjustments made due to Manual Recording
       * stop/start time adjustments or adjustments due to favorite padding.
       * @param Airing the Airing object
       * @return the scheduling end time of the Airing
       *
       * @declaration public long GetScheduleEndTime(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return new Long(a == null ? 0 : a.getSchedulingEnd());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetScheduleDuration", 1, new String[] { "Airing" })
    {
      /**
       * Get the duration for an airing accounting for any adjustments made due to Manual
       * Recording stop/start time adjustments or adjustments due to favorite padding.
       * @param Airing the Airing object
       * @return the scheduling duration of the Airing
       *
       * @declaration public long GetScheduleDuration(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return new Long(a == null ? 0 : a.getSchedulingDuration());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetScheduleRecordingRecurrence", 1, new String[] { "Airing" })
    {
      /**
       * If this Airing is a time-based recording this will get a description of the recurrence frequency for its recording recurrence
       * @param Airing the Airing object
       * @return a description of the recurrence frequency for this Airing's recording recurrence, or the empty string if this is not recurring time-based recording
       * @declaration public String GetScheduleRecordingRecurrence(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ManualRecord mr = Wizard.getInstance().getManualRecord(getAir(stack));
        return (mr == null) ? "" : ManualRecord.getRecurrenceName(mr.getRecurrence());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "PrintAiringLong", 1, new String[] { "Airing" })
    {
      /**
       * Returns a lengthy string which is suitable for displaying information about this Airing. This contains nearly all the details of the Airing &amp; its Show
       * @param Airing the Airing object
       * @return a lengthy string which is suitable for displaying information about this Airing
       *
       * @declaration public String PrintAiringLong(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack); return (a==null)?"":a.getFullString();
      }});
    rft.put(new PredefinedJEPFunction("Airing", "PrintAiringMedium", 1, new String[] { "Airing" })
    {
      /**
       * Returns a string which is suitable for displaying information about this Airing. This contains the Airing's channel &amp; a short time string as well as the title &amp; episode name or a short description
       * @param Airing the Airing object
       * @return a string which is suitable for displaying information about this Airing
       *
       * @declaration public String PrintAiringMedium(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack); return (a==null)?"":a.getPartialString();
      }});
    rft.put(new PredefinedJEPFunction("Airing", "PrintAiringShort", 1, new String[] { "Airing" })
    {
      /**
       * Returns a brief string which is suitable for displaying information about this Airing. This contains the Airing's channel &amp; a short time string as well as the title
       * @param Airing the Airing object
       * @return a brief string which is suitable for displaying information about this Airing
       *
       * @declaration public String PrintAiringShort(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack); return (a==null)?"":a.getShortString();
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringTitle", 1, new String[] { "Airing" })
    {
      /**
       * Gets the title of this Airing. This will be the same as the title of the Airing's Show
       * @param Airing the Airing object
       * @return the title of this Airing
       *
       * @declaration public String GetAiringTitle(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack); return (a==null)?"":a.getTitle();
      }});
    rft.put(new PredefinedJEPFunction("Airing", "IsWatched", 1, new String[] { "Airing" })
    {
      /**
       * Returns true if this Airing's content has been completely watched already. This may also return true if this Airing itself was not watched; but an Airing with the same content (as determined by SageTV's AI) was watched
       * @param Airing the Airing object
       * @return true if this Airing's content has beeen watched completely before
       *
       * @declaration public boolean IsWatched(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return Boolean.valueOf(a != null && a.isWatched());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetWatchedDuration", 1, new String[] { "Airing" })
    {
      /**
       * Gets the duration of time of this Airing that has been watched already. This time is relative to the Airing itself; not real time.
       * @param Airing the Airing object
       * @return the duration of time of this Airing that has been watched already in milliseconds
       *
       * @declaration public long GetWatchedDuration(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Watched w = Wizard.getInstance().getWatch(getAir(stack));
        return (w == null) ? new Long(0) : new Long(w.getWatchDuration());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetWatchedStartTime", 1, new String[] { "Airing" })
    {
      /**
       * Gets the time the user started watching this Airing. This time is relative to the Airing itself; not real time.
       * If this is the first time watching this Airing; then this will return the time the Airing started recording.
       * If this Airing has been watched previously; then the minimum value for this will be the Airing start time.
       * @param Airing the Airing object
       * @return the time the user started watching this Airing
       *
       * @declaration public long GetWatchedStartTime(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        if (a == null) return new Long(0);
        Watched w = Wizard.getInstance().getWatch(a);
        if (w != null) return new Long(w.getWatchStart());
        if (stack.getUIMgrSafe() != null && stack.getUIMgrSafe().getVideoFrame().hasFile())
        {
          MediaFile mf = stack.getUIMgrSafe().getVideoFrame().getCurrFile();
          if (mf != null && mf.getContentAiring() == a)
            return new Long(mf.getRecordTime());
        }
        return new Long(0);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetWatchedEndTime", 1, new String[] { "Airing" })
    {
      /**
       * Gets the time the user finished watching this Airing. This time is relative to the Airing itself; not real time.
       * If this Airing is currently being watched, this will be the maximum of any prior watch end time and the
       * current playback time in the Airing.
       * @param Airing the Airing object
       * @return the time the user finished watching this Airing
       *
       * @declaration public long GetWatchedEndTime(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        if (a == null) return new Long(0);
        Watched w = Wizard.getInstance().getWatch(a);
        if (stack.getUIMgrSafe() != null && stack.getUIMgrSafe().getVideoFrame().hasFile())
        {
          MediaFile mf = stack.getUIMgrSafe().getVideoFrame().getCurrFile();
          if (mf != null && mf.getContentAiring() == a)
          {
            return new Long(Math.max(stack.getUIMgrSafe().getVideoFrame().getMediaTimeMillis(true),
                (w == null) ? 0 : w.getWatchEnd()));
          }
        }
        return (w == null) ? new Long(0) : new Long(w.getWatchEnd());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetRealWatchedStartTime", 1, new String[] { "Airing" })
    {
      /**
       * Gets the time the user started watching this Airing, in real time.
       * @param Airing the Airing object
       * @return the time the user started watching this Airing in real time
       * @since 6.4
       *
       * @declaration public long GetRealWatchedStartTime(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        if (a == null) return new Long(0);
        Watched w = Wizard.getInstance().getWatch(a);
        if (w != null) return new Long(w.getRealWatchStart());
        if (stack.getUIMgrSafe() != null && stack.getUIMgrSafe().getVideoFrame().hasFile())
        {
          MediaFile mf = stack.getUIMgrSafe().getVideoFrame().getCurrFile();
          if (mf != null && mf.getContentAiring() == a)
            return new Long(stack.getUIMgrSafe().getVideoFrame().getRealWatchStart());
        }
        return new Long(0);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetRealWatchedEndTime", 1, new String[] { "Airing" })
    {
      /**
       * Gets the time the user finished watching this Airing, in real time.
       * @param Airing the Airing object
       * @return the time the user finished watching this Airing
       * @since 6.4
       *
       * @declaration public long GetRealWatchedEndTime(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        if (a == null) return new Long(0);
        Watched w = Wizard.getInstance().getWatch(a);
        if (stack.getUIMgrSafe() != null && stack.getUIMgrSafe().getVideoFrame().hasFile())
        {
          MediaFile mf = stack.getUIMgrSafe().getVideoFrame().getCurrFile();
          if (mf != null && mf.getContentAiring() == a)
          {
            return new Long(Sage.time());
          }
        }
        return (w == null) ? new Long(0) : new Long(w.getRealWatchEnd());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "SetWatched", 1, new String[] { "Airing" }, true)
    {
      /**
       * Sets the watched flag for this Airing to true as if the user watched the show from start to finish
       * @param Airing the Airing object
       *
       * @declaration public void SetWatched(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
          BigBrother.setWatched(a);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Airing", "SetWatchedTimes", 3, new String[] { "Airing", "WatchedEndTime", "RealStartTime" }, true)
    {
      /**
       * Updates the Watched information for this airing. The AiringEndTime should be an airing-relative time which indicates the time the
       * user has watched the show up until. The new watched end time will be the maximum of this value and the current watched end time. The
       * RealStartTime is the time (in real time) the user started watching this program at. Internally SageTV will set the start time of the watched
       * data to be the minimum of the recording start time and the airing start time; and the 'real' end time to be the current time.
       * @param Airing the Airing object
       * @param WatchedEndTime an airing-relative time which indicates the time the user has watched the show up until
       * @param RealStartTime the time (in real time) the user started watching this program at
       * @since 7.0
       *
       * @declaration public void SetWatchedTimes(Airing Airing, long WatchedEndTime, long RealStartTime);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long realStart = getLong(stack);
        long airEnd = getLong(stack);
			 Airing air = getAir(stack);
			 if (air != null && Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
         MediaFile mf = Wizard.getInstance().getFileForAiring(air);
          BigBrother.setWatched(air, (mf != null) ? mf.getRecordTime() : air.getStartTime(), airEnd, realStart, Sage.time(), false);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Airing", "ClearWatched", 1, new String[] { "Airing" }, true)
    {
      /**
       * Clears the watched information for this Airing completely.
       * @param Airing the Airing object
       *
       * @declaration public void ClearWatched(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
          BigBrother.clearWatched(a);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetLatestWatchedTime", 1, new String[] { "Airing" })
    {
      /**
       * Gets the time that viewing should resume from for this Airing if it is selected to view
       * @param Airing the Airing object
       * @return the time that viewing should resume from for this Airing if it is selected to view
       *
       * @declaration public long GetLatestWatchedTime(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(BigBrother.getLatestWatch(getAir(stack)));
      }});
    rft.put(new PredefinedJEPFunction("Airing", "IsWatchedCompletely", 1, new String[] { "Airing" })
    {
      /**
       * Returns true if this Airing has been completely watched already. This is different then {@link #IsWatched IsWatched()}
       * @param Airing the Airing object
       * @return true if this Airing has beeen watched completely
       *
       * @declaration public boolean IsWatchedCompletely(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(BigBrother.isFullWatch(Wizard.getInstance().getWatch(getAir(stack))));
      }});
    rft.put(new PredefinedJEPFunction("Airing", "IsDontLike", 1, new String[] { "Airing" })
    {
      /**
       * Returns true if this Airing has been set as content the user "Doesn't Like"
       * @param Airing the Airing object
       * @return true if this Airing has been set as content the user "Doesn't Like"
       *
       * @declaration public boolean IsDontLike(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        if (a == null) return Boolean.FALSE;
        return Boolean.valueOf(a.isDontLike());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "SetDontLike", 1, new String[] { "Airing" }, true)
    {
      /**
       * Called to indicate that the content in this Airing is "Not Liked" by the user
       * @param Airing the Airing object
       *
       * @declaration public void SetDontLike(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing air = getAir(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
          Carny.getInstance().addDontLike(air, true);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Airing", "ClearDontLike", 1, new String[] { "Airing" }, true)
    {
      /**
       * Called to cancel the indication that the content in this Airing is "Not Liked" by the user
       * @param Airing the Airing object
       *
       * @declaration public void ClearDontLike(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing air = getAir(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
          Carny.getInstance().removeDontLike(air);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Airing", "IsManualRecord", 1, new String[] { "Airing" })
    {
      /**
       * Returns true if this Airing has been selected by the user to manually record {@link #Record Record()}
       * @param Airing the Airing object
       * @return true if this Airing has been selected by the user to manually record {@link #Record Record()}
       *
       * @declaration public boolean IsManualRecord(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(Wizard.getInstance().getManualRecord(getAir(stack)) != null);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "IsNotManualOrFavorite", 1, new String[] { "Airing" })
    {
      /**
       * Returns true if this Airing has NOT been selected by the user to manually record {@link #Record Record()} and
       * is also NOT a Favorite (i.e. IsFavorite and IsManualRecord both return false)
       * @param Airing the Airing object
       * @return true if this Airing is not a ManualRecord or a Favorite
       *
       * @since 6.2
       *
       * @declaration public boolean IsNotManualOrFavorite(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing air = getAir(stack);
        return Boolean.valueOf(!(Wizard.getInstance().getManualRecord(air) != null || Carny.getInstance().isLoveAir(air)));
      }});
    rft.put(new PredefinedJEPFunction("Airing", "IsAiringHDTV", 1, new String[] { "Airing" })
    {
      /**
       * Returns true if this Airing is in HDTV
       * @param Airing the Airing object
       * @return true if this Airing is in HDTV, false otherwise
       *
       * @declaration public boolean IsAiringHDTV(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return Boolean.valueOf((a != null) && a.isHDTV());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetTrackNumber", 1, new String[] { "Airing" })
    {
      /**
       * Returns the track number for the Airing if it's from a Music Album. With music; each song (file) corresponds to an airing.
       * @param Airing the Airing object
       * @return the track number for the Airing if it's from a Music Album, 0 otherwise
       *
       * @declaration public int GetTrackNumber(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return new Integer(a != null ? a.getTrack() : 0);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetRecordingQuality", 1, new String[] { "Airing" })
    {
      /**
       * Returns the recording quality that this Airing has been specifically set to record at. This is only valid for user selected manual recordings {@link #Record Record()}
       * @param Airing the Airing object
       * @return the recording quality name that this Airing has been specifically set to record at; if no quality has been set it returns the empty string
       *
       * @declaration public String GetRecordingQuality(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        ManualRecord mr = Wizard.getInstance().getManualRecord(getAir(stack));
        return (mr == null) ? "" : mr.getRecordingQuality();
      }});
    rft.put(new PredefinedJEPFunction("Airing", "SetRecordingQuality", new String[] { "Airing", "Quality" }, true)
    {
      /**
       * Sets the recording quality for this Airing if it has been selected by the user as a manual record
       * @param Airing the Airing object
       * @param Quality the name of the recording quality
       *
       * @declaration public void SetRecordingQuality(Airing Airing, String Quality);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String q = getString(stack);
        ManualRecord mr = Wizard.getInstance().getManualRecord(getAir(stack));
        if (mr != null && Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          // This can actually cause an invalid schedule at this point, but we'll leave it and not
          // require them to resolve the conflict interactively. It can be dealt with in the
          // Recording Information Menu.
          mr.setRecordingQuality(q);
          SchedulerSelector.getInstance().kick(false); // because it can change the encoders for it
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Airing", "IsFavorite", 1, new String[] { "Airing" })
    {
      /**
       * Returns true if this Airing matches one of the Favorites the user has setup
       * @param Airing the Airing object
       * @return true if this Airing matches one of the Favorites the user has setup
       *
       * @declaration public boolean IsFavorite(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(Carny.getInstance().isLoveAir(getAir(stack)));
      }});
    rft.put(new PredefinedJEPFunction("Airing", "SetRecordingTimes", 3, new String[] { "Airing", "StartTime", "StopTime" })
    {
      /**
       * Modifies or creates a time-based recording that is associated with this Airing. This is also a type of Manual Record.
       * @param Airing the Airing object
       * @param StartTime the time the recording of this Airing should start
       * @param StopTime the time the recording of this Airing should stop
       * @return true if the call succeeds, otherwise a localized error message is returned
       *
       * @declaration public Object SetRecordingTimes(Airing Airing, long StartTime, long StopTime);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        final long stop = getLong(stack);
        final long start = getLong(stack);
        final Airing a = getAir(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          final ManualRecord mr = Wizard.getInstance().getManualRecord(a);
          final Object taskID = Catbert.getNewTaskID();
          final UIClient uiClient = (Sage.client ? stack.getUIMgrSafe() : stack.getUIMgr());
          Pooler.execute(new Runnable()
          {
            public void run()
            {
              Object rv;
              if (mr != null)
                rv = Catbert.getRecordFailureObject(SeekerSelector.getInstance().modifyRecord(start - mr.getStartTime(),
                    stop - mr.getEndTime(), mr, uiClient));
              else
                rv = Catbert.getRecordFailureObject(SeekerSelector.getInstance().timedRecord(start, stop, a.getStationID(), 0, a, uiClient));
              Catbert.asyncTaskComplete(taskID, rv);
            }
          }, "AsyncChannelSetRecordingTimes");
          return taskID;
        }
        else
          return Catbert.getRecordFailureObject(VideoFrame.WATCH_FAILED_INSUFFICIENT_PERMISSIONS);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "Record", 1, new String[] { "Airing" })
    {
      /**
       * Specifies that this Airing should be recorded. This is a Manul Recording.
       * @param Airing the Airing object
       * @return true if the call succeeds, otherwise a localized error message is returned
       *
       * @declaration public Object Record(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        final Airing a = getAir(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
        {
          final Object taskID = Catbert.getNewTaskID();
          final UIClient uiClient = (Sage.client ? stack.getUIMgrSafe() : stack.getUIMgr());
          Pooler.execute(new Runnable()
          {
            public void run()
            {
              Catbert.asyncTaskComplete(taskID, Catbert.getRecordFailureObject(SeekerSelector.getInstance().record(a, uiClient)));
            }
          }, "AsyncRecord");
          return taskID;
        }
        else
          return Catbert.getRecordFailureObject(VideoFrame.WATCH_FAILED_INSUFFICIENT_PERMISSIONS);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "CancelRecord", 1, new String[] { "Airing" }, true)
    {
      /**
       * Cancels a recording that was previously set with a call to {@link #Record Record()} or {@link #SetRecordingTimes SetRecordingTimes()}
       * @param Airing the Airing object
       *
       * @declaration public void CancelRecord(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
          SeekerSelector.getInstance().cancelRecord(a, Sage.client ? stack.getUIMgrSafe() : stack.getUIMgr());
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Airing", "IsAiringObject", 1, new String[] { "Airing" })
    {
      /**
       * Returns true if the argument is an Airing object. Automatic type conversion is NOT done in this call.
       * @param Airing the object to test
       * @return true if the argument is an Airing object
       *
       * @declaration public boolean IsAiringObject(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof sage.vfs.MediaNode)
          o = ((sage.vfs.MediaNode) o).getDataObject();
        return Boolean.valueOf(o instanceof Airing);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetParentalRating", 1, new String[] { "Airing" })
    {
      /**
       * Gets the parental rating information associated with this Airing. This is information such as TVY, TVPG, TVMA, etc.
       * @param Airing the Airing object
       * @return the parental rating information associated with this Airing
       *
       * @declaration public String GetParentalRating(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return (a==null) ? "" : a.getParentalRating();
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetExtraAiringDetails", 1, new String[] { "Airing" })
    {
      /**
       * Gets miscellaneous information about this Airing. This includes thing such as "Part 1 of 2", "CC", "HDTV", "Series Premiere", etc.
       * @param Airing the Airing object
       * @return miscellaneous information about this Airing
       *
       * @declaration public String GetExtraAiringDetails(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return (a==null) ? "" : a.getMiscInfo();
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringID", new String[] { "Airing" })
    {
      /**
       * Returns the unique ID used to identify this Airing. Can get used later on a call to {@link #GetAiringForID GetAiringForID()}
       * @param Airing the Airing object
       * @return the unique ID used to identify this Airing
       *
       * @declaration public int GetAiringID(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return (a == null) ? null : new Integer(a.getID());
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringForID", 1, new String[] { "AiringID" })
    {
      /**
       * Returns the Airing object that corresponds to the passed in ID. The ID should have been obtained from a call to {@link #GetAiringID GetAiringID()}
       * @param AiringID the Airing id
       * @return the Airing object that corresponds to the passed in ID
       *
       * @declaration public Airing GetAiringForID(int AiringID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int i = getInt(stack);
        return Wizard.getInstance().getAiringForID(i);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "AddAiring", 4, new String[] {"ShowExternalID", "StationID", "StartTime", "Duration"}, true)
    {
      /**
       * Adds a new Airing object to the database. This call should be used with caution.
       * @param ShowExternalID a GUID which uniquely identifies the Show that correlates with this Airing, this Show should already have been added
       * @param StationID the GUID which uniquely identifies a "Station" (sort of like a Channel)
       * @param StartTime the time at which the new Airing starts
       * @param Duration the duration of the new Airing in milliseconds
       * @return the newly added Airing
       *
       * @declaration public Airing AddAiring(String ShowExternalID, int StationID, long StartTime, long Duration);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long dur = getLong(stack);
        long start = getLong(stack);
        int statID = getInt(stack);
        return Wizard.getInstance().addAiring(getString(stack), statID, start, dur, (byte)0, (byte)0, (byte)0, (byte)0);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "AddAiringDetailed", -1, new String[] {"ShowExternalID", "StationID", "StartTime", "Duration",
        "PartNumber", "TotalParts", "ParentalRating", "HDTV", "Stero", "ClosedCaptioning", "SAP", "Subtitled", "PremierFinale"}, true)
    {
      /**
       * Adds a new Airing object to the database. This call should be used with caution. (it has more details you can specify than the
       * standard AddAiring API call)
       * @param ShowExternalID a GUID which uniquely identifies the Show that correlates with this Airing, this Show should already have been added
       * @param StationID the GUID which uniquely identifies a "Station" (sort of like a Channel)
       * @param StartTime the time at which the new Airing starts
       * @param Duration the duration of the new Airing in milliseconds
       * @param PartNumber for music files, the track number; for TV shows if it is a multipart show this is the part number, otherwise this should be 0
       * @param TotalParts for multipart TV shows, this is the total number of parts otherwise this should be zero; for music files it should be zero
       * @param ParentalRating the parental rating for the show, should be a localized value from "TVY", "TVY7", "TVG", "TVPG", "TV14", "TVM" or the empty string
       * @param HDTV true if it's an HDTV airing, false otherwise
       * @param Stereo true if it's a stereo recording, false otherwise
       * @param ClosedCaptioning true if the airing has closed captioning, false otherwise
       * @param SAP true if the Airing has a Secondary Audio Program (SAP), false otherwise
       * @param Subtitled true if the Airing is subtitled, false otherwise
       * @param PremierFinale should be the empty string or a localized value from the list "Premiere", "Channel Premiere", "Season Premiere", "Series Premiere", "Season Finale", "Series Finale"
       * @return the newly added Airing
       *
       * @since 5.0
       *
       * @declaration public Airing AddAiringDetailed(String ShowExternalID, int StationID, long StartTime, long Duration, int PartNumber, int TotalParts, String ParentalRating, boolean HDTV, boolean Stereo, boolean ClosedCaptioning, boolean SAP, boolean Subtitled, String PremierFinale);
       */

      /**
       * Adds a new Airing object to the database. This call should be used with caution. (it has more details you can specify than the
       * standard AddAiring API call)
       * @param ShowExternalID a GUID which uniquely identifies the Show that correlates with this Airing, this Show should already have been added
       * @param StationID the GUID which uniquely identifies a "Station" (sort of like a Channel)
       * @param StartTime the time at which the new Airing starts
       * @param Duration the duration of the new Airing in milliseconds
       * @param PartNumber for music files, the track number; for TV shows if it is a multipart show this is the part number, otherwise this should be 0
       * @param TotalParts for multipart TV shows, this is the total number of parts otherwise this should be zero; for music files it should be zero
       * @param ParentalRating the parental rating for the show, should be a localized value from "TVY", "TVY7", "TVG", "TVPG", "TV14", "TVM" or the empty string
       * @param Attributes a list of attributes for this Airing, the following values may be used: HDTV, Stereo, CC, SAP, Subtitled, 3D, DD5.1, Dolby, Letterbox, Live, New, Widescreen, Surround, Dubbed or Taped
       * @param PremierFinale should be the empty string or a localized value from the list "Premiere", "Channel Premiere", "Season Premiere", "Series Premiere", "Season Finale", "Series Finale"
       * @return the newly added Airing
       *
       * @since 7.1
       *
       * @declaration public Airing AddAiringDetailed(String ShowExternalID, int StationID, long StartTime, long Duration, int PartNumber, int TotalParts, String ParentalRating, String[] Attributes, String PremierFinale);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int miscB = 0;
        String premFin = getString(stack);
        if (curNumberOfParameters == 13)
        {
          boolean subtitled = evalBool(stack.pop());
          boolean sap = evalBool(stack.pop());
          boolean cc = evalBool(stack.pop());
          boolean stereo = evalBool(stack.pop());
          boolean hdtv = evalBool(stack.pop());
          if (subtitled)
            miscB |= sage.Airing.SUBTITLE_MASK;
          if (sap)
            miscB |= sage.Airing.SAP_MASK;
          if (cc)
            miscB |= sage.Airing.CC_MASK;
          if (stereo)
            miscB |= sage.Airing.STEREO_MASK;
          if (hdtv)
            miscB |= sage.Airing.HDTV_MASK;
        }
        else
        {
          String[] attribs = getStringList(stack);
          for (int i = 0; attribs != null && i < attribs.length; i++)
          {
            String test = attribs[i];
            if ("HDTV".equalsIgnoreCase(test))
              miscB |= sage.Airing.HDTV_MASK;
            else if ("Stereo".equalsIgnoreCase(test))
              miscB |= sage.Airing.STEREO_MASK;
            else if ("CC".equalsIgnoreCase(test))
              miscB |= sage.Airing.CC_MASK;
            else if ("SAP".equalsIgnoreCase(test))
              miscB |= sage.Airing.SAP_MASK;
            else if ("Subtitled".equalsIgnoreCase(test))
              miscB |= sage.Airing.SUBTITLE_MASK;
            else if ("3D".equalsIgnoreCase(test))
              miscB |= sage.Airing.THREED_MASK;
            else if ("DD5.1".equalsIgnoreCase(test))
              miscB |= sage.Airing.DD51_MASK;
            else if ("Dolby".equalsIgnoreCase(test))
              miscB |= sage.Airing.DOLBY_MASK;
            else if ("Letterbox".equalsIgnoreCase(test))
              miscB |= sage.Airing.LETTERBOX_MASK;
            else if ("Live".equalsIgnoreCase(test))
              miscB |= sage.Airing.LIVE_MASK;
            else if ("New".equalsIgnoreCase(test))
              miscB |= sage.Airing.NEW_MASK;
            else if ("Widescreen".equalsIgnoreCase(test))
              miscB |= sage.Airing.WIDESCREEN_MASK;
            else if ("Surround".equalsIgnoreCase(test))
              miscB |= sage.Airing.SURROUND_MASK;
            else if ("Dubbed".equalsIgnoreCase(test))
              miscB |= sage.Airing.DUBBED_MASK;
            else if ("Taped".equalsIgnoreCase(test))
              miscB |= sage.Airing.TAPE_MASK;
          }
        }
        if (premFin != null && premFin.length() > 0)
        {
          if (Sage.rez("Premiere").equals(premFin))
            miscB |= sage.Airing.PREMIERE_MASK;
          else if (Sage.rez("Season_Premiere").equals(premFin))
            miscB |= sage.Airing.SEASON_PREMIERE_MASK;
          else if (Sage.rez("Season_Finale").equals(premFin))
            miscB |= sage.Airing.SEASON_FINALE_MASK;
          else if (Sage.rez("Series_Finale").equals(premFin))
            miscB |= sage.Airing.SERIES_FINALE_MASK;
          else if (Sage.rez("Series_Premiere").equals(premFin))
            miscB |= sage.Airing.SERIES_PREMIERE_MASK;
          else if (Sage.rez("Channel_Premiere").equals(premFin))
            miscB |= sage.Airing.CHANNEL_PREMIERE_MASK;
        }
        String prs = getString(stack);
        int totalParts = getInt(stack);
        int currPart = getInt(stack);
        long dur = getLong(stack);
        long start = getLong(stack);
        int statID = getInt(stack);
        byte partsB = 0;
        if (totalParts > 0)
        {
          partsB = (byte) (totalParts & 0x0F);
          partsB = (byte)((partsB | ((currPart  & 0x0F)<< 4)) & 0xFF);
        }
        else
        {
          partsB = (byte) (currPart & 0xFF);
        }
        byte prB = 0;
        if (prs != null && prs.length() > 0)
        {
          for (int i = 0; i < sage.Airing.PR_NAMES.length; i++)
          {
            if (sage.Airing.PR_NAMES[i].equals(prs))
            {
              prB = (byte) i;
              break;
            }
          }
        }
        return Wizard.getInstance().addAiring(getString(stack), statID, start, dur, partsB, miscB, prB, (byte)0);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "IsAiringAttributeSet", 2, new String[] {"Airing", "Attribute"})
    {
      /**
       * Returns whether or not the specificed attibute is set for this Airing
       * @param Airing the Airing object
       * @param Attribute the following String values may be used: HDTV, Stereo, CC, SAP, Subtitled, 3D, DD5.1, Dolby, Letterbox, Live, New, Widescreen, Surround, Dubbed or Taped
       * @return true if the specified Attribute is set for this Airing, false otherwise
       * @since 7.1
       *
       * @declaration public boolean IsAiringAttributeSet(Airing Airing, String Attribute);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String test = getString(stack);
        Airing a = getAir(stack);
        if (a == null) return Boolean.FALSE;
        if ("HDTV".equalsIgnoreCase(test))
          return a.isHDTV() ? Boolean.TRUE : Boolean.FALSE;
        else if ("Stereo".equalsIgnoreCase(test))
          return a.isStereo() ? Boolean.TRUE : Boolean.FALSE;
        else if ("CC".equalsIgnoreCase(test))
          return a.isCC() ? Boolean.TRUE : Boolean.FALSE;
        else if ("SAP".equalsIgnoreCase(test))
          return a.isSAP() ? Boolean.TRUE : Boolean.FALSE;
        else if ("Subtitled".equalsIgnoreCase(test))
          return a.isSubtitled() ? Boolean.TRUE : Boolean.FALSE;
        else if ("3D".equalsIgnoreCase(test))
          return a.is3D() ? Boolean.TRUE : Boolean.FALSE;
        else if ("DD5.1".equalsIgnoreCase(test))
          return a.isDD51() ? Boolean.TRUE : Boolean.FALSE;
        else if ("Dolby".equalsIgnoreCase(test))
          return a.isDolby() ? Boolean.TRUE : Boolean.FALSE;
        else if ("Letterbox".equalsIgnoreCase(test))
          return a.isLetterbox() ? Boolean.TRUE : Boolean.FALSE;
        else if ("Live".equalsIgnoreCase(test))
          return a.isLive() ? Boolean.TRUE : Boolean.FALSE;
        else if ("New".equalsIgnoreCase(test))
          return a.isNew() ? Boolean.TRUE : Boolean.FALSE;
        else if ("Widescreen".equalsIgnoreCase(test))
          return a.isWidescreen() ? Boolean.TRUE : Boolean.FALSE;
        else if ("Surround".equalsIgnoreCase(test))
          return a.isSurround() ? Boolean.TRUE : Boolean.FALSE;
        else if ("Dubbed".equalsIgnoreCase(test))
          return a.isDubbed() ? Boolean.TRUE : Boolean.FALSE;
        else if ("Taped".equalsIgnoreCase(test))
          return a.isTaped() ? Boolean.TRUE : Boolean.FALSE;
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringAttributeList", 1, new String[] {"Airing"})
    {
      /**
       * Gets a list of attributes that are set for this Airing.
       * @param Airing the Airing object
       * @return a String array of the attributes set for this Airing, the following values may be in this array: HDTV, Stereo, CC, SAP, Subtitled, 3D, DD5.1, Dolby, Letterbox, Live, New, Widescreen, Surround, Dubbed or Taped
       * @since 7.1
       *
       * @declaration public String[] GetAiringAttributeList(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        if (a == null) return Pooler.EMPTY_STRING_ARRAY;
        java.util.ArrayList rv = new java.util.ArrayList();
        if (a.isHDTV()) rv.add("HDTV");
        if (a.isStereo()) rv.add("Stereo");
        if (a.isCC()) rv.add("CC");
        if (a.isSAP()) rv.add("SAP");
        if (a.isSubtitled()) rv.add("Subtitled");
        if (a.is3D()) rv.add("3D");
        if (a.isDD51()) rv.add("DD5.1");
        if (a.isDolby()) rv.add("Dolby");
        if (a.isLetterbox()) rv.add("Letterbox");
        if (a.isLive()) rv.add("Live");
        if (a.isNew()) rv.add("New");
        if (a.isWidescreen()) rv.add("Widescreen");
        if (a.isSurround()) rv.add("Surround");
        if (a.isDubbed()) rv.add("Dubbed");
        if (a.isTaped()) rv.add("Taped");
        return (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringPartNumber", 1, new String[] {"Airing"})
    {
      /**
       * Returns the part number for this Airing if it is a multi-part Airing.
       * @param Airing the Airing object
       * @return the part number for this Airing if it is a multi-part Airing, 1 otherwise
       * @since 7.1
       *
       * @declaration public int GetAiringPartNumber(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return (a == null) ? new Integer(1) : new Integer(Math.max(1, a.getPartNum()));
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringTotalParts", 1, new String[] {"Airing"})
    {
      /**
       * Returns the number of parts for this Airing if it is a multi-part Airing.
       * @param Airing the Airing object
       * @return the number of parts for this Airing if it is a multi-part Airing, 1 otherwise
       * @since 7.1
       *
       * @declaration public int GetAiringTotalParts(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return (a == null) ? new Integer(1) : new Integer(Math.max(1, a.getTotalParts()));
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringPremiereFinaleInfo", 1, new String[] {"Airing"})
    {
      /**
       * Returns a String which describes any kind of Premiere, Finale info for this Airing
       * @param Airing the Airing object
       * @return a String which describes any kind of Premiere, Finale info for this Airing, can be one of: "Premiere", "Channel Premiere", "Season Premiere", "Series Premiere", "Season Finale", "Series Finale" or the empty String if none apply
       * @since 7.1
       *
       * @declaration public String GetAiringPremiereFinaleInfo(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        if (a == null) return "";
        if (a.isSeasonPremiere()) return Sage.rez("Season_Premiere");
        if (a.isSeasonFinale()) return Sage.rez("Season_Finale");
        if (a.isSeriesPremiere()) return Sage.rez("Series_Premiere");
        if (a.isSeriesFinale()) return Sage.rez("Series_Finale");
        if (a.isChannelPremiere()) return Sage.rez("Channel_Premiere");
        if (a.isPremiere()) return Sage.rez("Premiere");
        return "";
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetMediaFileForAiring", 1, new String[] {"Airing"})
    {
      /**
       * Gets the MediaFile object which corresponds to this Airing object
       * @param Airing the Airing object
       * @return the MediaFile object which corresponds to this Airing object, or null if it has no associated MediaFile
       *
       * @declaration public MediaFile GetMediaFileForAiring(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getFileForAiring(getAir(stack));
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringOnAfter", 1, new String[] {"Airing"})
    {
      /**
       * Returns the Airing on the same Channel that is on immediately after the passed in Airing
       * @param Airing the Airing object
       * @return the Airing on the same Channel that is on immediately after the passed in Airing
       *
       * @declaration public Airing GetAiringOnAfter(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getTimeRelativeAiring(getAir(stack), 1);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetAiringOnBefore", 1, new String[] {"Airing"})
    {
      /**
       * Returns the Airing on the same Channel that is on immediately before the passed in Airing
       * @param Airing the Airing object
       * @return the Airing on the same Channel that is on immediately before the passed in Airing
       *
       * @declaration public Airing GetAiringOnBefore(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getTimeRelativeAiring(getAir(stack), -1);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetManualRecordProperty", 2, new String[] { "Airing", "PropertyName" })
    {
      /**
       * Returns a property value for a specified ManualRecord. This must have been set using SetManualRecordProperty and
       * the specified Airing must be a ManualRecord.
       * Returns the empty string when the property is undefined.
       * @param Airing the Airing object which is a ManualRecord
       * @param PropertyName the name of the property
       * @return the property value for the specified ManualRecord, or the empty string if it is not defined
       * @since 7.0
       *
       * @declaration public String GetManualRecordProperty(Airing Airing, String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String prop = getString(stack);
        Airing air = getAir(stack);
        ManualRecord mr = Wizard.getInstance().getManualRecord(air);
        return (mr == null) ? "" : mr.getProperty(prop);
      }});
    rft.put(new PredefinedJEPFunction("Airing", "SetManualRecordProperty", 3, new String[] { "Airing", "PropertyName", "PropertyValue" }, true)
    {
      /**
       * Sets a property for a specified ManualRecord. This can be any name/value combination (but the name cannot be null). If the value is null;
       * then the specified property will be removed from this ManualRecord. This only impacts the return values from GetManualRecordProperty and has no other side effects.
       * @param Airing the Airing object which is a ManualRecord
       * @param PropertyName the name of the property
       * @param PropertyValue the value of the property
       * @since 7.0
       *
       * @declaration public void SetManualRecordProperty(Airing Airing, String PropertyName, String PropertyValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String propV = getString(stack);
        String propN = getString(stack);
        Airing air = getAir(stack);
        ManualRecord mr = Wizard.getInstance().getManualRecord(air);
        if (mr != null && Permissions.hasPermission(Permissions.PERMISSION_EDITMETADATA, stack.getUIMgr()))
          mr.setProperty(propN, propV);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Airing", "GetPlayableAiring", 1, new String[] { "Airing" })
    {
      /**
       * Returns an Airing that correlates to the same content as the argument that is playable now.
       * This can only be from a completely recorded MediaFile (but could be extended beyond that).
       * @param Airing the Airing object to find a playable Airing for (for unique Shows, this can be linked to other Airings)
       * @return an Airing which represents playable content that is the same as this Airing
       * @since 8.0
       *
       * @declaration public Airing GetPlayableAiring(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing a = getAir(stack);
        return Wizard.getInstance().getPlayableAiring(a);
      }});

    rft.put(new PredefinedJEPFunction("Airing", "GetParentalLimitsExceeded", 1, new String[] { "Airing" })
    {
      /**
       * Checks the airing for exceeding parental ratings and return a string array filled with each rating.
       * If parental ratings is disabled, or the airing exceeds no parental settings, return empty array.
       * @param Airing the Airing object to check against ratings
       * @return a String[] of the parental limits exceeded by this Airing
       * @since 8.1
       *
       * @declaration public String[] GetParentalLimitsExceeded(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception {
        Airing air = getAir(stack);
        return stack.getUIMgrSafe().getVideoFrame().getPCRestrictionsForAiring(air);
      }
    });

    /*
		rft.put(new PredefinedJEPFunction("Airing", "", 1, new String[] { "Airing" })
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 return getAir(stack).;
			}});
     */

  }
}
