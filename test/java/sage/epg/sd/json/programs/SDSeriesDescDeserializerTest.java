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

import org.testng.annotations.Test;
import sage.epg.sd.DeserializeTest;

public class SDSeriesDescDeserializerTest extends DeserializeTest
{
  @Test(groups = {"gson", "schedulesDirect", "program", "description", "retry" })
  public void deserializeRetry()
  {
    String retry = "{\n" +
        "\"SH000000060000\": {\n" +
        "    \"response\": \"PROGRAMID_QUEUED\",\n" +
        "    \"code\": 6001,\n" +
        "    \"serverID\": \"20141201.1\",\n" +
        "    \"message\": \"Fetching programID:SH012423280000  Retry.\",\n" +
        "    \"datetime\": \"2014-11-14T19:15:54Z\"\n" +
        "}\n" +
        "}";

    SDSeriesDesc seriesDescs[] = deserialize(retry, SDSeriesDescArray.class).getSeriesDescs();
    assert seriesDescs[0].getCode() == 6001;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "description", "error" })
  public void deserializeError()
  {
    String error = "{\n" +
        "\"SH000000060000\": {\n" +
        "    \"response\": \"INVALID_PROGRAMID\",\n" +
        "    \"code\": 6000,\n" +
        "    \"serverID\": \"20140530.1\",\n" +
        "    \"message\": \"Invalid programID:ZZ01234567891234\",\n" +
        "    \"datetime\": \"2014-11-14T19:17:54Z\"\n" +
        "}\n" +
        "}";

    SDSeriesDesc seriesDescs[] = deserialize(error, SDSeriesDescArray.class).getSeriesDescs();
    assert seriesDescs[0].getCode() == 6000;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "description" })
  public void deserialize()
  {
    String descriptionJson = "{\n" +
        "    \"SH000000060000\": {\n" +
        "        \"code\": 0,\n" +
        "        \"description100\": \"A cafe owner attempts to repatriate\n" +
        "escapees during World War II.\",\n" +
        "        \"description1000\": \"A French cafe owner tries to ride out\n" +
        "World War II. Caught between the Gestapo and the Resistance and forced\n" +
        "into working with both, René also struggles to hide evidence of his\n" +
        "affairs with the waitresses from his wife, who, though she can't carry\n" +
        "a tune, frequently performs as a singer in the cafe.\"\n" +
        "    },\n" +
        "    \"SH000186930000\": {\n" +
        "        \"code\": 0,\n" +
        "        \"description100\": \"Homer and Marge Simpson raise Bart, Lisa\n" +
        "and baby Maggie.\",\n" +
        "        \"description1000\": \"This long-running animated comedy focuses\n" +
        "on the eponymous family in the town of Springfield in an unnamed U.S.\n" +
        "state. The head of the Simpson family, Homer, is not a typical family\n" +
        "man. A nuclear-plant employee, he does his best to lead his family but\n" +
        "often finds that they are leading him. The family includes loving,\n" +
        "blue-haired matriarch Marge, troublemaking son Bart, overachieving\n" +
        "daughter Lisa and baby Maggie. Other Springfield residents include the\n" +
        "family's religious neighbor, Ned Flanders, family physician Dr.\n" +
        "Hibbert, Moe the bartender and police chief Clancy Wiggum.\"\n" +
        "    }\n" +
        "}";

    SDSeriesDesc seriesDescs[] = deserialize(descriptionJson, SDSeriesDescArray.class).getSeriesDescs();

    for (SDSeriesDesc seriesDesc : seriesDescs)
    {
      assert seriesDesc.getProgramID() != null;
      assert seriesDesc.getDescription100() != null;
      assert seriesDesc.getDescription1000() != null;
      assert seriesDesc.getCode() == 0;
      assert seriesDesc.getMessage() == null;
    }
  }
}
