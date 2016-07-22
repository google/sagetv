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

import sage.Sage;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 * @author Narflex
 */
public abstract class SubtitleHandler
{
  public static final boolean SUB_DEBUG = Sage.DBG && Sage.getBoolean("debug_subtitles", false);
  public static final long NO_MORE_SUBS_LONG_WAIT = sage.Sage.MILLIS_PER_WEEK;
  public static final int PTS_VALID_MASK = 0x1;
  public static final int FLUSH_SUBTITLE_QUEUE = 0x2;
  public static final int CC_SUBTITLE_MASK = 0x10;

  /** Creates a new instance of SubtitleHandler */
  public SubtitleHandler(sage.media.format.ContainerFormat inFormat)
  {
    mediaFormat = inFormat;
    subEntries = new java.util.ArrayList<SubtitleEntry>();
    subLangEntryMap = new ConcurrentHashMap<String, List<SubtitleEntry>>();
    external = (mediaFormat != null);
  }

  // Returns the index into the List for the first item that overlaps (or is right after if no overlap, unless its the last one)
  protected int getSubEntryIndex(long targetTime)
  {
    subtitleLock.readLock().lock();

    try
    {
      int low = 0;
      int high = subEntries.size() - 1;
      int mid = -1;
      while (low <= high)
      {
        mid = (low + high) >> 1;
      SubtitleEntry midVal = subEntries.get(mid);
      long cmp = midVal.start - targetTime;

      if (cmp < 0)
        low = mid + 1;
      else if (cmp > 0)
        high = mid - 1;
      else
      {
        break;
      }
      }
      // Now back up to be sure we're on the first overlap
      if (mid < 0)
        return mid;
      if (mid < subEntries.size() - 1)
        mid++;
      while (mid > 0)
      {
        SubtitleEntry currEntry = subEntries.get(mid);
        SubtitleEntry prevEntry = subEntries.get(mid - 1);
        if (prevEntry.start >= targetTime ||
            (prevEntry.duration > 0 && prevEntry.start <= targetTime && prevEntry.start + prevEntry.duration > targetTime))
          mid--;
        else if (prevEntry.duration == 0 && prevEntry.start <= targetTime && currEntry.start > targetTime)
          mid--;
        else
          break;
      }
      return mid;
    }
    finally
    {
      subtitleLock.readLock().unlock();
    }
  }

  public String getSubtitleText(long currMediaTime, boolean willDisplay)
  {
    if (external)
      currMediaTime += delay;
    String rv = "";

    subtitleLock.readLock().lock();

    try
    {
      boolean foundValid = false;
      for (int i = getSubEntryIndex(currMediaTime); i < subEntries.size(); i++)
      {
        SubtitleEntry currEntry = subEntries.get(i);
        SubtitleEntry nextEntry = ((i < subEntries.size() - 1) ? subEntries.get(i + 1) : null);
        if (currEntry.start <= currMediaTime)
        {
          if ((currEntry.duration > 0 && currEntry.start + currEntry.duration > currMediaTime) ||
              (currEntry.duration == 0 && (nextEntry == null || nextEntry.start > currMediaTime)))
          {
            foundValid = true;
            if (rv.length() == 0 && willDisplay)
            {
              subEntryPos = i;
              currBlank = false;
            }
            rv = (rv.length() == 0) ? currEntry.text : (rv + "\n" + currEntry.text);
          }
          else if (rv.length() == 0 && willDisplay)
          {
            subEntryPos = i; // We're on a blank; so jump ahead one in the TTU estimate
            currBlank = true;
          }
        }
        else
        {
          if (rv.length() == 0 && willDisplay && !foundValid)
          {
            subEntryPos = i; // We're on a blank; so jump ahead one in the TTU estimate
            currBlank = true;
          }
          break;
        }
      }
    }
    finally
    {
      subtitleLock.readLock().unlock();
    }
    if (SUB_DEBUG) System.out.println("Got Subtitle text=" + rv + " subEntryPos=" + subEntryPos);
    return rv;
  }

  // If storage isn't big enough; then that needs to be noticed by the return value being larger than the size of 'storage'
  // The array can then be re-allocated and this method called again to load the complete data
  public int getSubtitleBitmap(long currMediaTime, boolean willDisplay, byte[] storage, boolean stripHeaders)
  {
    throw new UnsupportedOperationException("getSubtitleBitmap is not implemented for this subtitle handler!!");
  }

  public String[] getLanguages()
  {
    if (mediaFormat == null && subLangs == null)
      return subLangs = sage.Pooler.EMPTY_STRING_ARRAY;
    if (subLangs == null)
    {
      subLangs = mediaFormat.getSubpictureStreamSelectionDescriptors(external ? getMediaFormat() : null);
    }
    return subLangs;
  }

  public final boolean isEnabled()
  {
    return enabled;
  }

  public final void setEnabled(boolean x)
  {
    enabled = x;
    if (enabled && currLang == null)
    {
      getLanguages();
      if (subLangs.length == 0)
        currLang = "";
      else
        setCurrLanguage(subLangs[0]);
    }
  }

  public void setCurrLanguage(String x)
  {
    if (x == null) return;
    if (!x.equals(currLang))
    {
      currLang = x;

      // It's always faster to try to get the item, even if it doesn't exist.
      List<SubtitleEntry> newList = subLangEntryMap.get(currLang);

      if (newList != null)
      {
        subtitleLock.writeLock().lock();

        try
        {
          subEntries = newList;
          subEntryPos = -1; // reset the position
        }
        finally
        {
          subtitleLock.writeLock().unlock();
        }
      }
    }
  }

  public String getCurrLanguage()
  {
    return currLang;
  }

  // Returns the amount of time in milliseconds before the next text update needs to occur
  public long getTimeTillUpdate(long currMediaTime)
  {
    if (external)
      currMediaTime += delay;

    // NOTE: If there's multiple subs to display at once this won't notice the update time for the non-primary ones

    subtitleLock.readLock().lock();

    try
    {
      if (subEntries.isEmpty())
        return NO_MORE_SUBS_LONG_WAIT;
      if (subEntryPos < 0)
      {
        return Math.max(0, (subEntries.get(0)).start - currMediaTime);
      }
      SubtitleEntry prevEntry = (subEntryPos > 0 ? subEntries.get(subEntryPos - 1) : null);
      if (prevEntry != null)
      {
        // This checks if the position we have is too far forward in the stream relative to the current media time
        if ((prevEntry.duration == 0 && prevEntry.start >= currMediaTime) ||
            (prevEntry.duration != 0 && prevEntry.start + prevEntry.duration > currMediaTime))
        {
          return 0; // We're on the wrong one; need to update now
        }
      }
      SubtitleEntry currEntry = subEntries.get(subEntryPos);
      SubtitleEntry nextEntry = (subEntryPos == subEntries.size() - 1) ?
          null : (subEntries.get(subEntryPos + 1));
      long waitTime;
      if (currEntry.duration > 0)
      {
        if (currEntry.start + currEntry.duration <= currMediaTime && nextEntry == null && currBlank)
          waitTime = NO_MORE_SUBS_LONG_WAIT;
        else if (currEntry.start >= currMediaTime || currBlank)
          waitTime = currEntry.start - currMediaTime;
        else if (currEntry.start + currEntry.duration > currMediaTime)
          waitTime = currEntry.start + currEntry.duration - currMediaTime;
        else
          waitTime = 0;
      }
      else
      {
        waitTime = ((currEntry.start > currMediaTime || (currBlank && subEntryPos == 0)) ? (currEntry.start - currMediaTime) : NO_MORE_SUBS_LONG_WAIT);
      }
      if (nextEntry == null)
        return Math.max(0, waitTime);
      long nextTime = nextEntry.start - currMediaTime;
      if (nextTime < waitTime)
        waitTime = nextTime;
      if (waitTime < 0)
        waitTime = 0;
      return waitTime;
    }
    finally
    {
      subtitleLock.readLock().unlock();
    }
  }

  // We need the MediaFile object so we can resolve stv:// if we need to.
  public abstract void loadSubtitlesFromFiles(sage.MediaFile sourceFile);

  public void cleanup()
  {

  }

  public boolean hasExternalSubtitles()
  {
    return external;
  }

  public boolean mpControlled()
  {
    return !external;
  }

  public boolean areTextBased()
  {
    return true;
  }

  public static SubtitleHandler createSubtitleHandlerDirect(sage.VideoFrame vf, byte[] rawConfig, int flags, sage.media.format.ContainerFormat cf, String subFormat)
  {
    if ((flags & CC_SUBTITLE_MASK) == CC_SUBTITLE_MASK)
    {
      return new CCSubtitleHandler();
    }
    else if (rawConfig != null && rawConfig.length > 0)
    {
      if (sage.Sage.DBG) System.out.println("Creating new embedded subtitle handler formatHelper=" + subFormat);
      // Check if they're MP4 text-based or vobsub subtitles
      if (cf != null)
      {
        if (cf.getFormatName().equals(sage.media.format.MediaFormat.QUICKTIME))
        {
          if (subFormat != null && subFormat.startsWith("TEXT"))
          {
            return new MP4TextSubtitleHandler(rawConfig, false);
          }
          else if (subFormat != null && subFormat.startsWith("TX3G"))
          {
            return new MP4TextSubtitleHandler(rawConfig, true);
          }
          else if (rawConfig.length > 8 && rawConfig[4] == 'e' && rawConfig[5] == 's' && rawConfig[6] == 'd' && rawConfig[7] == 's')
          {
            // VobSub embedded in an MP4 file
            return new VobSubSubtitleHandler(vf, null);
          }
        }
        else if (cf.getFormatName().equals(sage.media.format.MediaFormat.MATROSKA) && "DVDSUB".equals(subFormat))
        {
          return new VobSubSubtitleHandler(vf, null);
        }
      }
      try
      {
        String configText = new String(rawConfig, 0, (rawConfig.length > 0 && rawConfig[rawConfig.length - 1] == 0) ? (rawConfig.length - 1) : rawConfig.length, sage.Sage.I18N_CHARSET);
        if (configText.indexOf("[Script Info]") != -1 && configText.indexOf("[V4") != -1)
        {
          // SSA/ASS subtitles
          return new SSASubtitleHandler(configText);
        }
      }
      catch (java.io.UnsupportedEncodingException uee)
      {
        if (sage.Sage.DBG) System.out.println("ERROR with text encoding:" + uee);
      }
    }
    return new RawSubtitleHandler();
  }

  public static SubtitleHandler createSubtitleHandler(sage.VideoFrame vf, sage.MediaFile mf)
  {
    sage.media.format.ContainerFormat cf = mf.getFileFormat();
    sage.media.format.SubpictureFormat[] subpics = cf.getSubpictureFormats();
    SubtitleHandler rv = null;
    for (int i = 0; i < subpics.length && rv == null; i++)
    {
      String targetFormat = subpics[i].getFormatName();
      if (sage.media.format.MediaFormat.SSA.equals(targetFormat))
        rv = new SSASubtitleHandler(cf);
      else if (sage.media.format.MediaFormat.SUB.equals(targetFormat))
        rv = new SUBSubtitleHandler(cf);
      else if (sage.media.format.MediaFormat.SAMI.equals(targetFormat))
        rv = new SAMISubtitleHandler(cf);
      else if (sage.media.format.MediaFormat.SRT.equals(targetFormat))
        rv = new SRTSubtitleHandler(cf);
      else if (sage.media.format.MediaFormat.VOBSUB.equals(targetFormat))
        rv = new VobSubSubtitleHandler(vf, cf);
    }
    if (rv != null)
      rv.loadSubtitlesFromFiles(mf);
    return rv;
  }

  // Returns true if the timing delay for the next subtitle update has changed (i.e. VF needs a kick)
  public boolean postSubtitleInfo(long time, long dur, byte[] rawText, int flags)
  {
    subtitleLock.writeLock().lock();

    try
    {
      if ((flags & FLUSH_SUBTITLE_QUEUE) == FLUSH_SUBTITLE_QUEUE)
      {
        subEntries.clear();
        subEntryPos = -1;
      }

      return insertEntryForPostedInfo(time, dur, rawText);
    }
    finally
    {
      subtitleLock.writeLock().unlock();
    }
  }

  protected boolean insertEntryForPostedInfo(long time, long dur, byte[] rawText)
  {
    String theText;
    try
    {
      theText = new String(rawText, 0, (rawText.length > 0 && rawText[rawText.length - 1] == 0) ? (rawText.length - 1) : rawText.length, sage.Sage.I18N_CHARSET);
    }
    catch (java.io.UnsupportedEncodingException uee)
    {
      theText = new String(rawText, 0, (rawText.length > 0 && rawText[rawText.length - 1] == 0) ? (rawText.length - 1) : rawText.length);
    }
    // Now strip any HTML tags that may be in the text
    if (theText.indexOf('<') != -1)
    {
      StringBuilder sb = new StringBuilder(theText.length());
      boolean inTag = false;
      for (int j = 0;  j < theText.length(); j++)
      {
        char c = theText.charAt(j);
        if (!inTag && c == '<')
          inTag = true;
        else if (inTag && c == '>')
          inTag = false;
        else if (!inTag)
          sb.append(c);
      }
      theText = sb.toString();
    }
    return insertSubtitleEntry(new SubtitleEntry(theText, time, dur));
  }

  protected boolean insertSubtitleEntry(SubtitleEntry newEntry)
  {
    if (newEntry == null) return false;

    subtitleLock.writeLock().lock();

    try
    {
      if (subEntries == null) return false;

      boolean rv = subEntryPos == subEntries.size() - 1;
      if (subEntries.isEmpty())
        subEntries.add(newEntry);
      else
      {
        int idx = java.util.Collections.binarySearch(subEntries, newEntry);
        if (idx < 0)
          idx = -(idx + 1);
        // Check to make sure we insert after the last one w/ the same start time
        for (idx = idx + 1; idx < subEntries.size() && (subEntries.get(idx)).start == newEntry.start; idx++);
        idx = Math.min(idx, subEntries.size());
        subEntries.add(idx, newEntry);
      }
      return rv;
    }
    finally
    {
      subtitleLock.writeLock().unlock();
    }
  }

  public long getOffsetToRelativeSub(int adjustAmount, long currMediaTime)
  {
    // Find the time diff between the sub that should be displayed at the specified time and the one that many offsets from it.
    currMediaTime += delay;

    subtitleLock.readLock().lock();

    try
    {
      if (subEntries.isEmpty())
        return 0;
      int tempPos = subEntryPos + adjustAmount;
      tempPos = Math.max(0, Math.min(tempPos, subEntries.size() - 1));
      // If we're wanting to show the next sub right now and that sub is actually the current index; just not displayed yet
      if (subEntryPos >= 0 && subEntries.get(subEntryPos).start > currMediaTime && adjustAmount > 0)
        adjustAmount--;
      SubtitleEntry ent = subEntries.get(tempPos);
      return ent.start - currMediaTime;
    }
    finally
    {
      subtitleLock.readLock().unlock();
    }
  }

  // This deals with resolving server references to external subtitle files and downloading them to temp storage to use ourself
  /*protected static java.io.File getLoadableSubtitleFile(sage.MediaFile mf, java.io.File subFile) throws java.io.IOException
  {
    if (mf.isLocalFile())
    {
      return subFile;
    }
    else
    {
      if (sage.Sage.EMBEDDED || sage.NetworkClient.getSN().requestMediaServerAccess(subFile, true))
      {
        java.io.File tmpFile = java.io.File.createTempFile("stv", ".sub");
        if (SUB_DEBUG) System.out.println("Downloading remote subtitle file from: " + subFile + " to local path: " + tmpFile);
        tmpFile.deleteOnExit();
        mf.copyToLocalStorage(subFile, tmpFile);
        return tmpFile;
      }
      else
        throw new java.io.IOException("Cannot get MediaServer access to remote subtitle file:" + subFile);
    }
  }*/

  // This handles various formats for time range strings used in subtitle files.
  // This'll return {start, duration} for the array
  protected static long[] parseTimeRange(String str) throws Exception
  {
    str = str.trim();
    long start = 0;
    long stop = 0;
    // The first integer chars up until the first non-integer char is the hours of the start time
    int idx0 = 0;
    int idx1 = idx0;
    for (; idx1 < str.length(); idx1++)
    {
      if (!Character.isDigit(str.charAt(idx1)))
        break;
    }
    start += Integer.parseInt(str.substring(idx0, idx1)) * sage.Sage.MILLIS_PER_HR;
    // Next is minutes
    idx0 = idx1 = idx1 + 1;
    for (; idx1 < str.length(); idx1++)
    {
      if (!Character.isDigit(str.charAt(idx1)))
        break;
    }
    start += Integer.parseInt(str.substring(idx0, idx1)) * sage.Sage.MILLIS_PER_MIN;
    // Next is seconds
    idx0 = idx1 = idx1 + 1;
    for (; idx1 < str.length(); idx1++)
    {
      if (!Character.isDigit(str.charAt(idx1)))
        break;
    }
    start += Integer.parseInt(str.substring(idx0, idx1)) * 1000;
    // Next is milliseconds
    idx0 = idx1 = idx1 + 1;
    for (; idx1 < str.length(); idx1++)
    {
      if (!Character.isDigit(str.charAt(idx1)))
        break;
    }
    start += Integer.parseInt(str.substring(idx0, idx1)) * (idx1 - idx0 == 2 ? 10 : (idx1 - idx0 == 1 ? 100 : 1));
    if (idx1 == str.length())
    {
      // There's only a start time on this line
      return new long[] { start, 0 };
    }
    // Now skip chars until we're back at an integer
    idx0 = idx1 + 1;
    while (idx0 < str.length() && !Character.isDigit(str.charAt(idx0)))
      idx0++;
    idx1 = idx0;
    for (; idx1 < str.length(); idx1++)
    {
      if (!Character.isDigit(str.charAt(idx1)))
        break;
    }
    stop += Integer.parseInt(str.substring(idx0, idx1)) * sage.Sage.MILLIS_PER_HR;
    // Next is minutes
    idx0 = idx1 = idx1 + 1;
    for (; idx1 < str.length(); idx1++)
    {
      if (!Character.isDigit(str.charAt(idx1)))
        break;
    }
    stop += Integer.parseInt(str.substring(idx0, idx1)) * sage.Sage.MILLIS_PER_MIN;
    // Next is seconds
    idx0 = idx1 = idx1 + 1;
    for (; idx1 < str.length(); idx1++)
    {
      if (!Character.isDigit(str.charAt(idx1)))
        break;
    }
    stop += Integer.parseInt(str.substring(idx0, idx1)) * 1000;
    // Next is milliseconds
    idx0 = idx1 = idx1 + 1;
    for (; idx1 < str.length(); idx1++)
    {
      if (!Character.isDigit(str.charAt(idx1)))
        break;
    }
    stop += Integer.parseInt(str.substring(idx0, idx1)) * (idx1 - idx0 == 2 ? 10 : (idx1 - idx0 == 1 ? 100 : 1));
    return new long[] { start, stop - start };
  }

  public boolean isPaletteInitialized()
  {
    return false;
  }

  public byte[] getPaletteData()
  {
    return null;
  }

  public int getSubtitleBitmapFlags()
  {
    return 0;
  }

  // Returns the format type of the subtitles
  public String getMediaFormat()
  {
    return "";
  }

  public long getDelay()
  {
    return delay;
  }

  public void setDelay(long newDelay)
  {
    delay = newDelay;
  }

  // NOTE: You still need to call VF.kick() for that loop to run; but this'll ensure an update occurs at that point
  public void forceSubRefresh()
  {
    subtitleLock.writeLock().lock();

    try
    {
      subEntryPos = -1;
    }
    finally
    {
      subtitleLock.writeLock().unlock();
    }
  }

  // File format for the media we're currently playing back; it has subtitle details in it
  protected sage.media.format.ContainerFormat mediaFormat;
  // Cached subtitle language values
  protected String[] subLangs;
  protected String currLang = null;
  protected boolean enabled;

  // Use this lock for all reads and writes to subEntries and any SubtitleEntry objects from subLangEntryMap
  protected final ReadWriteLock subtitleLock = new ReentrantReadWriteLock(true);
  // Sorted list of all known subtitle data mapped from String(language)->List
  protected java.util.Map<String, java.util.List<SubtitleEntry>> subLangEntryMap; // If there's more than one language, we'll be using this
  protected java.util.List<SubtitleEntry> subEntries; // for the current language
  protected int subEntryPos = -1;
  protected boolean currBlank;

  protected boolean external;

  protected long delay;

  protected static class SubtitleEntry implements Comparable<SubtitleEntry>
  {
    public SubtitleEntry(String text, long start, long duration)
    {
      this.text = text;
      this.start = start;
      this.duration = duration;
    }

    public SubtitleEntry(long offset, long size, long start, long duration)
    {
      this.offset = offset;
      this.size = size;
      this.start = start;
      this.duration = duration;
    }

    public SubtitleEntry(byte[] rawdata, long start, long duration)
    {
      this.bitmapdata = rawdata;
      this.start = start;
      this.duration = duration;
      this.size = rawdata.length;
    }

    @Override
    public int compareTo(SubtitleEntry o)
    {
      if (start < o.start)
        return -1;
      else if (start > o.start)
        return 1;
      else if (duration < o.duration)
        return -1;
      else if (duration > o.duration)
        return 1;
      else if (text != null)
        return text.compareTo(o.text);
      else
        return (int) (offset - o.offset);
    }

    public String toString()
    {
      return "SubtitleEntry[start=" + start + " dur=" + duration + " text=" + text + "]";
    }

    public long start;
    public long duration;
    // For text based subs
    public String text;
    // For bitmap based subs
    public long offset;
    public long size;
    // When we hold the bitmap data in memory; these MUST be consumed and released
    public byte[] bitmapdata;
  }

  public RollupAnimation getAnimationObject() {
    return null;
  }

}
