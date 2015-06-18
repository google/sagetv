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
public class SAMISubtitleHandler extends SubtitleHandler
{
  /** Creates a new instance of SAMISubtitleHandler */
  public SAMISubtitleHandler(sage.media.format.ContainerFormat inFormat)
  {
    super(inFormat);
  }

  public String getMediaFormat()
  {
    return sage.media.format.MediaFormat.SAMI;
  }

  public void loadSubtitlesFromFiles(sage.MediaFile sourceFile)
  {
    // Only one file for this format
    sage.media.format.SubpictureFormat[] subs = mediaFormat.getSubpictureFormats(sage.media.format.MediaFormat.SAMI);
    if (subs.length == 0)
      return;
    java.io.File subFile = null;
    java.io.BufferedReader inStream = null;
    try
    {
      subFile = getLoadableSubtitleFile(sourceFile, new java.io.File(subs[0].getPath()));
      inStream = sage.IOUtils.openReaderDetectCharset(subFile, sage.Sage.BYTE_CHARSET);
      // We do this one char at a time so we can analyze tags along the way easier
      int c = inStream.read();
      String lastTagName = null;
      java.util.Map lastAttributes = new java.util.HashMap();
      String currLanguage = null;
      long currStart = 0;
      StringBuffer tagName = new StringBuffer();
      StringBuffer tagAttribute = new StringBuffer();
      StringBuffer tagContent = new StringBuffer();
      boolean inTag = false;
      boolean inAttributeQuote = false;
      boolean tagNameComplete = false;
      java.util.Map classToLangMap = new java.util.HashMap();
      while (c != -1)
      {
        if (inTag)
        {
          if (c == '>')
          {
            if (!tagNameComplete)
              lastTagName = tagName.toString();
            else if (tagAttribute.length() > 0)
            {
              int idx = tagAttribute.indexOf("=");
              if (idx != -1)
              {
                lastAttributes.put(tagAttribute.substring(0, idx).toLowerCase(), tagAttribute.substring(idx + 1));
              }
            }
            // Finished reading a tag in; may want to do something here based on the tag
            inTag = false;
            if (lastTagName.equalsIgnoreCase("/style"))
            {
              if (sage.Sage.DBG) System.out.println("Found Style section in SAMI file: " + tagContent);
              subLangs = extractLanguagesFromStyleSection(tagContent.toString(), classToLangMap);
              if (sage.Sage.DBG) System.out.println("Built lang->class map of: " + classToLangMap);
            }
            else if (lastTagName.equalsIgnoreCase("/body"))
            {
              // We're done!
              break;
            }
            else if (lastTagName.equalsIgnoreCase("sync"))
            {
              // Before we start a new language; set the data for the old one
              processEntryData(tagContent, currStart, currLanguage);
              if (lastAttributes.containsKey("start"))
              {
                try
                {
                  currStart = Long.parseLong(lastAttributes.get("start").toString());
                }
                catch (Exception e)
                {
                  if (sage.Sage.DBG) System.out.println("ERROR parsing Start time in SAMI file:" + e);
                }
              }
              currLanguage = null;
            }
            else if (lastTagName.equalsIgnoreCase("p"))
            {
              // Before we start a new language; set the data for the old one
              processEntryData(tagContent, currStart, currLanguage);
              if (lastAttributes.containsKey("class"))
              {
                currLanguage = (String) classToLangMap.get(lastAttributes.get("class"));
              }
            }
            if (currLanguage == null)
              tagContent.setLength(0);
            else if (lastTagName.equalsIgnoreCase("br"))
              tagContent.append('\n');
            else
              tagContent.append(' ');
          }
          else if (Character.isWhitespace((char)c) && !inAttributeQuote)
          {
            if (!tagNameComplete)
            {
              tagNameComplete = true;
              String testStr = tagName.toString();
              // Comments shouldn't be treated as tags in the normal sense
              if (testStr.equals("!--"))
              {
                inTag = false;
                tagContent.setLength(0);
                tagContent.append('<');
                tagContent.append(testStr);
                tagContent.append((char) c);
              }
              else
                lastTagName = testStr;
              tagAttribute.setLength(0);
            }
            else if (tagAttribute.length() > 0)
            {
              int idx = tagAttribute.indexOf("=");
              if (idx != -1)
                lastAttributes.put(tagAttribute.substring(0, idx).toLowerCase(), tagAttribute.substring(idx + 1));
              else
                lastAttributes.put(tagAttribute.toString().toLowerCase(), "");
              tagAttribute.setLength(0);
            }
          }
          else if (!tagNameComplete)
            tagName.append((char) c);
          else
          {
            if (c == '"')
              inAttributeQuote = !inAttributeQuote;
            else
              tagAttribute.append((char) c);
          }
        }
        else if (c == '<')
        {
          inTag = true;
          tagNameComplete = false;
          tagName.setLength(0);
          tagAttribute.setLength(0);
          lastAttributes.clear();
        }
        else
          tagContent.append((char) c);
        c = inStream.read();
      }
      // Process the last one from the file
      processEntryData(tagContent, currStart, currLanguage);
    }
    catch (java.io.IOException e)
    {
      if (sage.Sage.DBG) System.out.println("ERROR loading subtitle file from " + subs[0] + " of " + e);
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

  private void processEntryData(StringBuffer tagContent, long startTime, String currLanguage)
  {
    if (currLanguage != null)
    {
      subEntries = (java.util.Vector) subLangEntryMap.get(currLanguage);
      if (subEntries == null)
        subLangEntryMap.put(currLanguage, subEntries = new java.util.Vector());
      SubtitleEntry newEntry = new SubtitleEntry(sage.media.rss.Translate.decode(tagContent).trim(), startTime, 0);
      insertSubtitleEntry(newEntry);
      if (sage.Sage.DBG) System.out.println("Inserted SAMI subtitle entry for lang=" + currLanguage + " " + newEntry);
      tagContent.setLength(0);
    }
  }

  public static String[] extractLanguagesFromStyleSection(String style)
  {
    return extractLanguagesFromStyleSection(style, null);
  }
  public static String[] extractLanguagesFromStyleSection(String style, java.util.Map classMap)
  {
    java.util.Vector rv = new java.util.Vector();
    // Remove the outer style tags and the HTML comment
    int idx = style.indexOf("<!--");
    if (idx != -1)
      style = style.substring(idx + 4).trim();
    idx = style.lastIndexOf("-->");
    if (idx != -1)
      style = style.substring(0, idx).trim();
    idx = 0;
    int lastEnd = -1;
    idx = style.indexOf('{');
    while (idx != -1)
    {
      String className = style.substring(lastEnd + 1, idx).trim();
      lastEnd = style.indexOf('}', idx);
      if (className.charAt(0) == '.')
      {
        // Found a language class
        int nameIdx = style.indexOf("Name:", idx);
        if (nameIdx != -1 && nameIdx < lastEnd)
        {
          int semiIdx = style.indexOf(';', nameIdx);
          if (semiIdx != -1 && semiIdx < lastEnd)
          {
            String currLang = style.substring(nameIdx + 5, semiIdx).trim();
            if (currLang.charAt(0) == '"')
              currLang = currLang.substring(1);
            if (currLang.charAt(currLang.length() - 1) == '"')
              currLang = currLang.substring(0, currLang.length() - 1);
            rv.add(currLang);
            if (classMap != null)
              classMap.put(className.substring(1), currLang);
          }
        }
      }
      idx = style.indexOf('{', lastEnd);
    }
    return (String[]) rv.toArray(sage.Pooler.EMPTY_STRING_ARRAY);
  }
}
