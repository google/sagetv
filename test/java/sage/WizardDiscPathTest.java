package sage;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertEquals;

public class WizardDiscPathTest
{
  private File temporaryFolder;

  @BeforeMethod
  public void createTemporaryFolder()
  {
    temporaryFolder = new File(System.getProperty("java.io.tmpdir"),
        "sagetv-disc-path-" + System.nanoTime());
    temporaryFolder.mkdirs();
  }

  @AfterMethod
  public void removeTemporaryFolder()
  {
    deleteTree(temporaryFolder);
  }

  @Test
  public void volumeRootResolvesToNestedVideoTs() throws Exception
  {
    File root = newFolder("movie");
    File videoTs = new File(root, "VIDEO_TS");
    videoTs.mkdir();
    new File(videoTs, "VIDEO_TS.IFO").createNewFile();
    assertEquals(videoTs, Wizard.normalizePlayableDiscPath(root));
  }

  @Test
  public void menuLessVolumeStillResolvesFromIfo() throws Exception
  {
    File root = newFolder("menu-less");
    File videoTs = new File(root, "VIDEO_TS");
    videoTs.mkdir();
    new File(videoTs, "VIDEO_TS.IFO").createNewFile();
    new File(videoTs, "VTS_01_0.IFO").createNewFile();
    new File(videoTs, "VTS_01_1.VOB").createNewFile();
    assertEquals(videoTs, Wizard.normalizePlayableDiscPath(root));
  }

  @Test
  public void videoTsVobResolvesToItsVolumeFolder() throws Exception
  {
    File videoTs = newFolder("VIDEO_TS");
    new File(videoTs, "VIDEO_TS.IFO").createNewFile();
    File menuVob = new File(videoTs, "VIDEO_TS.VOB");
    menuVob.createNewFile();
    assertEquals(videoTs, Wizard.normalizePlayableDiscPath(menuVob));
  }

  @Test
  public void unrelatedDirectoryIsUnchanged() throws Exception
  {
    File root = newFolder("ordinary-videos");
    new File(root, "movie.vob").createNewFile();
    assertEquals(root, Wizard.normalizePlayableDiscPath(root));
  }

  private File newFolder(String name)
  {
    File folder = new File(temporaryFolder, name);
    folder.mkdirs();
    return folder;
  }

  private static void deleteTree(File file)
  {
    if (file == null || !file.exists())
      return;
    File[] children = file.listFiles();
    if (children != null)
      for (File child : children)
        deleteTree(child);
    file.delete();
  }
}
