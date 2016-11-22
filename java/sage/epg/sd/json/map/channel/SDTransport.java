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

public enum SDTransport
{
  ANTENNA("Antenna", SDAntennaChannelMap[].class),
  DVBC("DVB-C", SDAdvancedChannelMap[].class),
  DVBS("DVB-S", SDAdvancedChannelMap[].class),
  DVBT("DVB-T", SDAdvancedChannelMap[].class),
  CABLE("Cable", SDBasicChannelMap[].class),
  SATELLITE("Satellite", SDBasicChannelMap[].class),
  // JS 7/31/2016:  I don't know if we should have this feature. The only live example I could find
  // didn't match the example in the most recent guide and that has me concerned that this would be
  // a hard to maintain feature.
  //QAM("Cable", QAMChannelMap.class), // You must check the modulation field to see if this it QAM or not.
  UNKNOWN("Unknown", SDBasicChannelMap[].class); // This should never happen.

  public final String NAME;
  public final Class CLASS;

  SDTransport(String name, Class classType) {
    NAME = name;
    CLASS = classType;
  }

  public static SDTransport getEnumType(String transport)
  {
    for(SDTransport transportEnum : SDTransport.values())
    {
      if (transportEnum.NAME.equals(transport))
      {
        /*if (transport.equals("Cable"))
        {
          if (modulation == null || modulation.equals(""))
            return CABLE;

          // If the modulation field is defined, it is always QAM.
          return QAM;
        }*/

        return transportEnum;
      }
    }

    return UNKNOWN;
  }

  @Override
  public String toString()
  {
    return "SDTransport{" +
        "NAME='" + NAME + '\'' +
        ", CLASS=" + CLASS +
        '}';
  }
}
