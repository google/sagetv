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
public class SSASubtitleHandler extends SubtitleHandler
{
  /** Creates a new instance of SUBSubtitleHandler */
  public SSASubtitleHandler(sage.media.format.ContainerFormat inFormat)
  {
    super(inFormat);
  }

  public SSASubtitleHandler(String configInfo)
  {
    super(null);
    processScriptInfo(configInfo);
  }

  public String getMediaFormat()
  {
    return sage.media.format.MediaFormat.SSA;
  }

  public void processScriptInfo(String scriptInfo)
  {
    // TODO: Get the real format information once we do stylized text
    if (sage.Sage.DBG) System.out.println("SSA handler got config data of:" + scriptInfo);
  }

  public void loadSubtitlesFromFiles(sage.MediaFile sourceFile)
  {
    // If there's multiple subtitle tracks; then there has to be multiple SSA files
    sage.media.format.SubpictureFormat[] subs = mediaFormat.getSubpictureFormats(sage.media.format.MediaFormat.SSA);
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

        // Now read in the SSA file and fill up the subEntries Vector
        inStream = sage.IOUtils.openReaderDetectCharset(subFile, sage.Sage.I18N_CHARSET);
        String line = inStream.readLine();
        StringBuffer configInfo = new StringBuffer();
        // Read everything up to [Events] and process that config data
        while (line != null && !line.equalsIgnoreCase("[Events]"))
        {
          line = line.trim();
          configInfo.append(line);
          configInfo.append('\n');
          line = inStream.readLine();
        }
        processScriptInfo(configInfo.toString());
        line = inStream.readLine();
        while (line != null)
        {
          // Process the Events in the file
          if (line.startsWith("Dialogue:"))
          {
            SubtitleEntry newEntry = createEntryForDialogue(line);
            if (newEntry != null)
              insertSubtitleEntry(newEntry);
          }
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

  protected boolean insertEntryForPostedInfo(long time, long dur, byte[] rawData)
  {
    String rawText;
    try
    {
      rawText = new String(rawData, 0, (rawData.length > 0 && rawData[rawData.length - 1] == 0) ? (rawData.length - 1) : rawData.length, sage.Sage.I18N_CHARSET);
    }
    catch (java.io.UnsupportedEncodingException uee)
    {
      rawText = new String(rawData, 0, (rawData.length > 0 && rawData[rawData.length - 1] == 0) ? (rawData.length - 1) : rawData.length);
    }
    boolean rv = false;
    int idx = rawText.indexOf("Dialogue:");
    if (idx != -1)
    {
      java.util.StringTokenizer toker = new java.util.StringTokenizer(rawText.substring(idx), "\r\n");
      while (toker.hasMoreTokens())
      {
        String currLine = toker.nextToken();
        idx = currLine.indexOf("Dialogue:");
        if (idx != -1)
        {
          SubtitleEntry newEntry = createEntryForDialogue(currLine.substring(idx));
          if (newEntry != null)
          {
            if (newEntry.start <= 0)
              newEntry.start = time;
            if (newEntry.duration <= 0)
              newEntry.duration = dur;
            rv |= insertSubtitleEntry(newEntry);
          }
        }
      }
    }
    return rv;
  }

  private SubtitleEntry createEntryForDialogue(String line)
  {
    // Dialogue event; create a subtitle entry for it
    line = line.substring("Dialogue:".length()).trim();
    // First one is layer for ASS and marked for SSA
    int idx0 = line.indexOf(',');
    // Next two are start-stop time so parse this as a group
    int idx1 = line.indexOf(',', idx0 + 1);
    idx1 = line.indexOf(',', idx1 + 1);
    try
    {
      long[] times = parseTimeRange(line.substring(idx0 + 1, idx1));
      // Next is the style
      idx0 = idx1;
      idx1 = line.indexOf(',', idx0 + 1);
      // Next is the name of the speaker
      idx0 = idx1;
      idx1 = line.indexOf(',', idx0 + 1);
      // Next is the MarginL
      idx0 = idx1;
      idx1 = line.indexOf(',', idx0 + 1);
      // Next is the MarginR
      idx0 = idx1;
      idx1 = line.indexOf(',', idx0 + 1);
      // Next is the MarginV
      idx0 = idx1;
      idx1 = line.indexOf(',', idx0 + 1);
      // Next is the Effect
      idx0 = idx1;
      idx1 = line.indexOf(',', idx0 + 1);
      if (idx1 != -1)
      {
        String text = line.substring(idx1 + 1);
        // Now we have the text; clean it up!
        boolean inTag = false;
        StringBuffer sb = new StringBuffer();
        for (int j = 0;  j < text.length(); j++)
        {
          char c = text.charAt(j);
          if (!inTag && c == '{')
            inTag = true;
          else if (inTag && c == '}')
            inTag = false;
          else if (!inTag && c == '\\' && j < text.length() - 1 && (text.charAt(j + 1) == 'n' || text.charAt(j + 1) == 'N'))
          {
            // Skip the \n or \N and insert a newline
            sb.append('\n');
            j ++;
          }
          else if (!inTag)
            sb.append(c);
        }
        SubtitleEntry newEntry = new SubtitleEntry(sb.toString(), times[0], times[1]);
        if (sage.Sage.DBG) System.out.println("Created new SSA sub entry: " + newEntry);
        return newEntry;
      }
      else
      {
        if (sage.Sage.DBG) System.out.println("Invalid entry for SSA dialoge line, not enough tokens:" + line);
      }
    }
    catch (Exception e)
    {
      if (sage.Sage.DBG) System.out.println("Invalid timing entry for SSA dialoge line:" + line + " err=" + e);
    }
    return null;
  }
}
