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
package sage.epg.sd.json.locale;

import sage.epg.sd.gson.JsonDeserializationContext;
import sage.epg.sd.gson.JsonDeserializer;
import sage.epg.sd.gson.JsonElement;
import sage.epg.sd.gson.JsonObject;
import sage.epg.sd.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.Map;

public class SDLanguageDeserializer implements JsonDeserializer<SDLanguage[]>
{
  @Override
  public SDLanguage[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
  {
    if (!json.isJsonObject())
    {
      throw new JsonParseException("Unexpected format. " + json.toString());
    }

    JsonObject jsonObject = json.getAsJsonObject();
    SDLanguage returnLanguages[] = new SDLanguage[jsonObject.size()];

    int i = 0;
    for (Map.Entry<String, JsonElement> kvp :  jsonObject.entrySet())
    {
      String digraph = kvp.getKey();
      String name = kvp.getValue().getAsString();
      returnLanguages[i++] = new SDLanguage(digraph, name);
    }

    return returnLanguages;
  }
}
