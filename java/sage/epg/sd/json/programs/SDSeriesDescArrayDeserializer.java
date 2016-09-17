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
package sage.epg.sd.json.programs;

import sage.epg.sd.gson.JsonDeserializationContext;
import sage.epg.sd.gson.JsonDeserializer;
import sage.epg.sd.gson.JsonElement;
import sage.epg.sd.gson.JsonObject;
import sage.epg.sd.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SDSeriesDescArrayDeserializer implements JsonDeserializer<SDSeriesDescArray>
{
  @Override
  public SDSeriesDescArray deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
  {
    if (!json.isJsonObject())
    {
      throw new JsonParseException("Program description must be an object.");
    }

    List<SDSeriesDesc> returnValue = new ArrayList<SDSeriesDesc>();

    for (Map.Entry<String, JsonElement> programs : ((JsonObject)json).entrySet())
    {
      String programID = programs.getKey();
      JsonElement element = programs.getValue();

      if (!element.isJsonObject())
      {
        throw new JsonParseException("Program description data must be an object.");
      }

      int code = 0;
      String message = null;
      String description100 = null;
      String description1000 = null;

      for (Map.Entry<String, JsonElement> data : ((JsonObject)element).entrySet())
      {
        switch (data.getKey())
        {
          case "code":
            code = data.getValue().getAsInt();
            break;
          case "message":
            message = data.getValue().getAsString();
            break;
          case "description100":
            description100 = data.getValue().getAsString();
            break;
          case "description1000":
            description1000 = data.getValue().getAsString();
            break;
        }
      }

      // We don't iterate more than once.
      returnValue.add(new SDSeriesDesc(code, message, programID, description100, description1000));
    }

    int size = returnValue.size();
    if (size == 0) return new SDSeriesDescArray(SDSeriesDescArray.EMPTY_SERIES_DESC);
    return new SDSeriesDescArray(returnValue.toArray(new SDSeriesDesc[size]));
  }
}
