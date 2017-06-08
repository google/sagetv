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
package sage.epg.sd.json.images;

import sage.epg.sd.gson.JsonDeserializationContext;
import sage.epg.sd.gson.JsonDeserializer;
import sage.epg.sd.gson.JsonElement;
import sage.epg.sd.gson.JsonObject;
import sage.epg.sd.gson.JsonParseException;
import sage.epg.sd.SDUtils;
import sage.epg.sd.json.SDError;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SDProgramImagesDeserializer implements JsonDeserializer<SDProgramImages[]>
{
  @Override
  public SDProgramImages[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
  {
    if (!json.isJsonArray())
    {
      SDError error = SDUtils.getError((JsonObject) json);
      if (error != null)
        return new SDProgramImages[] { new SDProgramImages(error.getCode(), error.getMessage(), "0", new SDImage[0]) };
      else
        return new SDProgramImages[] { new SDProgramImages(6000, null, "0", new SDImage[0]) };
    }

    List<SDProgramImages> programImages = new ArrayList<SDProgramImages>();

    for (JsonElement entry : json.getAsJsonArray())
    {
      if (!entry.isJsonObject())
        continue;

      int code = 0;
      String message = null;
      String programID = null;
      SDImage data[] = null;

      for (Map.Entry<String, JsonElement> imageEntry : entry.getAsJsonObject().entrySet())
      {
        String key = imageEntry.getKey();
        JsonElement value = imageEntry.getValue();

        switch (key)
        {
          case "code":
            code = value.getAsInt();
            break;
          case "message":
            message = value.getAsString();
            break;
          case "programID":
            programID = value.getAsString();
            break;
          case "data":
            // There is an undocumented error that will return JSON that is not an array. We will just
            // return an empty array in this case.
            if (value.isJsonArray())
              data = context.deserialize(value, SDImage[].class);
            else
            {
              code = 6000;
              data = SDProgramImages.EMPTY_IMAGES;
            }
            break;
        }
      }

      programImages.add(new SDProgramImages(code, message, programID, data));
    }

    return programImages.toArray(new SDProgramImages[programImages.size()]);
  }
}
