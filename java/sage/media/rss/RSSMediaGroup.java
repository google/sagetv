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
package sage.media.rss;

/**
 *
 * @author Narflex
 */
public class RSSMediaGroup extends RSSObject
{
  private java.util.Vector content;
  private String thumbURL;
  private String thumbWidth;
  private String thumbHeight;
  private String playerURL;

  /** Creates a new instance of RSSMediaGroup */
  public RSSMediaGroup()
  {
    content = new java.util.Vector();
  }

  public String toDebugString()
  {
    return "RSSMediaGroup contents=" + getContent();
  }

  public java.util.Vector getContent()
  {
    return content;
  }

  public void addContent(RSSMediaContent rmc)
  {
    content.add(rmc);
  }

  public String getThumbURL()
  {
    return thumbURL;
  }

  public void setThumbURL(String thumbURL)
  {
    // Convert any '&amp;' symbols in this string
    StringBuffer sb = null;
    int nextIdx = thumbURL.indexOf("&amp;");
    int lastIdx = 0;
    while (nextIdx != -1)
    {
      if (sb == null)
        sb = new StringBuffer();
      sb.append(thumbURL.substring(lastIdx, nextIdx + 1)); // includes the & from &amp;
      lastIdx = nextIdx + 5;
    }
    if (sb != null)
    {
      sb.append(thumbURL.substring(lastIdx, thumbURL.length()));
      this.thumbURL = sb.toString();
    }
    else
      this.thumbURL = thumbURL;
  }

  public String getThumbWidth()
  {
    return thumbWidth;
  }

  public void setThumbWidth(String thumbWidth)
  {
    this.thumbWidth = thumbWidth;
  }

  public String getThumbHeight()
  {
    return thumbHeight;
  }

  public void setThumbHeight(String thumbHeight)
  {
    this.thumbHeight = thumbHeight;
  }

  public String getPlayerURL()
  {
    return playerURL;
  }

  public void setPlayerURL(String playerURL)
  {
    this.playerURL = playerURL;
  }

}
