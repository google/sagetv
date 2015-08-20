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
public class RSSMediaContent implements java.io.Serializable
{

  /** Creates a new instance of RSSMediaContent */
  public RSSMediaContent(String url, String type, String medium, String expression, String duration, String width, String height)
  {
    this.setUrl(url);
    this.setType(type);
    this.setMedium(medium);
    this.setExpression(expression);
    this.setDuration(duration);
    this.setWidth(width);
    this.setHeight(height);
  }

  private String url;
  private String type;
  private String medium;
  private String expression;
  private String duration;
  private String width;
  private String height;

  public String getUrl()
  {
    return url;
  }

  public void setUrl(String url)
  {
    this.url = url;
  }

  public String getType()
  {
    return type;
  }

  public void setType(String type)
  {
    this.type = type;
  }

  public String getMedium()
  {
    return medium;
  }

  public void setMedium(String medium)
  {
    this.medium = medium;
  }

  public String getExpression()
  {
    return expression;
  }

  public void setExpression(String expression)
  {
    this.expression = expression;
  }

  public String getDuration()
  {
    return duration;
  }

  public void setDuration(String duration)
  {
    this.duration = duration;
  }

  public String getWidth()
  {
    return width;
  }

  public void setWidth(String width)
  {
    this.width = width;
  }

  public String getHeight()
  {
    return height;
  }

  public void setHeight(String height)
  {
    this.height = height;
  }

}
