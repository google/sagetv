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

import java.io.IOException;

public class SDSeriesDescDeserializerTest extends DeserializeTest
{
  @Test(groups = {"gson", "schedulesDirect", "program", "description", "retry" })
  public void deserializeRetry() throws IOException
  {
    String retry = "epg/sd/json/errors/6001SeriesDesc.json";
    SDSeriesDesc seriesDescs[] = deserialize(retry, SDSeriesDescArray.class).getSeriesDescs();
    assert seriesDescs[0].getCode() == 6001;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "description", "error" })
  public void deserializeError() throws IOException
  {
    String error = "epg/sd/json/errors/6000SeriesDesc.json";
    SDSeriesDesc seriesDescs[] = deserialize(error, SDSeriesDescArray.class).getSeriesDescs();
    assert seriesDescs[0].getCode() == 6000;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "description" })
  public void deserialize() throws IOException
  {
    String descriptionJson = "epg/sd/json/programs/seriesDesc.json";
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
