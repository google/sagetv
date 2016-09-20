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
  public void seriesEncodingTest()
  {
    String programImages = "[\n" +
        "{\n" +
        "\"programID\": \"EP00688359\",\n" +
        "\"data\": [\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Peter Jacobson as Dr. Chris Taub\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n169539_cc_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Omar Epps as Dr. Eric  Foreman\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n72370_cc_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Peter Jacobson as Dr. Chris Taub\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n169539_cc_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Kal Penn as Dr. Lawrence Kutner\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n242224_cc_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Odette Annable as Dr. Jessica Adams\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n637331_cc_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Charlyne Yi as Dr. Chi Park\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n511642_cc_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Olivia Wilde as Thirteen/ Dr. Remy Hadley\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n314917_cc_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p3056194_e_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Episode\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p3056194_e_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Episode\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Hugh Laurie as Dr. Gregory House\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n87269_cc_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jennifer Morrison as Dr. Allison Cameron\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n169177_cc_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Lisa Edelstein as Dr. Lisa Cuddy\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n68332_cc_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Hugh Laurie as Dr. Gregory House\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n87269_cc_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Olivia Wilde as Thirteen/ Dr. Remy Hadley\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n314917_cc_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Odette Annable as Dr. Jessica Adams\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n637331_cc_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"267\",\n" +
        "\"height\": \"200\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jesse Spencer, Charlyne Yi, Omar Epps, Peter Jacobson, Odette Annable and Robert Sean Leonard (from left)\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_ce_h7_aa.jpg\",\n" +
        "\"category\": \"Cast Ensemble\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Kal Penn as Dr. Lawrence Kutner\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n242224_cc_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Lisa Edelstein as Dr. Lisa Cuddy\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n68332_cc_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Omar Epps as Dr. Eric  Foreman\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n72370_cc_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Kal Penn as Dr. Lawrence Kutner\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n242224_cc_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Hugh Laurie as Dr. Gregory House\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n87269_cc_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Robert Sean Leonard as Dr. James Wilson\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n55541_cc_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jesse Spencer as Dr. Robert Chase\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n202782_cc_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jennifer Morrison as Dr. Allison Cameron\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n169177_cc_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Kal Penn as Dr. Lawrence Kutner\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n242224_cc_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Lisa Edelstein as Dr. Lisa Cuddy\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n68332_cc_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jesse Spencer, Charlyne Yi, Omar Epps, Peter Jacobson, Odette Annable and Robert Sean Leonard (from left)\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_ce_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Cast Ensemble\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jesse Spencer as Dr. Robert Chase\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n202782_cc_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jesse Spencer, Charlyne Yi, Omar Epps, Peter Jacobson, Odette Annable and Robert Sean Leonard (from left)\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_ce_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Cast Ensemble\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jennifer Morrison as Dr. Allison Cameron\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n169177_cc_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Olivia Wilde as Thirteen/ Dr. Remy Hadley\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n314917_cc_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Charlyne Yi as Dr. Chi Park\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n511642_cc_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jennifer Morrison as Dr. Allison Cameron\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n169177_cc_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jesse Spencer, Charlyne Yi, Omar Epps, Peter Jacobson, Odette Annable and Robert Sean Leonard (from left)\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_ce_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Cast Ensemble\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Charlyne Yi as Dr. Chi Park\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n511642_cc_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Robert Sean Leonard as Dr. James Wilson\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n55541_cc_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Odette Annable as Dr. Jessica Adams\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n637331_cc_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Peter Jacobson as Dr. Chris Taub\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n169539_cc_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Charlyne Yi as Dr. Chi Park\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n511642_cc_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Omar Epps as Dr. Eric  Foreman\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n72370_cc_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p3056194_e_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Episode\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Robert Sean Leonard as Dr. James Wilson\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n55541_cc_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Lisa Edelstein as Dr. Lisa Cuddy\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n68332_cc_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Odette Annable as Dr. Jessica Adams\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n637331_cc_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jesse Spencer as Dr. Robert Chase\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n202782_cc_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Peter Jacobson as Dr. Chris Taub\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n169539_cc_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Robert Sean Leonard as Dr. James Wilson\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n55541_cc_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jesse Spencer as Dr. Robert Chase\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n202782_cc_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Hugh Laurie as Dr. Gregory House\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n87269_cc_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Jesse Spencer, Charlyne Yi, Omar Epps, Peter Jacobson, Odette Annable and Robert Sean Leonard (from left)\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p185044_ce_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Cast Ensemble\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Omar Epps as Dr. Eric  Foreman\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n72370_cc_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Olivia Wilde as Thirteen/ Dr. Remy Hadley\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p185044_n314917_cc_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Cast in Character\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p3056194_e_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Episode\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p185044_l_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Logo\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p185044_l_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Logo\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p185044_l_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Logo\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p185044_l_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Logo\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892174_b1t_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892174_b1t_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892174_b1t_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892174_b1t_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892174_b1t_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p7892174_b1t_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892174_b1t_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p7892174_b1t_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892174_b1t_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892174_b1t_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892174_b1t_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892174_b1t_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892174_b1t_v5_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892174_b1t_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892174_b1t_v7_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892174_b1t_v8_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892174_b1t_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892175_b1t_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892175_b1t_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892175_b1t_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892175_b1t_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892175_b1t_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892175_b1t_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892175_b1t_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892175_b1t_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892175_b1t_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892175_b1t_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892175_b1t_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892175_b1t_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892175_b1t_v5_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892175_b1t_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892175_b1t_v7_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892175_b1t_v8_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892175_b1t_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892176_b1t_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892176_b1t_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892176_b1t_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892176_b1t_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892176_b1t_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892176_b1t_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892176_b1t_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892176_b1t_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892176_b1t_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892176_b1t_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892176_b1t_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892176_b1t_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892176_b1t_v5_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892176_b1t_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892176_b1t_v7_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892176_b1t_v8_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892176_b1t_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892177_b1t_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892177_b1t_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892177_b1t_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892177_b1t_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892177_b1t_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892177_b1t_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892177_b1t_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892177_b1t_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892177_b1t_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892177_b1t_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892177_b1t_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892177_b1t_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892177_b1t_v5_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892177_b1t_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892177_b1t_v7_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892177_b1t_v8_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892177_b1t_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892178_b1t_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892178_b1t_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892178_b1t_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892178_b1t_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892178_b1t_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892178_b1t_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892178_b1t_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892178_b1t_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892178_b1t_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892178_b1t_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892178_b1t_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892178_b1t_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892178_b1t_v5_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892178_b1t_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892178_b1t_v7_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892178_b1t_v8_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892178_b1t_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892179_b1t_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892179_b1t_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892179_b1t_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892179_b1t_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892179_b1t_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892179_b1t_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892179_b1t_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892179_b1t_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892179_b1t_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892179_b1t_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892179_b1t_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892179_b1t_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p7892179_b1t_v5_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p7892179_b1t_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892179_b1t_v7_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892179_b1t_v8_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p7892179_b1t_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p8201297_b1t_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p8201297_b1t_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p8201297_b1t_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p8201297_b1t_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p8201297_b1t_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p8201297_b1t_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p8201297_b1t_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p8201297_b1t_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p8201297_b1t_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p8201297_b1t_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p8201297_b1t_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p8201297_b1t_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p8201297_b1t_v5_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p8201297_b1t_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p8201297_b1t_v7_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p8201297_b1t_v8_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p8201297_b1t_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p8729531_b1t_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p8729531_b1t_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p8729531_b1t_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p8729531_b1t_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p8729531_b1t_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p8729531_b1t_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p8729531_b1t_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p8729531_b1t_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p8729531_b1t_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p8729531_b1t_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p8729531_b1t_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p8729531_b1t_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p8729531_b1t_v5_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p8729531_b1t_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p8729531_b1t_v7_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p8729531_b1t_v8_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p8729531_b1t_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1T\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p185044_b_h10_af.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p185044_b_h9_ab.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p185044_b_v9_ab.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p185044_i_h10_ab.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p185044_i_h11_ab.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p185044_i_h9_ab.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p185044_i_v9_ab.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p185044_b_h11_af.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p185044_i_v7_ab.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p185044_b_v7_ab.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p185044_b_v4_ab.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p185044_i_v4_ab.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p185044_b_h5_ab.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p185044_i_h5_ab.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p185044_b_h14_af.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p185044_i_h14_ab.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p185044_i_v8_ab.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p185044_b_v8_ab.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p185044_b_v6_ab.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p185044_i_v6_ab.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p185044_b_v2_ab.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p185044_i_v2_ab.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p185044_i_h3_ab.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p185044_b_h3_ab.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p185044_b_h13_af.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p185044_i_h13_ab.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p185044_b_v5_ab.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p185044_i_v5_ab.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p185044_b_v3_ab.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p185044_i_v3_ab.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p185044_b_h6_ab.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p185044_i_h6_ab.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p185044_i_h12_ab.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p185044_b_h12_af.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Series\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p8729531_i_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p8729531_i_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p8729531_i_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p8729531_i_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p8729531_i_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p8729531_i_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p8729531_i_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p8729531_i_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p8729531_i_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p8729531_i_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p8729531_i_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p8729531_i_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p8729531_i_v5_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p8729531_i_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p8729531_i_v7_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p8729531_i_v8_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p8729531_i_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892174_b_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892174_b_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892174_b_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892174_b_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892175_b_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p7892178_b_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892178_b_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892174_b_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892175_b_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892175_b_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p7892175_b_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892175_b_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p7892178_b_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p7892178_b_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p7892178_b_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Banner-L1\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\",\n" +
        "\"tier\": \"Season\"\n" +
        "}\n" +
        "]\n" +
        "}\n" +
        "]";
    SDProgramImages images[] = deserialize(programImages, SDProgramImages[].class);
    int[] showcardID = new int[1];
    byte[][] packedImages = SDImages.encodeImages(images[0].getImages(), showcardID, SDImages.ENCODE_NON_SERIES_ONLY);
    verifyImages(images[0].getImages(), packedImages, showcardID[0], 7);

    showcardID[0] = 0;
    packedImages = SDImages.encodeImages(images[0].getImages(), showcardID, SDImages.ENCODE_ALL);
    verifyImages(images[0].getImages(), packedImages, showcardID[0], 15);
  }

  @Test(groups = {"gson", "schedulesDirect", "images", "movie" })
  public void movieEncodingTest()
  {
    String programImages = "[\n" +
        "{\n" +
        "\"programID\": \"MV00152818\",\n" +
        "\"data\": [\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bv.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p35193_i_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Téa Leoni as Deborah Clasky and Cloris Leachman as Evelyn Wright in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ca.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1080\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p35193_i_v9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"72\",\n" +
        "\"height\": \"108\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Cloris Leachman as Evelyn Wright in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cd_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"A scene from the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bu_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Morena and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bz_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Morena and Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bq.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"288\",\n" +
        "\"height\": \"432\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bh.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Téa Leoni as Deborah Clasky and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bf_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Spanglish (2004)\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p35193_d_v8_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Box Art\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Sarah Steele as Bernice Clasky and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bn.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Sarah Steele as Bernice Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bj_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Morena and Sarah Steele as Bernice Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bm_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bk.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cg.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Paz Vega as Flor Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cc_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"135\",\n" +
        "\"height\": \"180\",\n" +
        "\"uri\": \"assets/p35193_i_v2_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"288\",\n" +
        "\"height\": \"432\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Cloris Leachman as Evelyn Wright in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cd.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Morena, Téa Leoni as Deborah Clasky and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ba.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"288\",\n" +
        "\"height\": \"432\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Spanglish (2004)\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/39464_aa.jpg\",\n" +
        "\"category\": \"Poster Art\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1920\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p35193_i_h10_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Moreno and Shelbie Bruce as Cristina Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ck_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Téa Leoni as Deborah Clasky, Paz Vega as Flor Morena and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bo.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bs.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bs_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Adam Sandler as John Clasky, Shelbie Bruce as Cristina Moreno and Paz Vega as Flor Morena in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bt.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Morena, Téa Leoni as Deborah Clasky and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ba_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Adam Sandler as John Clasky, Shelbie Bruce as Cristina Moreno and Paz Vega as Flor Morena in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bt_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Morena and Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bq_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Adam Sandler as John Clasky and Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_br.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Adam Sandler as John Clasky and Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_br_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Téa Leoni as Deborah Clasky, Paz Vega as Flor Morena and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bo_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"288\",\n" +
        "\"height\": \"432\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Paz Vega as Flor Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bp.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"72\",\n" +
        "\"height\": \"108\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Paz Vega as Flor Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bp_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Paz Vega as Flor Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cc.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Téa Leoni as Deborah Clasky and Cloris Leachman as Evelyn Wright in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ca_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Cloris Leachman as Evelyn Wright in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cb_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Cloris Leachman as Evelyn Wright in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cb.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"72\",\n" +
        "\"height\": \"108\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Adam Sandler as John Clasky and Paz Vega as Flor Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_by_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Téa Leoni as Deborah Clasky and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bb.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"288\",\n" +
        "\"height\": \"432\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Adam Sandler as John Clasky and Paz Vega as Flor Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_by.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Téa Leoni as Deborah Clasky and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bb_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Morena and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bz.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bw_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bw.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Adam Sandler as John Clasky and Paz Vega as Flor Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bx_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Adam Sandler as John Clasky and Paz Vega as Flor Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bx.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Shelbie Bruce as Cristina Moreno and Paz Vega as Flor Morena in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cj_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Moreno and Shelbie Bruce as Cristina Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ck.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ci_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Shelbie Bruce as Cristina Moreno and Paz Vega as Flor Morena in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cj.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ch_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ci.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cg_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ch.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"72\",\n" +
        "\"height\": \"108\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Shelbie Bruce as Cristina Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cf_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Cloris Leachman as Evelyn Wright and Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ce_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"288\",\n" +
        "\"height\": \"432\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Shelbie Bruce as Cristina Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_cf.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Cloris Leachman as Evelyn Wright and Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_ce.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"360\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p35193_i_h3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"720\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"assets/p35193_i_h6_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"180\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p35193_i_h5_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"540\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p35193_i_v4_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"1440\",\n" +
        "\"uri\": \"assets/p35193_i_v8_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p35193_i_v7_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"72\",\n" +
        "\"height\": \"108\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Spanglish (2004)\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/39464_aa_t.jpg\",\n" +
        "\"category\": \"Poster Art\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"270\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"assets/p35193_i_v3_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"3x4\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"288\",\n" +
        "\"height\": \"432\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Paz Vega as Flor Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bd.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"960\",\n" +
        "\"height\": \"540\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p35193_i_h12_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"72\",\n" +
        "\"height\": \"108\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Paz Vega as Flor Moreno in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bd_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(Top to Bottom) Cloris Leachman as Evelyn Wright and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bc.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"135\",\n" +
        "\"uri\": \"assets/p35193_i_h14_aa.jpg\",\n" +
        "\"size\": \"Xs\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(Top to Bottom) Cloris Leachman as Evelyn Wright and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bc_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Téa Leoni as Deborah Clasky and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bf.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Spanglish (2004)\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p35193_d_v5_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Box Art\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"270\",\n" +
        "\"uri\": \"assets/p35193_i_h13_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"120\",\n" +
        "\"height\": \"180\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Spanglish (2004)\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"assets/p35193_d_v6_aa.jpg\",\n" +
        "\"size\": \"Sm\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Box Art\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"480\",\n" +
        "\"height\": \"720\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Spanglish (2004)\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p35193_d_v7_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Box Art\",\n" +
        "\"text\": \"yes\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1280\",\n" +
        "\"height\": \"720\",\n" +
        "\"uri\": \"assets/p35193_i_h11_aa.jpg\",\n" +
        "\"size\": \"Lg\",\n" +
        "\"aspect\": \"16x9\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"72\",\n" +
        "\"height\": \"108\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bl_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"288\",\n" +
        "\"height\": \"432\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bl.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bk_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Sarah Steele as Bernice Clasky and Adam Sandler as John Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bn_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Morena and Sarah Steele as Bernice Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bm.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"72\",\n" +
        "\"height\": \"108\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bh_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bg_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"240\",\n" +
        "\"height\": \"360\",\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/p35193_i_v5_aa.jpg\",\n" +
        "\"size\": \"Md\",\n" +
        "\"aspect\": \"2x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bg.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"Sarah Steele as Bernice Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bj.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"1440\",\n" +
        "\"height\": \"1080\",\n" +
        "\"uri\": \"assets/p35193_i_h9_aa.jpg\",\n" +
        "\"size\": \"Ms\",\n" +
        "\"aspect\": \"4x3\",\n" +
        "\"category\": \"Iconic\",\n" +
        "\"text\": \"no\",\n" +
        "\"primary\": \"true\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Morena and Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bi_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"(L-R) Paz Vega as Flor Morena and Téa Leoni as Deborah Clasky in \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bi.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"432\",\n" +
        "\"height\": \"288\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"A scene from the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bu.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "},\n" +
        "{\n" +
        "\"width\": \"108\",\n" +
        "\"height\": \"72\",\n" +
        "\"caption\": {\n" +
        "\"content\": \"On the set of the film \\\"Spanglish.\\\"\",\n" +
        "\"lang\": \"en\"\n" +
        "},\n" +
        "\"uri\": \"https://s3.amazonaws.com/schedulesdirect/assets/39464_bv_t.jpg\",\n" +
        "\"category\": \"Scene Still\",\n" +
        "\"text\": \"no\"\n" +
        "}\n" +
        "]\n" +
        "}\n" +
        "]";
    SDProgramImages images[] = deserialize(programImages, SDProgramImages[].class);
    int[] showcardID = new int[1];
    byte[][] packedImages = SDImages.encodeImages(images[0].getImages(), showcardID, SDImages.ENCODE_ALL);
    verifyImages(images[0].getImages(), packedImages, showcardID[0], 22);
  }

  @Test(groups = {"gson", "schedulesDirect", "images", "channelLogo" })
  public void channelLogoEncodingTest()
  {
    // Source: https://json.schedulesdirect.org/20141201/lineups/USA-PA67431-X
    String cable = "{" +
        "\"map\": [" +
        "{" +
        "\"stationID\": \"11523\"," +
        "\"channel\": \"002\"" +
        "}," +
        "{" +
        "\"stationID\": \"11559\"," +
        "\"channel\": \"003\"" +
        "}," +
        "{" +
        "\"stationID\": \"11782\"," +
        "\"channel\": \"004\"" +
        "}" +
        "]," +
        "\"stations\": [" +
        "{" +
        "\"stationID\": \"11523\"," +
        "\"name\": \"WHP\"," +
        "\"callsign\": \"WHP\"," +
        "\"affiliate\": \"CBS\"," +
        "\"broadcastLanguage\": [" +
        "\"en\"" +
        "]," +
        "\"descriptionLanguage\": [" +
        "\"en\"" +
        "]," +
        "\"broadcaster\": {" +
        "\"city\": \"Harrisburg\"," +
        "\"state\": \"PA\"," +
        "\"postalcode\": \"17110\"," +
        "\"country\": \"United States\"" +
        "}," +
        "\"logo\": {" +
        "\"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s10098_h3_aa.png\"," +
        "\"height\": 270," +
        "\"width\": 360," +
        "\"md5\": \"c6fcd923a007fd4db485e0\"" +
        "}" +
        "}," +
        "{" +
        "\"stationID\": \"11559\"," +
        "\"name\": \"WITF\"," +
        "\"callsign\": \"WITF\"," +
        "\"affiliate\": \"PBS\"," +
        "\"broadcastLanguage\": [" +
        "\"en\"" +
        "]," +
        "\"descriptionLanguage\": [" +
        "\"en\"" +
        "]," +
        "\"broadcaster\": {" +
        "\"city\": \"Harrisburg\"," +
        "\"state\": \"PA\"," +
        "\"postalcode\": \"17111\"," +
        "\"country\": \"United States\"" +
        "}," +
        "\"isCommercialFree\": true," +
        "\"logo\": {" +
        "\"URL\": \"https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/s11039_h3_aa.png\"," +
        "\"height\": 270," +
        "\"width\": 360," +
        "\"md5\": \"594f1251ccd7e9f21b6856\"" +
        "}" +
        "}," +
        "{" +
        "\"stationID\": \"11782\"," +
        "\"name\": \"WPMT\"," +
        "\"callsign\": \"WPMT\"," +
        "\"affiliate\": \"FOX\"," +
        "\"broadcastLanguage\": [" +
        "\"en\"" +
        "]," +
        "\"descriptionLanguage\": [" +
        "\"en\"" +
        "]," +
        "\"broadcaster\": {" +
        "\"city\": \"York\"," +
        "\"state\": \"PA\"," +
        "\"postalcode\": \"17403\"," +
        "\"country\": \"United States\"" +
        "}," +
        "\"logo\": {" +
        "\"URL\": \"https://s3.amazonaw.com/schedulesdirect/assets/stationLogos/s10212_h3_aa.png\"," +
        "\"height\": 270," +
        "\"width\": 360," +
        "\"md5\": \"4c0a04c47337a16a8944a5\"" +
        "}" +
        "}" +
        "]," +
        "\"metadata\": {" +
        "\"lineup\": \"USA-PA67431-X\"," +
        "\"modified\": \"2016-07-30T05:26:44Z\"," +
        "\"transport\": \"Cable\"" +
        "}" +
        "}";

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
