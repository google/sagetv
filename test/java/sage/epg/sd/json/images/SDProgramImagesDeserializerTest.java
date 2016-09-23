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

import org.testng.annotations.Test;
import sage.epg.sd.DeserializeTest;

import java.io.IOException;

public class SDProgramImagesDeserializerTest extends DeserializeTest
{
  @Test(groups = {"gson", "schedulesDirect", "program", "images", "error" })
  public void deserializeError() throws IOException
  {
    String error = "epg/sd/json/errors/6000.json";
    SDProgramImages program[] = deserialize(error, SDProgramImages[].class);
    assert program[0].getCode() == 6000;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "images", "error" })
  public void deserializeTMSError() throws IOException
  {
    //[{"programID":"SH02468914","data":{"errorCode":1013,"errorMessage":"invalid_tms_id"}}]
    String error = "epg/sd/json/errors/tms1013.json";
    SDProgramImages program[] = deserialize(error, SDProgramImages[].class);
    assert program[0].getCode() == 6000;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "images" })
  public void deserialize() throws IOException
  {
    String programImages = "epg/sd/json/images/programImagesShow.json";
    SDProgramImages images[] = deserialize(programImages, SDProgramImages[].class);

    assert images[0].getCode() == 0;
    assert images[0].getImages() != null;
    assert "SH01915214".equals(images[0].getProgramID());
    for (SDImage image : images[0].getImages())
    {
      assert image.getHeight() != 0;
      assert image.getWidth() != 0;
      assert image.getUri() != null;
      assert image.getCategory() != null;
    }
  }
}
