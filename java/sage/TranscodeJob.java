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

public abstract class TranscodeJob
{
  protected static final String SOURCE_FILE = "sourcefile";
  protected static final String DEST_FILE = "destfile";
  protected static final String TARGET_FORMAT = "format";
  protected static final String TARGET_FORMAT_NAME = "formatname";
  protected static final String REPLACE_ORIGINAL = "replace";
  protected static final String TRANSCODE_PROCESSOR = "processor";
  protected static final String TRANSCODE_STATUS = "status";
  protected static final String TRANSCODE_CLIP_START = "clip_start";
  protected static final String TRANSCODE_CLIP_DURATION = "clip_duration";
  public static final int WAITING = 1;
  public static final int TRANSCODING = 2;
  public static final int TRANSCODING_SEGMENT_COMPLETE = 3;
  public static final int LIMBO = 4;
  public static final int COMPLETED = 5;
  public static final int DESTROYED = -1;
  public static final int TRANSCODE_FAILED = -2;

  protected TranscodeJob(MediaFile mf, String targetFormatName,
      sage.media.format.ContainerFormat targetFormat, boolean replaceOriginal, java.io.File inDestFile)
  {
    this(mf, targetFormatName, targetFormat, replaceOriginal, inDestFile, 0, 0);
  }
  protected TranscodeJob(MediaFile mf, String targetFormatName,
      sage.media.format.ContainerFormat targetFormat, boolean replaceOriginal, java.io.File inDestFile,
      long clipStartTime, long clipDuration)
  {
    this.mf = mf;
    this.targetFormat = targetFormat;
    this.targetFormatName = targetFormatName;
    this.replaceOriginal = replaceOriginal;
    this.jobID = Ministry.getInstance().getNextJobID();
    this.destFile = inDestFile;
    this.clipStartTime = clipStartTime;
    this.clipDuration = clipDuration;
  }

  protected TranscodeJob(int propJobID)
  {
    this.jobID = propJobID;
    String srcFileProp = Sage.get(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + SOURCE_FILE, null);
    if (srcFileProp == null || srcFileProp.length() == 0)
      throw new IllegalArgumentException("Invalid transcode job source file!");
    java.io.File srcFile = new java.io.File(srcFileProp);
    if (!srcFile.isFile())
      throw new IllegalArgumentException("Invalid transcode job source file!");
    mf = Wizard.getInstance().getFileForFilePath(srcFile);
    if (mf == null)
      throw new IllegalArgumentException("Invalid transcode job source file!");
    targetFormatName = Sage.get(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + TARGET_FORMAT_NAME, "");
    targetFormat = Ministry.getPredefinedTargetFormat(targetFormatName);
    if (targetFormat == null)
      throw new IllegalArgumentException("Invalid transcode job format!");
    replaceOriginal = Sage.getBoolean(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + REPLACE_ORIGINAL, false);
    String destFileProp = Sage.get(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + DEST_FILE, null);
    if (destFileProp != null && destFileProp.length() > 0)
      destFile = new java.io.File(destFileProp);
    jobState = Sage.getInt(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + TRANSCODE_STATUS, 0);
    clipStartTime = Sage.getLong(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + TRANSCODE_CLIP_START, 0);
    clipDuration = Sage.getLong(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + TRANSCODE_CLIP_DURATION, 0);
  }
  public void saveToProps()
  {
    Sage.put(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + SOURCE_FILE, mf.getFile(0).toString());
    Sage.putBoolean(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + REPLACE_ORIGINAL, replaceOriginal);
    Sage.put(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + DEST_FILE, (destFile == null) ? "" : destFile.toString());
    Sage.put(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + TARGET_FORMAT_NAME, targetFormatName);
    Sage.putInt(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + TRANSCODE_STATUS,
        (jobState == COMPLETED || jobState < 0) ? jobState : 0);
    Sage.putLong(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + TRANSCODE_CLIP_START, clipStartTime);
    Sage.putLong(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + TRANSCODE_CLIP_DURATION, clipDuration);
  }

  public long getWaitTime()
  {
    if (mf.isTV())
      return Math.max(1, mf.getContentAiring().getSchedulingEnd() - Sage.time() +
          Sage.getLong("mstry_start_delay", 10000));
    else
      return 0;
  }

  public boolean isReadyForConversion()
  {
    if (mf.isTV() && mf.getContentAiring().getSchedulingEnd() + Sage.getLong("mstry_start_delay", 10000) > Sage.time()) return false;
    return true;
  }

  public boolean hasLostHope()
  {
    return !Wizard.getInstance().isMediaFileOK(mf);
  }

  public int getJobState() { return jobState; }
  public void setJobState(int x)
  {
    jobState = x;
    if (x == COMPLETED)
    {
      // Update the destination file
      destFile = getTargetFile(getStartingSegment());
    }
    saveToProps();
  }

  public MediaFile getMediaFile() { return mf; }
  public sage.media.format.ContainerFormat getTargetFormat() { return targetFormat; }
  public String getTargetFormatName() { return targetFormatName; }

  public boolean shouldReplaceOriginal() { return replaceOriginal; }

  private java.io.File getUniqueFilePath(String prefix, String suffix)
  {
    int idx = 0;
    java.io.File rv = null;
    do
    {
      if (!(rv = new java.io.File(prefix + (idx == 0 ? "" : ("." + idx)) + suffix)).exists() || replaceOriginal)
        return rv;
      idx++;
    } while (true);
  }

  public void createFilenames()
  {
    targetFiles = new java.io.File[mf.getNumSegments()];
    tempFiles = new java.io.File[mf.getNumSegments()];
    String prefFileExt = targetFormat.getPreferredFileExtension();
    for (int i = 0; i < targetFiles.length; i++)
    {
      if (destFile == null)
      {
        String fname = mf.getFile(i).toString();
        int dotIdx = fname.lastIndexOf('.');
        if (dotIdx != -1)
          fname = fname.substring(0, dotIdx);
        // TODO: We need to add more file extensions
        targetFiles[i] = getUniqueFilePath(fname, prefFileExt);
        tempFiles[i] = new java.io.File(fname + ".tmp");
      }
      else if (destFile.isDirectory())
      {
        String fname = mf.getFile(i).getName();
        int dotIdx = fname.lastIndexOf('.');
        if (dotIdx != -1)
          fname = fname.substring(0, dotIdx);
        // TODO: We need to add more file extensions
        targetFiles[i] = getUniqueFilePath(new java.io.File(destFile, fname).toString(), prefFileExt);
        tempFiles[i] = new java.io.File(destFile, fname + ".tmp");
      }
      else
      {
        String fname = destFile.toString();
        // Make sure the dot is in the file extension!
        String onlyName = destFile.getName();
        int dotIdx = onlyName.lastIndexOf('.');
        if (dotIdx != -1)
        {
          dotIdx += (fname.length() - onlyName.length());
          fname = fname.substring(0, dotIdx);
        }
        if (i == 0)
        {
          // Always append the proper file extension
          //					if (dotIdx == -1)
          targetFiles[i] = getUniqueFilePath(fname, prefFileExt);
          //					else
          //						targetFiles[i] = destFile;
          tempFiles[i] = new java.io.File(fname + ".tmp");
        }
        else
        {
          targetFiles[i] = getUniqueFilePath(fname + "-" + i, prefFileExt);
          tempFiles[i] = new java.io.File(fname + "-" + i + ".tmp");
        }
      }
    }
  }
  public java.io.File getTargetFile(int segment) { return (targetFiles != null && targetFiles.length > segment) ? targetFiles[segment] : null; }
  public java.io.File getTempFile(int segment) { return (tempFiles != null && tempFiles.length > segment) ? tempFiles[segment] : null; }
  public long estimateTranscodeSize()
  {
    long overallBitrate = targetFormat.getBitrate();
    if (overallBitrate == 0)
    {
      for (int i = 0; i < targetFormat.getNumberOfStreams(); i++)
      {
        sage.media.format.BitstreamFormat bf = targetFormat.getStreamFormat(i);
        overallBitrate += bf.getBitrate();
      }
    }
    // If we have no bitrate info, then it's probably a transmux and those are usually about the same size
    if (overallBitrate == 0)
      return mf.getFile(transcodeSegment).length();
    else
      return (overallBitrate / 8) * ((clipDuration <= 0 ? mf.getRecordDuration() : clipDuration) / 1000);
  }

  public void startTranscode()
  {
    createFilenames();
    setJobState(TRANSCODING);
    transcodeSegment = getStartingSegment();
    if (Sage.DBG) System.out.println("Initiating xcode for " + mf.getFile(transcodeSegment));
    Seeker.getInstance().requestFileStorage(getTempFile(transcodeSegment), estimateTranscodeSize());
    getTempFile(transcodeSegment).deleteOnExit();
    transcodeNow();
  }

  public void continueTranscode()
  {
    setJobState(TRANSCODING);
    transcodeSegment++;
    if (Sage.DBG) System.out.println("Initiating xcode for " + mf.getFile(transcodeSegment));
    Seeker.getInstance().requestFileStorage(getTempFile(transcodeSegment), estimateTranscodeSize());
    getTempFile(transcodeSegment).deleteOnExit();
    transcodeNow();
  }

  protected abstract void transcodeNow();

  // subclasses should call this first before they do their cleanup
  public void cleanupCurrentTranscode()
  {
    java.io.File deadFile = getTempFile(transcodeSegment);
    if (deadFile != null)
      Seeker.getInstance().clearFileStorageRequest(deadFile);
  }

  public void abandon()
  {
    //jobState = DESTROYED;
    if (tempFiles != null)
    {
      for (int i = 0; i < tempFiles.length; i++)
        tempFiles[i].delete();
    }
  }

  public java.io.File[] getTargetFiles() { return targetFiles; }
  public java.io.File[] getTempFiles() { return tempFiles; }

  public int getJobID()
  {
    return jobID;
  }

  public java.io.File getDestFile()
  {
    return destFile;
  }

  public abstract float getPercentComplete();

  public long getClipStartTime() { return clipStartTime; }
  public long getClipDuration() { return clipDuration; }

  public int getStartingSegment()
  {
    if (clipStartTime == 0)
      return 0;
    else
      return mf.segmentLocation(clipStartTime + mf.getRecordTime(), true);
  }

  public int getEndingSegment()
  {
    if (clipDuration == 0)
      return mf.getNumSegments() - 1;
    else
      return mf.segmentLocation(clipStartTime + clipDuration + mf.getRecordTime(), true);
  }

  protected MediaFile mf;
  protected sage.media.format.ContainerFormat targetFormat;
  protected String targetFormatName;
  protected int jobState;
  public int transcodeSegment;
  private java.io.File[] targetFiles;
  private java.io.File[] tempFiles;
  private java.io.File destFile;
  private boolean replaceOriginal;
  protected int jobID;
  protected long clipStartTime;
  protected long clipDuration;

  // NOTE: These are for DirectShow, but we're just leaving them in this class since that's what the names
  // match up for
  protected static native long createTranscodeEngine0(byte srcFormat, byte targetFormat, String srcFile, String destFile) throws PlaybackException, EncodingException;
  // this does not return until the transcoding is complete
  protected static native boolean doNativeTranscode0(long ptr) throws PlaybackException, EncodingException;
  // This does not return until the source filter has released the file
  protected static native void killNativeTranscode0(long ptr);
  /* LAZER -- Use the declares below for LAZER support
	// NOTE: These are for DirectShow, but we're just leaving them in this class since that's what the names
	// match up for
	public static native long createTranscodeEngine0(byte srcFormat, byte targetFormat, String srcFile, String destFile) throws PlaybackException, EncodingException;
	// this does not return until the transcoding is complete
	public static native boolean doNativeTranscode0(long ptr) throws PlaybackException, EncodingException;
	// This does not return until the source filter has released the file
	public static native void killNativeTranscode0(long ptr);
	public static native boolean readTranscodeData0(long ptr, byte[] buf); // must be 32K
   **/
}
