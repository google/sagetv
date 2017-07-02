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
public class DShowTranscodeJob extends TranscodeJob implements Runnable
{
  static
  {
    if (Sage.WINDOWS_OS)
    {
      sage.Native.loadLibrary("DShowTranscode");
    }
  }

  /** Creates a new instance of DShowTranscodeJob */
  public DShowTranscodeJob(MediaFile mf, String inFormatName, sage.media.format.ContainerFormat targetFormat,
      boolean replaceOriginal, java.io.File inDestFile)
  {
    super(mf, inFormatName, targetFormat, replaceOriginal, inDestFile);
  }

  public void saveToProps()
  {
    return;
    //		super.saveToProps();
    //		Sage.put(Ministry.TRANSCODE_JOB_PROPS + '/' + jobID + '/' + TRANSCODE_PROCESSOR, "directshow");
  }
  protected void transcodeNow()
  {
    try
    {

      // Now determine which transcoding engine to use
      nativePtr = createTranscodeEngine0(mf.getLegacyMediaSubtype(), MediaFile.getLegacyMediaSubtype(targetFormat),
          mf.getFile(transcodeSegment).toString(), getTempFile(transcodeSegment).toString());
    }
    catch (Throwable e)
    {
      System.out.println("TRANSCODING ABANDONED ERROR:" + e);
      jobState = TRANSCODE_FAILED;
    }
    if (nativePtr != 0)
    {
      Pooler.execute(this, "Transcode", Thread.MIN_PRIORITY);
    }
    else
    {
      System.out.println("TRANSCODING ENGINE FAILED TO CREATE");
      jobState = TRANSCODE_FAILED;
    }
  }

  public void run()
  {
    // This just executes the current transcode job which is indicated by the transcodeSegment and then
    // puts itself into the limbo state
    try
    {
      if (doNativeTranscode0(nativePtr))
      {
        jobState = TRANSCODING_SEGMENT_COMPLETE;
      }
      else
        jobState = TRANSCODE_FAILED;
    }
    catch (Exception e)
    {
      System.out.println("TRANSCODING ABANDONED ERROR:" + e);
      jobState = TRANSCODE_FAILED;
    }
    Ministry.getInstance().kick();
  }

  public void cleanupCurrentTranscode()
  {
    super.cleanupCurrentTranscode();
    killNativeTranscode0(nativePtr);
    nativePtr = 0;
  }

  public float getPercentComplete()
  {
    // We can guess this based on the file size
    java.io.File sf = mf.getFile(transcodeSegment);
    java.io.File tf = getTempFile(transcodeSegment);
    return (sf == null || tf == null) ? 0 : Math.min(1.0f, ((float)tf.length()) / sf.length());
  }

  private long nativePtr;

}
