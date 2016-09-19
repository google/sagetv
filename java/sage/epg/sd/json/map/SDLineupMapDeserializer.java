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
package sage.epg.sd.json.map;

import sage.epg.sd.gson.JsonDeserializationContext;
import sage.epg.sd.gson.JsonDeserializer;
import sage.epg.sd.gson.JsonElement;
import sage.epg.sd.gson.JsonObject;
import sage.epg.sd.gson.JsonParseException;
import sage.epg.sd.SDUtils;
import sage.epg.sd.json.SDError;
import sage.epg.sd.json.map.channel.SDTransport;
import sage.epg.sd.json.map.station.SDStation;

import java.lang.reflect.Type;

public class SDLineupMapDeserializer implements JsonDeserializer<SDLineupMap>
{
  @Override
  public SDLineupMap deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
  {
    if (!json.isJsonObject())
    {
      throw new JsonParseException("Unexpected format. " + json.toString());
    }
    JsonObject object = json.getAsJsonObject();

    SDError error = SDUtils.getError(object);
    if (error != null)
      return new SDLineupMap(error);

    SDMapMetadata metadata = context.deserialize(object.get("metadata"), SDMapMetadata.class);
    if (metadata == null)
    {
      throw new JsonParseException("Metadata missing. " + object.toString());
    }

    SDStation stations[] = context.deserialize(object.get("stations"), SDStation[].class);
    if (stations == null)
    {
      throw new JsonParseException("Stations missing. " + object.toString());
    }

    SDTransport transport = SDTransport.getEnumType(metadata.getTransport());
    SDChannelMap maps[] = context.deserialize(object.get("map"), transport.CLASS);
    if (maps == null)
    {
      throw new JsonParseException("Maps missing. " + object.toString());
    }

    // Iterate over the entire map to convert all of the station ID strings into integers. We do
    // this here because this map will be traversed a lot, so we would either need to synchronize
    // the getter so we can cache the conversion or convert on every get both options will be slower
    // than just doing this once before the getters are accessible.
    for (SDChannelMap map : maps)
    {
      map.setStationId();
    }

    return new SDLineupMap(maps, stations, metadata, transport);
  }
}
