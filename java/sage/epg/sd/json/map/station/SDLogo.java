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
package sage.epg.sd.json.map.station;

public class SDLogo
{
  private String URL;
  private int height;
  private int width;
  private String md5;

  public String getURL()
  {
    return URL;
  }

  public int getHeight()
  {
    return height;
  }

  public int getWidth()
  {
    return width;
  }

  public String getMd5()
  {
    return md5;
  }

  @Override
  public String toString()
  {
    return "SDLogo{" +
        "URL='" + URL + '\'' +
        ", height=" + height +
        ", width=" + width +
        ", md5='" + md5 + '\'' +
        '}';
  }
}
