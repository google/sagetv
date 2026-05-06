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

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import sage.TestUtils;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SDRipperSkipCacheTest
{
  @BeforeClass
  public void setUpSage() throws Throwable
  {
    TestUtils.initializeSageTVForTesting();
  }

  @Test(groups = {"schedulesDirect", "cache", "imageSkip"})
  public void loadImageSkipSetReturnsEmptyWhenFileDoesNotExist() throws Exception
  {
    SDRipper ripper = new SDRipper(1);
    Path tempDir = Files.createTempDirectory("sd-skip-cache-missing-");
    Path cachePath = tempDir.resolve("missing.cache");

    @SuppressWarnings("unchecked")
    Set<String> loaded = (Set<String>) invokeLoadImageSkipSet(ripper, cachePath.toString());

    Assert.assertNotNull(loaded, "Expected a non-null Set for missing cache file.");
    Assert.assertTrue(loaded.isEmpty(), "Expected an empty Set for missing cache file.");

    Files.deleteIfExists(cachePath);
    Files.deleteIfExists(tempDir);
  }

  @Test(groups = {"schedulesDirect", "cache", "imageSkip"})
  public void saveAndLoadImageSkipSetRoundTrip() throws Exception
  {
    SDRipper ripper = new SDRipper(1);
    Path tempDir = Files.createTempDirectory("sd-skip-cache-roundtrip-");
    Path cachePath = tempDir.resolve("program.cache");

    Set<String> expected = new HashSet<>(Arrays.asList("EP000000000001", "SH000000000002", "  SH000000000003  "));
    invokeSaveImageSkipSet(ripper, cachePath.toString(), expected);

    @SuppressWarnings("unchecked")
    Set<String> loaded = (Set<String>) invokeLoadImageSkipSet(ripper, cachePath.toString());

    Assert.assertEquals(loaded.size(), 3, "Expected all non-empty values to be persisted.");
    Assert.assertTrue(loaded.contains("EP000000000001"), "Program id should be present.");
    Assert.assertTrue(loaded.contains("SH000000000002"), "Series id should be present.");
    Assert.assertTrue(loaded.contains("SH000000000003"), "Trimmed values should be persisted without surrounding whitespace.");

    Files.deleteIfExists(cachePath);
    Files.deleteIfExists(tempDir);
  }

  @Test(groups = {"schedulesDirect", "cache", "imageSkip"})
  public void saveImageSkipSetSkipsNullAndBlankValues() throws Exception
  {
    SDRipper ripper = new SDRipper(1);
    Path tempDir = Files.createTempDirectory("sd-skip-cache-filter-");
    Path cachePath = tempDir.resolve("person.cache");

    Set<String> source = new HashSet<>(Arrays.asList("", "  ", "PR1234567890", null));
    invokeSaveImageSkipSet(ripper, cachePath.toString(), source);

    @SuppressWarnings("unchecked")
    Set<String> loaded = (Set<String>) invokeLoadImageSkipSet(ripper, cachePath.toString());

    Assert.assertEquals(loaded.size(), 1, "Only one valid value should be persisted.");
    Assert.assertTrue(loaded.contains("PR1234567890"), "The valid person id should be persisted.");

    Files.deleteIfExists(cachePath);
    Files.deleteIfExists(tempDir);
  }

  @Test(groups = {"schedulesDirect", "cache", "imageSkip"})
  public void loadImageSkipSetCollapsesDuplicateLines() throws Exception
  {
    SDRipper ripper = new SDRipper(1);
    Path tempDir = Files.createTempDirectory("sd-skip-cache-dupes-");
    Path cachePath = tempDir.resolve("dupes.cache");

    Files.write(cachePath,
      Arrays.asList("EP000000000100", "EP000000000100", "SH000000000200", "SH000000000200"),
      StandardCharsets.UTF_8);

    @SuppressWarnings("unchecked")
    Set<String> loaded = (Set<String>) invokeLoadImageSkipSet(ripper, cachePath.toString());

    Assert.assertEquals(loaded.size(), 2, "Duplicate cache lines should collapse to unique entries.");
    Assert.assertTrue(loaded.contains("EP000000000100"), "Expected EP id to be present.");
    Assert.assertTrue(loaded.contains("SH000000000200"), "Expected SH id to be present.");

    Files.deleteIfExists(cachePath);
    Files.deleteIfExists(tempDir);
  }

  private static Object invokeLoadImageSkipSet(SDRipper ripper, String fileName) throws Exception
  {
    Method method = SDRipper.class.getDeclaredMethod("loadImageSkipSet", String.class);
    method.setAccessible(true);
    return method.invoke(ripper, fileName);
  }

  private static void invokeSaveImageSkipSet(SDRipper ripper, String fileName, Set<String> values) throws Exception
  {
    Method method = SDRipper.class.getDeclaredMethod("saveImageSkipSet", String.class, Set.class);
    method.setAccessible(true);
    method.invoke(ripper, fileName, values);
  }
}
