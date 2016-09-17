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
package sage.epg.sd.json.map.channel;

import sage.epg.sd.json.map.SDChannelMap;

public class SDAntennaChannelMap extends SDChannelMap
{
  private int uhfVhf;
  private int atscMajor;
  private int atscMinor;

  public int getUhfVhf()
  {
    return uhfVhf;
  }

  public int getAtscMajor()
  {
    return atscMajor;
  }

  public int getAtscMinor()
  {
    return atscMinor;
  }

  @Override
  public String getChannel()
  {
    // Sometimes a channel is still analog. If this happens, it doesn't make sense to return 0-0
    // since that can't possibly be a valid channel. Instead we just return the analog frequency.
    if (atscMajor == 0 && atscMinor == 0)
      return Integer.toString(uhfVhf);

    return atscMajor + "-" + atscMinor;
  }

  @Override
  public String getPhysicalChannel()
  {
    // Sometimes a channel is still analog. If this happens, it doesn't make sense to return XX-0-0
    // since that can't possibly be a valid channel, so we don't return a physical channel here.
    if (atscMajor == 0 && atscMinor == 0)
      return null;

    return uhfVhf + "-" + atscMajor + "-" + atscMinor;
  }

  @Override
  public String toString()
  {
    return "SDAntennaChannelMap{" +
        "uhfVhf=" + uhfVhf +
        ", atscMajor=" + atscMajor +
        ", atscMinor=" + atscMinor +
        "} " + super.toString();
  }
}
