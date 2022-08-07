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

/**
 *
 * @author Narflex
 */
public class FFMPEGTranscodeJob extends TranscodeJob
{

  /** Creates a new instance of FFMPEGTranscodeJob */
  public FFMPEGTranscodeJob(MediaFile mf, String inFormatName, sage.media.format.ContainerFormat targetFormat,
      boolean replaceOriginal, java.io.File inDestFile, long inClipStartTime, long inClipDuration)
  {
    super(mf, inFormatName, targetFormat, replaceOriginal, inDestFile, inClipStartTime, inClipDuration);
  }
  public FFMPEGTranscodeJob(int inJobID)
  {
    super(inJobID);
  }

  public void saveToProps()
  {
    super.saveToProps();
    Sage.put(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + TRANSCODE_PROCESSOR, "sagetv");
  }

  protected void transcodeNow()
  {
    tranny = new FFMPEGTranscoder();
    tranny.setSourceFile(null, mf.getFile(transcodeSegment));
    tranny.setOutputFile(getTempFile(transcodeSegment));
    tranny.setTranscodeFormat(mf.getFileFormat(), targetFormat);
    if (segmentForLastPass != transcodeSegment)
    {
      currPass = 0;
      segmentForLastPass = transcodeSegment;
    }
    // Don't do multipass for 3GP, PSP or iPod because of the bitrate restrictions
    enableMultipass = Sage.getBoolean("transcoder/enable_multipass_encoding", false) &&
        ("mpeg4".equals(targetFormat.getPrimaryVideoFormat()) || "xvid".equals(targetFormat.getPrimaryVideoFormat())) &&
        !"3gp".equals(targetFormat.getFormatName()) && !"mp4".equals(targetFormat.getFormatName()) && !"psp".equals(targetFormat.getFormatName());
    if (enableMultipass)
    {
      if (currPass == 0)
      {
        // hasn't started yet, on the first pass
        currPass = 1;
      }
      else
      {
        // We're on our subsequent pass now
        currPass++;
      }
      tranny.setPass(currPass);
    }
    if (clipStartTime > 0 || clipDuration > 0)
    {
      if (transcodeSegment == getStartingSegment())
      {
        if (transcodeSegment == getEndingSegment())
        {
          tranny.setEditParameters(clipStartTime, clipDuration);
        }
        else if (clipStartTime > 0)
        {
          tranny.setEditParameters(clipStartTime, 0);
        }
      }
      else if (transcodeSegment == getEndingSegment() && clipDuration > 0)
      {
        tranny.setEditParameters(0, clipDuration - (mf.getStart(transcodeSegment) - mf.getRecordTime() - clipStartTime));
      }
    }
    try
    {
      tranny.startTranscode();
    }
    catch (java.io.IOException ex)
    {
      System.out.println("TRANSCODING ENGINE FAILED TO CREATE");
      jobState = TRANSCODE_FAILED;
      saveToProps();
      tranny = null;
      return;
    }
    monitorThread = new TranscodeMonitor();
    monitorThread.setDaemon(true);
    monitorThread.setPriority(Thread.MIN_PRIORITY);
    monitorThread.start();
  }

  public void cleanupCurrentTranscode()
  {
    super.cleanupCurrentTranscode();
    if (tranny != null)
    {
      tranny.stopTranscode();
      tranny = null;
    }
  }

  public float getPercentComplete()
  {
    FFMPEGTranscoder tempy = tranny;
    if (tempy != null)
    {
      long fullLength = clipDuration == 0 ? (mf.getDuration(transcodeSegment) - clipStartTime) : clipDuration;
      
      long currTime = tempy.getCurrentTranscodeStreamTime();
      float rv = ((float) currTime) / fullLength;
      if (enableMultipass)
        rv = (currPass == 1) ? rv/2.0f : (0.5f + rv/2.0f);
      return rv;
    }
    return 0;
  }

  private FFMPEGTranscoder tranny;
  private Thread monitorThread;
  private boolean enableMultipass;
  private int currPass;
  private int segmentForLastPass = -1;
  private class TranscodeMonitor extends Thread
  {
    public void run()
    {
      while (jobState == TRANSCODING && tranny != null)
      {
        if (tranny.isTranscodeDone())
        {
          if (tranny.didTranscodeCompleteOK())
          {
            if (enableMultipass && currPass == 1)
            {
              if (Sage.DBG) System.out.println("First pass for transcoding is done...starting the next pass...");
              transcodeNow();
              return;
            }
            jobState = TRANSCODING_SEGMENT_COMPLETE;
          }
          else
            jobState = TRANSCODE_FAILED;
          Ministry.getInstance().kick();
          saveToProps();
          return;
        }
        try{Thread.sleep(1000);}catch(Exception e){}
      }
    }
  }
}
