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
package sage.epg.sd.json.headend;

import java.util.Arrays;

public class SDHeadend
{
  private static final SDHeadendLineup[] EMPTY_LINEUPS = new SDHeadendLineup[0];

  private String headend;
  private String transport;
  private String location;
  private SDHeadendLineup lineups[];

  public String getHeadend()
  {
    return headend;
  }

  public String getTransport()
  {
    return transport;
  }

  public String getLocation()
  {
    return location;
  }

  public SDHeadendLineup[] getLineups()
  {
    if (lineups == null)
      return EMPTY_LINEUPS;

    return lineups;
  }

  @Override
  public String toString()
  {
    return "SDLookupHeadend{" +
        "headend='" + headend + '\'' +
        ", transport='" + transport + '\'' +
        ", location='" + location + '\'' +
        ", lineups=" + Arrays.toString(lineups) +
        '}';
  }
}
