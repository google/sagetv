package sage.plugin;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import sage.*;

import java.io.File;
import java.io.FileNotFoundException;

import static org.testng.Assert.*;

/**
 * Created by seans on 03/09/16.
 */
public class CorePluginManagerTest
{
  File stvRoot = null;

  @BeforeClass
  public void setup() throws Throwable
  {
    TestUtils.initializeSageTVForTesting();

    // needed by the installer
    stvRoot = TestUtils.STVs;
  }

  @Test
  public void testInstallPluginWithJarPackage() throws Throwable
  {
    // Create a dummy jar as the source along with an MD5 that can be resolved
    String version = String.valueOf(System.currentTimeMillis());
    String jarName = "test-1.0."+version+".jar";
    File f = new File("tmp/localfiles/"+jarName);
    f.getParentFile().mkdirs();
    TestDataUtils.fillLocalFile(f.getPath(), jarName.getBytes(), 128000);
    IOUtils.writeStringToFile(new File(f.getParent(), f.getName()+".md5"), IOUtils.calcMD5(f));


    // Create a plugin manager instance with a test plugin
    CorePluginManager corePluginManager = CorePluginManager.getInstance();
    PluginWrapper plugin = new PluginWrapper();
    plugin.setId("test");
    plugin.setVersion("1.0."+version);
    PluginWrapper.Package pkg = new PluginWrapper.Package("JAR", f.getCanonicalFile().toURI().toURL().toString(), null, true);
    plugin.addPackage(pkg);

    // plugin installer needs a UI apparently...
    UIManager uiManager = UIManager.getLocalUI();

    String RESULT = corePluginManager.installPlugin(plugin, uiManager, stvRoot);
    System.out.println(RESULT);
    assertEquals("OK", RESULT);
    File dest = new File("JARs/"+jarName);
    assertTrue(dest.exists());
    assertEquals(pkg.getMD5().toLowerCase(), IOUtils.calcMD5(dest).toLowerCase());
  }

  @Test
  public void testInstallPluginWithZipPackageInJARs() throws Throwable
  {
    File zipPackage = new File("../../test/resources/test-jar-1.0.zip");
    if (!zipPackage.exists()) throw new FileNotFoundException("Missing " + zipPackage.getAbsolutePath());

    // Create a dummy jar as the source along with an MD5 that can be resolved
    // Create a plugin manager instance with a test plugin
    CorePluginManager corePluginManager = CorePluginManager.getInstance();
    PluginWrapper plugin = new PluginWrapper();
    plugin.setId("test-jar");
    plugin.setVersion("1.0");
    PluginWrapper.Package pkg = new PluginWrapper.Package("JAR", zipPackage.getCanonicalFile().toURI().toURL().toString(), null, true);
    pkg.setRawMD5(IOUtils.calcMD5(zipPackage));
    plugin.addPackage(pkg);

    // plugin installer needs a UI apparently...
    UIManager uiManager = UIManager.getLocalUI();

    String RESULT = corePluginManager.installPlugin(plugin, uiManager, stvRoot);
    System.out.println(RESULT);
    assertEquals("OK", RESULT);
    File dest = new File("JARs/test-jar-1.0.jar");
    assertTrue(dest.exists());
    assertEquals("72972c8c2c463de655de1a03cbc78a10", IOUtils.calcMD5(dest).toLowerCase());
  }


}