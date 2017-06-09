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
package sage.media.sub;

import sage.MediaFile;
import sage.Pooler;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Narflex
 */
public class SRTSubtitleHandler extends SubtitleHandler
{
  private final static int SUBTITLE_OK = 0;
  private final static int SUBTITLE_RETRY = 1;
  private final static int SUBTITLE_PASS = 2;

  // The max number of times to retry on a file with I/O errors while monitoring.
  private final static int MAX_RETRY = 3;

  private final Object srtLock = new Object();

  // Flag used to tell the monitoring thread it's time to clean up and terminate.
  private volatile boolean cleanup = false;
  // Used to signal we need a new subtitle now. Setting this true will cause the subtitle monitor to
  // return on the next added subtitle regardless of if it's the last one or not.
  private volatile long inDemand = -1;
  // Set to determine if we need to start a thread to continuously monitor this file for new entries.
  private boolean monitorFile = false;
  // This is set if we are actively monitoring a TV file for new subtitles.
  private boolean monitoringFile = false;
  // These are used if the file will be monitored for changes.
  private BufferedReader readers[] = new BufferedReader[0];
  // These are used to make sure that if a file keeps having I/O errors that we eventually stop
  // trying.
  private int retry[] = new int[0];
  // Used to try to fix the last subtitle in case we didn't get the whole thing. When we get a null
  // line instead of a blank line, this value changes from 0 (OK) to 1 (RETRY). The first attempt to
  // fix the subtitle changes the value to 2 (PASS). The value 2 (PASS) signals to stop trying to
  // fix the subtitle and is reset back to 0 (OK). This way we don't spend half a commercial break
  // re-parsing the same subtitle the entire time.
  private int lastSubtitle[] = new int[0];
  // This is use for monitoring.
  private MediaFile sourceFile = null;

  // These are the files we are actually monitoring. This is set when we first load everything.
  private sage.media.format.SubpictureFormat[] subs = new sage.media.format.SubpictureFormat[0];

  // The subtitle text is gathered here. We really shouldn't keep creating this expensive object
  // since it's slowing things down measurably.
  private final StringBuilder stringBuilder = new StringBuilder();

  /** Creates a new instance of SRTSubtitleHandler */
  public SRTSubtitleHandler(sage.media.format.ContainerFormat inFormat)
  {
    super(inFormat);
  }

  public String getMediaFormat()
  {
    return sage.media.format.MediaFormat.SRT;
  }

  /*public static long parseSRTTime(String s)
  {
    if (s.length() != 12)
    {
      if (sage.Sage.DBG) System.out.println("SRT time strings are always 12 chars long! We got: " + s);
      return -1;
    }
    long rv = 0;
    try
    {
      rv += Integer.parseInt(s.substring(0, 2)) * sage.Sage.MILLIS_PER_HR;
      rv += Integer.parseInt(s.substring(3, 5)) * sage.Sage.MILLIS_PER_MIN;
      rv += Integer.parseInt(s.substring(6, 8)) * 1000;
      rv += Integer.parseInt(s.substring(9));
    }
    catch (Exception e)
    {
      if (sage.Sage.DBG) System.out.println("ERROR parsing SRT time entry of:" + e);
      return -1;
    }
    return rv;
  }*/

  @Override
  public void loadSubtitlesFromFiles(sage.MediaFile sourceFile)
  {
    // Subtitles are always loaded from here only once, so it's not a problem to cache this object.
    this.sourceFile = sourceFile;

    // If there are multiple subtitle tracks; then there have to be multiple SRT files
    subs = mediaFormat.getSubpictureFormats(sage.media.format.MediaFormat.SRT);
    getLanguages();

    // While this could be deduced to just checking if the MediaFile is TV anywhere we check the
    // value of monitorFile, this allows us to be more flexible if we want to fine-tune that enables
    // monitoring in the future.
    monitorFile = sourceFile.isTV();

    // Subtitles are always loaded from here only once, so it's not a problem to initialize
    // everything here with the assumption that we don't need to clean anything up first.
    readers = new BufferedReader[subs.length];
    retry = new int[subs.length];
    Arrays.fill(retry, MAX_RETRY);
    lastSubtitle = new int[subs.length];
    Arrays.fill(lastSubtitle, SUBTITLE_OK);

    if (!monitorFile)
    {
      loadSubtitlesFromFiles();
    }
    else
    {
      monitoringFile = true;
      // Since we are creating a new thread either way, load the subtitles asynchronously.
      Runnable execute = new Runnable()
      {
        @Override
        public void run()
        {
          if (SUB_DEBUG) System.out.println("Monitoring for new SRT subtitles.");

          try
          {

            while (!cleanup)
            {
              try
              {
                synchronized (srtLock)
                {
                  // Find all new subtitles. This method can also be forced to return early if we
                  // need a new subtitle right this instant, so it's not a problem if the polling
                  // via this thread doesn't completely keep up with the edge of video playback. It
                  // should be a little more efficient for the video to tell us when it needs a new
                  // subtitle versus using more aggressive polling.
                  loadSubtitlesFromFiles();
                }

                // This is only supposed to be fast enough to keep the number of new subtitles
                // generally below 10 per file per poll.
                Thread.sleep(5000);

                if (cleanup)
                  break;
              }
              catch(InterruptedException e)
              {
                break;
              }
            }
          }
          catch (Exception e)
          {
            if (sage.Sage.DBG) System.out.println("An exception was created while monitoring for new SRT subtitles:" + e);
            if (sage.Sage.DBG) e.printStackTrace(System.out);
          }
          finally
          {
            synchronized (srtLock)
            {
              for (BufferedReader reader : readers)
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
          }
        }
      };

      Pooler.execute(execute, "SRTSubtitleMonitor");
    }
  }

  private void loadSubtitlesFromFiles()
  {
    int forceByteCharset = -1;
    for (int i = 0; i < subs.length; i++)
    {
      // If a particular file has failed too many times, we stop trying to read it.
      if (retry[i] <= 0)
        continue;

      List<SubtitleEntry> newSubEntries = subLangEntryMap.get(subLangs[i]);

      if (newSubEntries == null)
      {
        // The new entries are not visible outside of this method until they are added the the
        // HashMap in this step. Also the HashMap is concurrent, so there are no additional
        // considerations about thread-safety here.
        subLangEntryMap.put(subLangs[i], newSubEntries = new ArrayList<SubtitleEntry>());
      }

      // If these are not the subtitles we actually need, skip where we are in this file until
      // the next poll. subEntries and newSubEntries will be the same object if we are on the
      // currently selected subtitles.
      if (monitorFile && inDemand != -1 && subEntries != newSubEntries)
      {
        continue;
      }

      java.io.BufferedReader inStream = null;
      try
      {
        if (readers[i] == null)
        {
          subtitleLock.writeLock().lock();

          try
          {
            // We don't know where we left off, remove the all entries so we don't create
            // duplicates.
            newSubEntries.clear();
          }
          finally
          {
            subtitleLock.writeLock().unlock();
          }

          // Now read in the SRT file and fill up the newSubEntries List
          inStream = sage.IOUtils.openReaderDetectCharset(new java.io.File(subs[i].getPath()), (forceByteCharset == i) ? sage.Sage.BYTE_CHARSET : sage.Sage.I18N_CHARSET, sourceFile.isLocalFile());

          if (monitorFile)
          {
            // Later if readers[i] is null, we know to close the stream because we're not going to be
            // polling this stream.
            readers[i] = inStream;

            // Just in case, so we get a heads up on why someone might have noticed random subtitles
            // missing.
            if (!inStream.markSupported())
            {
              if (SUB_DEBUG) System.out.println("WARNING: mark is not supported. SRT subtitle retry is not available.");
            }
          }
        }
        else
        {
          inStream = readers[i];

          // Remove the last subtitle so we can try and parse it again since we didn't see a blank
          // line on the first pass. We can do this reliably because SRT subtitles are not being
          // sorted on insertion.
          if (lastSubtitle[i] == SUBTITLE_RETRY)
          {
            if (SUB_DEBUG) System.out.println("Retrying last SRT subtitle.");

            subtitleLock.writeLock().lock();

            try
            {
              // It is unlikely that this flag is set and there aren't any subtitle entries, but we
              // check anyway.
              if (newSubEntries.size() > 0)
                newSubEntries.remove(newSubEntries.size() - 1);
            }
            finally
            {
              subtitleLock.writeLock().unlock();
            }

            lastSubtitle[i] = SUBTITLE_PASS;
          }
        }

        // A mark is set before we start in case we don't have any good subtitles right away. We
        // will reset to this point if we don't have any complete subtitles. Almost any number will
        // do, but it needs to be large enough that it's unlikely that one subtitle will exceed this
        // value in size.
        if (inStream.markSupported() && monitorFile) inStream.mark(16384);
        String line = inStream.readLine();
        while (line != null)
        {
          // First is the subtitle entry # in the file
          long inTime = -1;
          long duration = -1;
          int subNum = -1;
          try
          {
            subNum = Integer.parseInt(line.trim());
          }
          catch (NumberFormatException e)
          {
            // When monitoring, this will generate a lot of log entries.
            if (sage.Sage.DBG && !monitorFile) System.out.println("ERROR parsing SRT entry number of: " + e);
          }
          // Next is the in/out timecodes for this sub entry
          // These are good to 'sync' on since they have a specific format to them
          do
          {
            line = inStream.readLine();
            if (line == null) break;
            try
            {
              long[] timeSpan = parseTimeRange(line);
              inTime = timeSpan[0];
              duration = timeSpan[1];
            }
            catch (Exception e)
            {
              if (sage.Sage.DBG) System.out.println("ERROR in SRT file; didn't find timecode on line; instead found: " + line + " error=" + e);
              continue;
            }
            break;
          }while(true);
          // Always reset the string builder.
          stringBuilder.setLength(0);
          line = inStream.readLine();
          while (line != null && (line = line.trim()).length() > 0)
          {
            if (stringBuilder.length() > 0)
              stringBuilder.append('\n');
            // Strip out the tags while we append the string
            boolean inTag = false;
            for (int j = 0;  j < line.length(); j++)
            {
              char c = line.charAt(j);
              if (!inTag && c == '<')
                inTag = true;
              else if (inTag && c == '>')
                inTag = false;
              else if (!inTag)
                stringBuilder.append(c);
            }
            line = inStream.readLine();
          }
          if (stringBuilder.length() > 0)
          {
            subtitleLock.writeLock().lock();

            try
            {
              newSubEntries.add(new SubtitleEntry(stringBuilder.toString(), inTime, duration));
            }
            finally
            {
              subtitleLock.writeLock().unlock();
            }

            // Add this new Subtitle Entry
            if (SUB_DEBUG) System.out.println("Found new SRT subtitle entry in=" + inTime + " dur=" + duration + " text=" + stringBuilder);

            // If the last line was null, it's possible that we have an incomplete subtitle. We
            // added the subtitle anyway, but on the next pass remove it and re-parse it one more
            // time.
            lastSubtitle[i] =
                inStream.markSupported() && lastSubtitle[i] == SUBTITLE_OK && line == null ?
                    SUBTITLE_RETRY : SUBTITLE_OK;

            // Adjust the mark to the last good subtitle. We will reset to this point if we don't
            // make it here again so we don't skip subtitles. At this point we have either already
            // read the blank line between subtitles or we are about to break out of the loop.
            if (inStream.markSupported() && monitorFile && lastSubtitle[i] == SUBTITLE_OK) inStream.mark(16384);

            // We only need one new subtitle that's passed the current media time to continue. The
            // monitoring thread should mostly keep us ahead of the need to do this. We also make
            // sure we are returning with subtitles relevant to the currently in use subtitles.
            // subEntries and newSubEntries will be the same object if this is the case.
            if (monitorFile && inDemand != -1 && subEntries == newSubEntries && inTime > inDemand)
            {
              inDemand = -1;

              // Reset the stream back to right after the last good subtitle or if no subtitles were
              // found, the beginning of the file so we can try again with more data later. Even
              // though this subtitle could be displayed with missing text, if we were to rewind the
              // recording, there's no reason why we shouldn't try to make this correct the second
              // time around.
              if (inStream.markSupported() && monitorFile) inStream.reset();

              // We have what we need, don't check any other streams.
              return;
            }

            // If these are not the subtitles we actually need, skip where we are in this file until
            // the next poll.
            if (monitorFile && inDemand != -1 && subEntries != newSubEntries)
            {
              break;
            }
          }
          if (line != null) // it's the blank line
            line = inStream.readLine();
        }

        // Reset the stream back to right after the last good subtitle or if no subtitles were
        // found, the beginning of the file so we can try again with more data later.
        if (inStream.markSupported() && monitorFile) inStream.reset();

        // Reset attempt count on success.
        retry[i] = MAX_RETRY;
      }
      catch (java.io.IOException e)
      {
        if (sage.Sage.DBG) System.out.println("ERROR loading subtitle file from " + subs[i] + " of " + e);
        if (sage.Sage.DBG) e.printStackTrace(System.out);

        // This ensures the reader will be closed. If we are monitoring, the stored subtitles will
        // be cleared, the file will be re-opened and parsed from the beginning. Hopefully the
        // second run will have a better outcome.
        readers[i] = null;

        // Decrement the number of attempts left.
        retry[i]--;
      }
      finally
      {
        // Only close the stream if we will not be monitoring it.
        if (inStream != null && readers[i] == null)
        {
          try
          {
            inStream.close();
          }
          catch (Exception e){}
        }
      }
    }
  }

  @Override
  public long getTimeTillUpdate(long currMediaTime)
  {
    long next = super.getTimeTillUpdate(currMediaTime);

    if (!monitoringFile)
      return next;

    if (next == NO_MORE_SUBS_LONG_WAIT)
    {
      // We need to set this flag outside of synchronization, so if the monitoring thread is
      // currently parsing subtitles, it will return quickly once it finds a subtitle that's past
      // the current media time even if there are more subtitles pending.
      inDemand = currMediaTime;

      synchronized (srtLock)
      {
        // We cannot reach this point until the monitoring thread is not checking for new subtitles
        // and we are guaranteed that it will not be able to run again until we exit this
        // synchronized block. If inDemand is still set to the current media time, that means the
        // monitoring thread probably wasn't loading new subtitles or there weren't any new
        // subtitles available, so we try one more time load the next one here.
        if (inDemand != -1)
        {
          // This will return after only finding one new subtitle.
          loadSubtitlesFromFiles();
        }

        // Run this inside the sync block so we have no contention between the monitoring thread
        // adding more subtitles and getting the required subtitle.
        next = super.getTimeTillUpdate(currMediaTime);
      }

      if (next == NO_MORE_SUBS_LONG_WAIT)
      {
        // This will cause VideoFrame to come back in a half second. Hopefully we have new subtitles
        // at this time. During commercial breaks there may not be any subtitles, so it's best to
        // not put a limit on how many times this is allowed to happen or to increase the delay
        // because we might miss the first subtitle when the show comes back.
        next = 1000;

        // Reset the subtitles position.
        subEntryPos = -1;
      }
    }

    return next;
  }

  @Override
  public void cleanup()
  {
    // This is synchronized in case a different thread calls getTimeTillUpdate() when it interrupts
    // the monitoring thread to get it to stop sleeping and check for an update immediately.
    synchronized (srtLock)
    {
      if (monitoringFile)
      {
        cleanup = true;
      }
    }
  }
}
