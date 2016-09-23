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

public class SDProgramMetadataDeserializerTest extends DeserializeTest
{
  @Test(groups = {"gson", "schedulesDirect", "program", "metadata" })
  public void deserialize() throws IOException
  {
    String metadataJson = "epg/sd/json/programs/programMetadata.json";
    SDProgramMetadata metadata = deserialize(metadataJson, SDProgramMetadata.class);
    assert metadata.getProvider() != null;
    assert metadata.getEpisode() != 0;
    assert metadata.getSeason() != 0;
    assert metadata.getTotalEpisodes() != 0;
    assert metadata.getTotalSeasons() != 0;
  }
}
