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
 *
 * @author Narflex
 */
public class TranscodeAPI
{
  private TranscodeAPI(){	}

  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Transcode", "GetTranscodeFormats", true)
    {
      /**
       * Gets the names of the different transcode formats
       * @return a list of the names of the different transcode formats
       * @since 5.1
       *
       * @declaration public String[] GetTranscodeFormats();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.keys("transcoder/formats");
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "GetTranscodeFormatDetails", new String[] { "FormatName" }, true)
    {
      /**
       * Gets the format details for the specified format name
       * @param FormatName the name of the transcode format to get the parameter details for
       * @return the full detail string that describes the specified transcode format
       * @since 5.1
       *
       * @declaration public String GetTranscodeFormatDetails(String FormatName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.get("transcoder/formats/" + getString(stack), null);
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "AddTranscodeFormat", new String[] { "FormatName", "FormatDetails" }, true)
    {
      /**
       * Adds the specified transcode format to the list of available formats
       * @param FormatName the name of the new transcode format
       * @param FormatDetails the detailed property string for the new format
       * @since 5.1
       *
       * @declaration public String AddTranscodeFormat(String FormatName, String FormatDetails);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String details = getString(stack);
        String name = getString(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_CONVERSION, stack.getUIMgr()))
          Sage.put("transcoder/formats/" + name, details);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "RemoveTranscodeFormat", new String[] { "FormatName" }, true)
    {
      /**
       * Removed the specified transcode format to the list of available formats
       * @param FormatName the name of the transcode format to remove
       * @since 5.1
       *
       * @declaration public String RemoveTranscodeFormat(String FormatName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String name = getString(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_CONVERSION, stack.getUIMgr()))
          Sage.remove("transcoder/formats/" + name);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "AddTranscodeJob", -1, new String[] { "SourceMediaFile", "FormatName", "DestinationFile", "DeleteSourceAfterTranscode" }, true)
    {
      /**
       * Adds the specified job to the transcoder's queue. Returns a Job ID# for future reference of it.
       * @param SourceMediaFile the source file that is to be transcoded, if it consists of multiple segments, all segments will be transcoded
       * @param FormatName the name of the transcode format to use for this conversion
       * @param DestinationFile the target file path for the conversion or null if SageTV should automatically determine the filename of the target files, if a directory is given then SageTV auto-generates the filename in that directory
       * @param DeleteSourceAfterTranscode if true then the source media files are deleted when the transcoding is done, if false the source files are kept
       * @return the job ID number to reference this transcode job
       * @since 5.1
       *
       * @declaration public int AddTranscodeJob(MediaFile SourceMediaFile, String FormatName, java.io.File DestinationFile, boolean DeleteSourceAfterTranscode);
       */

      /**
       * Adds the specified job to the transcoder's queue. Returns a Job ID# for future reference of it. This allows specification of the
       * start time and duration for the media which allows extracting a 'clip' from a file.
       * @param SourceMediaFile the source file that is to be transcoded, if it consists of multiple segments, all segments will be transcoded
       * @param FormatName the name of the transcode format to use for this conversion
       * @param DestinationFile the target file path for the conversion or null if SageTV should automatically determine the filename of the target files, if a directory is given then SageTV auto-generates the filename in that directory
       * @param DeleteSourceAfterTranscode if true then the source media files are deleted when the transcoding is done, if false the source files are kept
       * @param ClipTimeStart specifies the time in the file in seconds that the clip starts at (this number is relative to the beginning of the actual file)
       * @param ClipDuration specifies the duration of the clip in seconds to extract from the file (0 to convert until the end of the file)
       * @return the job ID number to reference this transcode job
       * @since 5.1
       *
       * @declaration public int AddTranscodeJob(MediaFile SourceMediaFile, String FormatName, java.io.File DestinationFile, boolean DeleteSourceAfterTranscode, long ClipTimeStart, long ClipDuration);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long clipStart = 0;
        long clipDuration = 0;
        if (curNumberOfParameters == 6)
        {
          clipDuration = getLong(stack);
          clipStart = getLong(stack);
        }
        boolean deleteAfter = evalBool(stack.pop());
        java.io.File destFile = getFile(stack);
        String formatName = getString(stack);
        sage.media.format.ContainerFormat format = Ministry.getPredefinedTargetFormat(formatName);
        MediaFile mf = getMediaFile(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_CONVERSION, stack.getUIMgr()))
          return new Integer(Ministry.getInstance().addTranscodeJob(mf, formatName, format, destFile, deleteAfter, clipStart*1000, clipDuration*1000));
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "GetTranscodeJobStatus", new String[] { "JobID" }, true)
    {
      /**
       * Gets the status of the specified transcoding job
       * @param JobID the Job ID of the transcoding job to get the status of
       * @return the status information for the specified transcoding job, will be one of: COMPLETED, TRANSCODING, WAITING TO START, or FAILED
       * @since 5.1
       *
       * @declaration public String GetTranscodeJobStatus(int JobID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        switch (Ministry.getInstance().getJobStatusCode(getInt(stack)))
        {
          case TranscodeJob.COMPLETED:
            return "COMPLETED";
          case TranscodeJob.TRANSCODING_SEGMENT_COMPLETE:
          case TranscodeJob.TRANSCODING:
          case TranscodeJob.LIMBO:
            return "TRANSCODING";
          case TranscodeJob.TRANSCODE_FAILED:
            return "FAILED";
          default:
            return "WAITING TO START";
        }
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "CancelTranscodeJob", new String[] { "JobID" }, true)
    {
      /**
       * Cancels the specified transcoding ob
       * @param JobID the Job ID of the transcoding job to cancel
       * @return true if the job exists and was cancelled, false otherwise
       * @since 5.1
       *
       * @declaration public boolean CancelTranscodeJob(int JobID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int jobID = getInt(stack);
        return (Permissions.hasPermission(Permissions.PERMISSION_CONVERSION, stack.getUIMgr()) &&
            Ministry.getInstance().cancelTranscodeJob(jobID)) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "GetTranscodeJobSourceFile", new String[] { "JobID" }, true)
    {
      /**
       * Gets the source file of the specified transcoding job
       * @param JobID the Job ID of the transcoding job to get the source file for
       * @return the source file of the specified transcoding job
       * @since 5.1
       *
       * @declaration public MediaFile GetTranscodeJobSourceFile(int JobID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Ministry.getInstance().getJobSourceFile(getInt(stack));
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "GetTranscodeJobDestFile", new String[] { "JobID" }, true)
    {
      /**
       * Gets the destination file of the specified transcoding job
       * @param JobID the Job ID of the transcoding job to get the destination file for
       * @return the destination file of the specified transcoding job, or null if no destination file was specified
       * @since 5.1
       *
       * @declaration public java.io.File GetTranscodeJobDestFile(int JobID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Ministry.getInstance().getJobDestFile(getInt(stack));
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "GetTranscodeJobShouldKeepOriginal", new String[] { "JobID" }, true)
    {
      /**
       * Returns whether or not the specified transcoding job retains the original source file
       * @param JobID the Job ID of the transcoding job to get the destination file for
       * @return true if the specified transcoding job keeps its original file when done, false otherwise
       * @since 5.1
       *
       * @declaration public boolean GetTranscodeJobShouldKeepOriginal(int JobID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Ministry.getInstance().getJobShouldKeepOriginal(getInt(stack)) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "GetTranscodeJobClipStart", new String[] { "JobID" }, true)
    {
      /**
       * Returns the clip start time for the specified transcode job
       * @param JobID the Job ID of the transcoding job to get the destination file for
       * @return the clip start time for the specified transcode job, 0 if the start time is unspecified
       * @since 5.1
       *
       * @declaration public long GetTranscodeJobClipStart(int JobID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(Ministry.getInstance().getJobClipStartTime(getInt(stack)));
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "GetTranscodeJobClipDuration", new String[] { "JobID" }, true)
    {
      /**
       * Returns the clip duration for the specified transcode job
       * @param JobID the Job ID of the transcoding job to get the destination file for
       * @return the clip duration for the specified transcode job, 0 if the entire file will be trancoded
       * @since 5.1
       *
       * @declaration public long GetTranscodeJobClipDuration(int JobID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(Ministry.getInstance().getJobClipDuration(getInt(stack)));
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "GetTranscodeJobFormat", new String[] { "JobID" }, true)
    {
      /**
       * Gets the target format of the specified transcoding job
       * @param JobID the Job ID of the transcoding job to get the target format file for
       * @return the target format of the specified transcoding job
       * @since 5.1
       *
       * @declaration public String GetTranscodeJobFormat(int JobID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Ministry.getInstance().getJobFormat(getInt(stack));
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "ClearTranscodedJobs", true)
    {
      /**
       * Removes all of the completed transcode jobs from the transcoder queue
       * @since 5.1
       *
       * @declaration public void ClearTranscodedJobs();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Permissions.hasPermission(Permissions.PERMISSION_CONVERSION, stack.getUIMgr()))
          Ministry.getInstance().clearCompletedTranscodes();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "GetTranscodeJobs", true)
    {
      /**
       * Returns a list of the job IDs for all the current jobs in the transcode queue.
       * @return the list of job IDs for all the current jobs in the transcode queue
       * @since 5.1
       *
       * @declaration public Integer[] GetTranscodeJobs();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int[] jobs = Ministry.getInstance().getTranscodeJobIDs();
        Integer[] rv = new Integer[jobs == null ? 0 : jobs.length];
        for (int i = 0; i < rv.length; i++)
          rv[i] = new Integer(jobs[i]);
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "CanFileBeTranscoded", new String[] { "MediaFile" }, true)
    {
      /**
       * Returns true if the specified MediaFile can be transcoded, false otherwise. Transcoding may be restricted
       * by certain formats and also by DRM.
       * @param MediaFile the MediaFile object
       * @return true if the specified MediaFile can be transcoded, false otherwise
       * @since 5.1
       *
       * @declaration public boolean CanFileBeTranscoded(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        if (mf == null) return Boolean.FALSE;
        return Ministry.getInstance().isValidSourceFormat(mf.getFileFormat()) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Transcode", "GetTranscodeJobCompletePercent", new String[] { "JobID" }, true)
    {
      /**
       * Gets the percent complete (between 0 and 1 as a float) for a transcode job
       * @param JobID the Job ID of the transcoding job to get the percent complete of
       * @return the percent complete for the specified transcoding job
       * @since 5.1
       *
       * @declaration public float GetTranscodeJobCompletePercent(int JobID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Float(Ministry.getInstance().getJobPercentComplete(getInt(stack)));
      }});

    /*
		rft.put(new PredefinedJEPFunction("Transcode", "", 0, new String[] {  })
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 return null;
			}});
     */
  }
}
