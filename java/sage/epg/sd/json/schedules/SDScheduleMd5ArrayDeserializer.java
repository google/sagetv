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
package sage.epg.sd.json.schedules;

import sage.epg.sd.gson.JsonDeserializationContext;
import sage.epg.sd.gson.JsonDeserializer;
import sage.epg.sd.gson.JsonElement;
import sage.epg.sd.gson.JsonObject;
import sage.epg.sd.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SDScheduleMd5ArrayDeserializer implements JsonDeserializer<SDScheduleMd5Array>
{
  @Override
  public SDScheduleMd5Array deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
  {
    if (!json.isJsonObject())
    {
      throw new JsonParseException("Schedule must be an object.");
    }

    List<SDScheduleMd5> md5List = new ArrayList<SDScheduleMd5>();

    String stationID = null;
    // 22 is probably the largest this array will typically be.
    List<SDScheduleMd5.Md5> dates = new ArrayList<SDScheduleMd5.Md5>(22);

    for (Map.Entry<String, JsonElement> station : ((JsonObject)json).entrySet())
    {
      stationID = station.getKey();

      if (!station.getValue().isJsonObject())
      {
        // Schedules Direct will sometimes provide an empty array. This is undocumented, but
        // presumably it would mean the station doesn't have any hashes or is about to be deleted.
        if (station.getValue().isJsonArray())
          continue;

        throw new JsonParseException("Schedule date/md5 must be an object.");
      }

      for (Map.Entry<String, JsonElement> date : ((JsonObject) station.getValue()).entrySet())
      {
        String newDate = date.getKey();
        if (!date.getValue().isJsonObject())
        {
          throw new JsonParseException("Schedule date and md5 must be an object.");
        }
        String newMd5 = ((JsonObject) date.getValue()).get("md5").getAsString();
        dates.add(new SDScheduleMd5.Md5(newDate, newMd5));
      }

      int size = dates.size();
      if (size == 0)
        md5List.add(new SDScheduleMd5(stationID, SDScheduleMd5.EMPTY_MD5S));
      else
        md5List.add(new SDScheduleMd5(stationID, dates.toArray(new SDScheduleMd5.Md5[size])));
      dates.clear();
    }
    int size = md5List.size();
    if (size == 0) return new SDScheduleMd5Array(SDScheduleMd5Array.EMPTY_MD5_SCHEDULES);
    return new SDScheduleMd5Array(md5List.toArray(new SDScheduleMd5[size]));
  }
}
