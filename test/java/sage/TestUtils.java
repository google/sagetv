package sage;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by seans on 04/09/16.
 */
public class TestUtils
{
  public static File JARs = null;
  public static File STVs = null;

  private static File projectRoot = null;
  private static File testResourcesRoot = null;
  /**
   * Used for Testing.  This will call the normal startup() method with but it will not start a full SageTV server.
   * It just initializes enough of the SageTV subsystem, such as prefs and resources so that most of the code that
   * doesn't rely on native parts or a running server can still be tested.
   *
   * @throws Throwable
   */
  public static void initializeSageTVForTesting() throws Throwable
  {
    if (System.getProperty("sage.testing.started")!=null)
    {
      // the test stub has been started, so just exit since there is no point
      // in doing this again during a test run
      return;
    }
    System.setProperty("sage.testing.started", "true");

    Native.LOG_NATIVE_FAILURES=false;

    System.setProperty("java.awt.headless", "true");

    Sage.TESTING=true;
    Sage.client=false;
    Sage.USE_HIRES_TIME=false;

    // create some important files/dirs for testing
    JARs = new File("JARs");
    STVs = new File("STVs");
    JARs.mkdirs();
    STVs.mkdirs();

    // create the startup params
    String mainWnd="0";
    String stdoutHandle="0";
    String sysArgs = "-startup"; // can also accept -properties
    String appName = "sagetv sagetv";
    Sage.startup(new String[] {mainWnd, stdoutHandle, sysArgs, appName});
  }

  /**
   * Returns a resource file relative to the test/resources/ directory
   * @param name
   * @return
   */
  public static File getTestResource(String name)
  {
    if (projectRoot==null) getProjectRoot();
    return new File(testResourcesRoot, name);
  }

  /**
   * Returns the file that is the ROOT of the project files
   *
   * @return
   */
  public static File getProjectRoot()
  {
    if (projectRoot==null)
    {
      // TEST Root is buildoutput/sagetv_test/ so we go back 2 dirs to get project root
      projectRoot = new File("../../");
      testResourcesRoot = new File(projectRoot, "test/resources/");
      if (!testResourcesRoot.exists())
      {
        // check if CWD is actually the project root (should not be, but sometimes if you run tests in the IDE ie might)
        projectRoot = new File(".");
        testResourcesRoot = new File(projectRoot, "test/resources/");
        if (!testResourcesRoot.exists())
        {
          throw new RuntimeException("Unable to determine the Project Root.  Current Dir is: " + projectRoot.getAbsolutePath());
        }
      }
    }
    return projectRoot;
  }

  public static String file2url(File file)
  {
    try
    {
      return file.getCanonicalFile().toURI().toURL().toExternalForm();
    } catch (java.io.IOException e)
    {
      e.printStackTrace();
    }
    return null;
  }
}
