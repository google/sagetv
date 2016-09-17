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

/**
 * This class is used for lineups being looked up to be added to your account.
 */
public class SDHeadendLineup
{
  private String name;
  private String lineup;
  private String uri;

  public String getName()
  {
    // There's a consistency problem whereby look ups show the name as Antenna, but the account name
    // is Local Over the Air Broadcast. We are normalizing to Local Over the Air Broadcast.
    if (name.equals("Antenna"))
      return "Local Over the Air Broadcast";

    return name;
  }

  public String getLineup()
  {
    return lineup;
  }

  public String getUri()
  {
    return uri;
  }

  @Override
  public String toString()
  {
    return "SDLookupLineup{" +
        "name='" + name + '\'' +
        ", lineup='" + lineup + '\'' +
        ", uri='" + uri + '\'' +
        '}';
  }
}
