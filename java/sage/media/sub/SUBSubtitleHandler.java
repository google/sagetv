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
public class SUBSubtitleHandler extends SubtitleHandler
{

  /** Creates a new instance of SUBSubtitleHandler */
  public SUBSubtitleHandler(sage.media.format.ContainerFormat inFormat)
  {
    super(inFormat);
  }

  public String getMediaFormat()
  {
    return sage.media.format.MediaFormat.SUB;
  }

  public void loadSubtitlesFromFiles(sage.MediaFile sourceFile)
  {
    // If there's multiple subtitle tracks; then there has to be multiple SUB files
    sage.media.format.SubpictureFormat[] subs = mediaFormat.getSubpictureFormats(sage.media.format.MediaFormat.SUB);
    getLanguages();
    for (int i = 0; i < subs.length; i++)
    {
      java.io.File subFile = null;
      java.io.BufferedReader inStream = null;
      try
      {
        subFile = getLoadableSubtitleFile(sourceFile, new java.io.File(subs[i].getPath()));
        subEntries = new java.util.Vector();
        subLangEntryMap.put(subLangs[i], subEntries);

        // Now read in the SUB file and fill up the subEntries Vector
        inStream = sage.IOUtils.openReaderDetectCharset(subFile, sage.Sage.I18N_CHARSET);
        String line = inStream.readLine();
        // IMPORTANT: There's a few different formats that can be in a .sub file; so check for which one it is
        // The 2 I've found are MicroDVD and SubViewer
        if (line.startsWith("{"))
        {
          // MicroDVD .sub format: http://en.wikipedia.org/wiki/MicroDVD
          // Format is {startFrame}{stopFrame}text
          // The first 'text' can be the frame rate as a float; if that's not there then we'll use the frame rate from the media format
          boolean firstLine = true;
          float fps = 30;
          if (mediaFormat.getVideoFormat().getFps() > 0)
            fps = mediaFormat.getVideoFormat().getFps();
          while (line != null)
          {
            line = line.trim();
            int idx1 = line.indexOf('}');
            int idx2 = line.indexOf('}', idx1 + 1);
            if (idx1 != -1 && idx2 != -1)
            {
              int f1 = -1;
              int f2 = -1;
              String str = null;
              try
              {
                f1 = Integer.parseInt(line.substring(1, idx1));
                f2 = Integer.parseInt(line.substring(idx1 + 2, idx2));
                str = line.substring(idx2 + 1);
              }
              catch (NumberFormatException nfe)
              {
                if (sage.Sage.DBG) System.out.println("ERROR parsing MicroDVD .sub format; non-number with frame for: " + line);
              }
              if (f1 >= 0 && f2 >= 0 && str != null)
              {
                boolean addMe = true;
                if (firstLine)
                {
                  // Try to get the frame rate
                  try
                  {
                    fps = Float.parseFloat(str);
                    addMe = false;
                  }
                  catch (NumberFormatException nfe)
                  {
                  }
                  if (sage.Sage.DBG) System.out.println("MicroDVD .sub format using fps=" + fps);
                }
                if (addMe)
                {
                  str = str.replace('|', '\n');
                  subEntries.add(new SubtitleEntry(str, Math.round(f1*1000L/fps), Math.round((f2 - f1)*1000L/fps)));
                  if (sage.Sage.DBG) System.out.println("Added new MicroDVD Sub entry: " + subEntries.lastElement());
                }
              }
            }
            else
            {
              if (sage.Sage.DBG) System.out.println("ERROR parsing MicroDVD .sub format with line: " + line);
            }
            line = inStream.readLine();
            firstLine = false;
          }
        }
        else if (line.startsWith("[")) // SubViewer format
        {
          // Skip all of the header contents
          while (line != null && line.startsWith("["))
            line = inStream.readLine();

          // We read a timecode and then captions until we hit a blank line; then we do it over again
          while (line != null)
          {
            // First is the in/out timecodes for this sub entry
            long inTime = 0;
            long duration = 0;
            // These are good to 'sync' on since they have a specific format to them
            do
            {
              if (line == null) break;
              try
              {
                long[] timeSpan = parseTimeRange(line);
                inTime = timeSpan[0];
                duration = timeSpan[1];
              }
              catch (Exception e)
              {
                if (sage.Sage.DBG) System.out.println("ERROR in SUB file; didn't find timecode on line; instead found: " + line + " error=" + e);
                line = inStream.readLine();
                continue;
              }
              break;
            }while(true);
            if (line == null) break;
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
                else if (!inTag && c == '[' && j < line.length() - 3 && line.indexOf("[br]", j) == j)
                {
                  // Skip the [br] and insert a newline
                  sb.append('\n');
                  j += 3;
                }
                else if (!inTag)
                  sb.append(c);
              }
              line = inStream.readLine();
            }
            if (sb.length() > 0)
            {
              // Add this new Subtitle Entry
              if (sage.Sage.DBG) System.out.println("Found new SUB subtitle entry in=" + inTime + " dur=" + duration + " text=" + sb);
              subEntries.add(new SubtitleEntry(sb.toString(), inTime, duration));
            }
            if (line != null) // it's the blank line
              line = inStream.readLine();

          }
        }
        else
        {
          if (sage.Sage.DBG) System.out.println("Invalid .sub file contents; don't understand header: " + line);
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
