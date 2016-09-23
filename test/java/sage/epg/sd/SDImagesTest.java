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
package sage.epg.sd;

import org.testng.annotations.Test;
import sage.epg.sd.json.images.SDImage;
import sage.epg.sd.json.images.SDProgramImages;
import sage.epg.sd.json.map.SDLineupMap;
import sage.epg.sd.json.map.station.SDLogo;
import sage.epg.sd.json.map.station.SDStation;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SDImagesTest extends DeserializeTest
{
  private void verifyImages(SDImage[] images, byte[][] packedImages, int showcardID, int expected)
  {
    assert packedImages.length == expected : "Expected " + expected + " indexes, got " + packedImages.length;
    Set<SDImage> foundImages = new HashSet<>();
    for (int i = 1; i < packedImages.length; i++)
    {
      assert packedImages[i] != null : "Index " + i + " was null!";
      boolean exists = false;
      String decoded = SDImages.decodeImageUrl(showcardID, packedImages[i]);
      for (SDImage image : images)
      {
        if (image.getUri().equals(decoded))
        {
          // Duplicate URLs are also not acceptable.
          if (!foundImages.add(image))
            continue;

          exists = true;
          break;
        }
      }

      assert exists : "The decoded URL '" + decoded + "' did not exist in the original image collection.";
    }

    for (int i = 0; i < packedImages[0].length; i++)
    {
      if (packedImages[0][i] == 0)
      {
        int count = SDImages.getImageCount(i, packedImages);
        assert count == 0 : "Expected count to be == 0, got " + count;
        continue;
      }

      int count = SDImages.getImageCount(i, packedImages);
      assert count > 0 : "Expected count to be > 0, got " + count;
      for (int j = 0; j < count; j++)
      {
        // Verify that every image type in the array is returnable.
        String url = SDImages.getImageUrl(showcardID, packedImages, j, i);
        assert url != null : "Expected a URL for index=" + j + " imageTypeIndex=" + i + ", got null.";
      }

      // Verify that if we request an invalid index for this type, it doesn't return anything.
      String url = SDImages.getImageUrl(showcardID, packedImages, count, i);
      assert url == null : "Expected null for index=" + count + " imageTypeIndex=" + i + ", got " + url;
    }
  }

  @Test(groups = {"gson", "schedulesDirect", "images", "series" })
  public void seriesEncodingTest() throws IOException
  {
    String programImages = "epg/sd/json/images/programImagesEpisode.json";
    SDProgramImages images[] = deserialize(programImages, SDProgramImages[].class);
    int[] showcardID = new int[1];
    byte[][] packedImages = SDImages.encodeImages(images[0].getImages(), showcardID, SDImages.ENCODE_NON_SERIES_ONLY);
    verifyImages(images[0].getImages(), packedImages, showcardID[0], 7);

    showcardID[0] = 0;
    packedImages = SDImages.encodeImages(images[0].getImages(), showcardID, SDImages.ENCODE_ALL);
    verifyImages(images[0].getImages(), packedImages, showcardID[0], 15);
  }

  @Test(groups = {"gson", "schedulesDirect", "images", "movie" })
  public void movieEncodingTest() throws IOException
  {
    String programImages = "epg/sd/json/images/programImagesMovie.json";
    SDProgramImages images[] = deserialize(programImages, SDProgramImages[].class);
    int[] showcardID = new int[1];
    byte[][] packedImages = SDImages.encodeImages(images[0].getImages(), showcardID, SDImages.ENCODE_ALL);
    verifyImages(images[0].getImages(), packedImages, showcardID[0], 22);
  }

  @Test(groups = {"gson", "schedulesDirect", "images", "channelLogo" })
  public void channelLogoEncodingTest() throws IOException
  {
    // Source: https://json.schedulesdirect.org/20141201/lineups/USA-PA67431-X
    String cable = "epg/sd/json/map/mapCable.json";
    SDLineupMap lineupMap = deserialize(cable, SDLineupMap.class);
    lineupMap.getStations()[0].getLogo().getURL();

    for (SDStation station : lineupMap.getStations())
    {
      SDLogo logo = station.getLogo();
      if (logo != null)
      {
        int logoID[] = new int[1];
        byte[] encodedLogo = SDImages.encodeLogoURL(logo.getURL(), logoID);
        String decodedLogo = SDImages.decodeLogoURL(encodedLogo, logoID[0]);
        assert logo.getURL().equals(decodedLogo);
      }
    }
  }
}
