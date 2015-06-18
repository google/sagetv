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

/**
 *
 * @author Narflex
 */
public class SRTSubtitleHandler extends SubtitleHandler
{

  /** Creates a new instance of SRTSubtitleHandler */
  public SRTSubtitleHandler(sage.media.format.ContainerFormat inFormat)
  {
    super(inFormat);
  }

  public String getMediaFormat()
  {
    return sage.media.format.MediaFormat.SRT;
  }

  public static long parseSRTTime(String s)
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
    catch (Exception nfe)
    {
      if (sage.Sage.DBG) System.out.println("ERROR parsing SRT time entry of:" + nfe);
      return -1;
    }
    return rv;
  }

  public void loadSubtitlesFromFiles(sage.MediaFile sourceFile)
  {
    // If there's multiple subtitle tracks; then there has to be multiple SRT files
    sage.media.format.SubpictureFormat[] subs = mediaFormat.getSubpictureFormats(sage.media.format.MediaFormat.SRT);
    getLanguages();
    int forceByteCharset = -1;
    for (int i = 0; i < subs.length; i++)
    {
      java.io.File subFile = null;
      java.io.BufferedReader inStream = null;
      try
      {
        subFile = getLoadableSubtitleFile(sourceFile, new java.io.File(subs[i].getPath()));
        subEntries = new java.util.Vector();
        subLangEntryMap.put(subLangs[i], subEntries);

        // Now read in the SRT file and fill up the subEntries Vector
        inStream = sage.IOUtils.openReaderDetectCharset(subFile, (forceByteCharset == i) ? sage.Sage.BYTE_CHARSET : sage.Sage.I18N_CHARSET);
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
            if (sage.Sage.DBG) System.out.println("ERROR parsing SRT entry number of: " + e);
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
          StringBuffer sb = new StringBuffer();
          line = inStream.readLine();
          while (line != null && (line = line.trim()).length() > 0)
          {
            if (sb.length() > 0)
              sb.append('\n');
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
                sb.append(c);
            }
            line = inStream.readLine();
          }
          if (sb.length() > 0)
          {
            // Add this new Subtitle Entry
            if (sage.Sage.DBG) System.out.println("Found new SRT subtitle entry in=" + inTime + " dur=" + duration + " text=" + sb);
            subEntries.add(new SubtitleEntry(sb.toString(), inTime, duration));
          }
          if (line != null) // it's the blank line
            line = inStream.readLine();
        }
      }
      catch (java.io.IOException e)
      {
        if (sage.Sage.DBG) System.out.println("ERROR loading subtitle file from " + subs[i] + " of " + e);
        if (sage.Sage.DBG) e.printStackTrace();
      }
      finally
      {
        if (subFile != null && !sourceFile.isLocalFile())
          subFile.delete();
        if (inStream != null)
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

}
