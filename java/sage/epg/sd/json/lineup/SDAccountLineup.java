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
package sage.epg.sd.json.lineup;

/**
 * This class is used for lineups you already have added to your account.
 */
public class SDAccountLineup
{
  private String lineup;
  private String name;
  private String transport;
  private String location;
  private String uri;
  private boolean isDeleted; // If this is true, notify the user.

  public String getLineup()
  {
    return lineup;
  }

  public String getName()
  {
    return name;
  }

  public String getTransport()
  {
    return transport;
  }

  public String getLocation()
  {
    return location;
  }

  public String getUri()
  {
    return uri;
  }

  public boolean isDeleted()
  {
    return isDeleted;
  }

  @Override
  public String toString()
  {
    return "SDAccountLineup{" +
      "lineup='" + lineup + '\'' +
      ", name='" + name + '\'' +
      ", transport='" + transport + '\'' +
      ", location='" + location + '\'' +
      ", uri='" + uri + '\'' +
      ", isDeleted=" + isDeleted +
      '}';
  }
}
