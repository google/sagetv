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
package sage.media.format;

/**
 *
 * @author Narflex
 */
public class SubpictureFormat extends BitstreamFormat
{
  public String toString()
  {
    return "Subpic[" + formatName + (id != null ? (" id=" + id) : "") + (path != null ? (" path=" + path) : "")
                + (language == null ? "]" : (" " + language + "]")) + " forced=" + forced;
  }

  public String getLanguage()
  {
    return language;
  }

  public void setLanguage(String s)
  {
    language = s != null ? s.intern() : null;
  }
  
  public void setForced(boolean value)
  {
    this.forced = value;
  }
    
  public boolean getForced()
  {
    return forced;
  }

  public String getPath()
  {
    return path;
  }

  public void setPath(String s)
  {
    path = s;
  }

  public String getFullPropertyString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("bf=sub;");
    sb.append(super.getFullPropertyString());
    if (language != null && language.length() > 0)
    {
      sb.append("lang=");
      sb.append(escapeString(language));
      sb.append(';');
    }
    if (path != null && path.length() > 0)
    {
      sb.append("path=");
      sb.append(escapeString(path));
      sb.append(';');
    }
    if (compositionPageId != null && compositionPageId.length() > 0)
    {
      sb.append("cpgid=");
      sb.append(compositionPageId);
      sb.append(';');
    }
    if (ancillaryPageId != null && ancillaryPageId.length() > 0)
    {
      sb.append("apgid=");
      sb.append(ancillaryPageId);
      sb.append(';');
    }
    if(this.forced == true)
    {
      sb.append("forced=");
      sb.append("true");
      sb.append(';');
    }
    else
    {
      sb.append("forced=");
      sb.append("false");
      sb.append(';');
    }
    return sb.toString();
  }

  public static SubpictureFormat buildSubFormatFromProperty(String str)
  {
    SubpictureFormat rv = new SubpictureFormat();
    int currNameStart = 0;
    int currValueStart = -1;
    for (int i = 0; i < str.length(); i++)
    {
      char c = str.charAt(i);
      if (c == '\\')
      {
        // Escaped character, so skip the next one
        i++;
        continue;
      }
      else if (c == '=')
      {
        // We found the name=value delimeter, set the value start position
        currValueStart = i + 1;
      }
      else if (c == ';')
      {
        // We're at the end of the name value pair, get their values!
        String name = str.substring(currNameStart, currValueStart - 1);
        String value = str.substring(currValueStart, i);
        currNameStart = i + 1;
        currValueStart = -1;
        if ("f".equals(name))
          rv.setFormatName(unescapeString(value));
        else if ("lang".equals(name))
          rv.language = unescapeString(value).intern();
        else if ("main".equals(name))
          rv.primary = "yes".equalsIgnoreCase(value);
        else if ("index".equals(name))
          rv.orderIndex = Integer.parseInt(value);
        else if ("tag".equals(name))
          rv.id = value.intern();
        else if ("path".equals(name))
          rv.path = unescapeString(value);
        else if ("cpgid".equals(name))
          rv.compositionPageId = value.intern();
        else if ("apgid".equals(name))
          rv.ancillaryPageId = value.intern();
        else if("forced".equals(name))
          rv.forced = Boolean.parseBoolean(value);      
      }
    }
    return rv;
  }

  public String getPrettyDesc()
  {
    return "Subpic" + ((language != null && language.length() > 0) ? (":" + language) : "") +
        ((formatName != null && formatName.length() > 0) ? (" " + formatName) : "");
  }

  protected String language;
  // Path to an external subtitle file
  protected String path;
  protected String compositionPageId; // for dvb subpicture
  protected String ancillaryPageId; // for dvb subpicture
  protected boolean forced = false; //If the track is forced
}
