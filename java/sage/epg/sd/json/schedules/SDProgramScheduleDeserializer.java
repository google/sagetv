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
import sage.epg.sd.json.programs.SDContentRating;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Map;

public class SDProgramScheduleDeserializer implements JsonDeserializer<SDProgramSchedule>
{
  @Override
  public SDProgramSchedule deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
  {
    if (!json.isJsonObject())
    {
      throw new JsonParseException("Schedule program must be an object.");
    }

    SDProgramSchedule newSchedule = new SDProgramSchedule();

    for (Map.Entry<String, JsonElement> program : ((JsonObject)json).entrySet())
    {
      JsonElement element = program.getValue();

      switch (program.getKey())
      {
        case "programID":
          newSchedule.programID = element.getAsString();
          break;
        case "airDateTime":
          newSchedule.airDateTime = element.getAsString();
          break;
        case "duration":
          newSchedule.duration = element.getAsInt();
          break;
        case "md5":
          newSchedule.md5 = element.getAsString();
          break;
        case "new":
          newSchedule.newShowing = element.getAsBoolean();
          break;
        case "cableInTheClassroom":
          newSchedule.cableInTheClassroom = element.getAsBoolean();
          break;
        case "catchup":
          newSchedule.catchup = element.getAsBoolean();
          break;
        case "continued":
          newSchedule.continued = element.getAsBoolean();
          break;
        case "educational":
          newSchedule.educational = element.getAsBoolean();
          break;
        case "joinedInProgress":
          newSchedule.joinedInProgress = element.getAsBoolean();
          break;
        case "leftInProgress":
          newSchedule.leftInProgress = element.getAsBoolean();
          break;
        case "premiere":
          newSchedule.premiere = element.getAsBoolean();
          break;
        case "programBreak":
          newSchedule.programBreak = element.getAsBoolean();
          break;
        case "repeat":
          newSchedule.repeat = element.getAsBoolean();
          break;
        case "signed":
          newSchedule.signed = element.getAsBoolean();
          break;
        case "subjectToBlackout":
          newSchedule.subjectToBlackout = element.getAsBoolean();
          break;
        case "timeApproximate":
          newSchedule.timeApproximate = element.getAsBoolean();
          break;
        case "free":
          newSchedule.free = element.getAsBoolean();
          break;
        case "liveTapeDelay":
          newSchedule.liveTapeDelay = element.getAsString();
          break;
        case "isPremiereOrFinale":
          newSchedule.isPremiereOrFinale = element.getAsString();
          break;
        case "ratings":
          newSchedule.ratings = context.deserialize(element, SDContentRating[].class);
          break;
        case "multipart":
          newSchedule.multipart = context.deserialize(element, SDMultiPart.class);
          break;
        case "audioProperties":
          newSchedule.audioProperties = context.deserialize(element, String[].class);
          break;
        case "videoProperties":
          if (element.isJsonArray())
          {
            newSchedule.videoProperties = context.deserialize(element, String[].class);
          }
          else
          {
            ArrayList<String> videoProperties = new ArrayList<>();
            // Work around for an issue in the Schedules Direct data feed that has been fixed, but
            // may linger for a while.
            for (Map.Entry<String, JsonElement> videoPropertiesEntry : ((JsonObject)element).entrySet())
            {
              videoProperties.add(videoPropertiesEntry.getValue().getAsString());
            }
            newSchedule.videoProperties = videoProperties.toArray(new String[videoProperties.size()]);
          }
          break;
      }
    }

    return newSchedule;
  }
}
