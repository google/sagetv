package sage.plugin;

import org.testng.annotations.Test;
import sage.Native;
import sage.TestUtils;

import static org.testng.Assert.*;

/**
 * Created by seans on 03/09/16.
 */
public class PluginWrapperTest
{
  @Test
  public void testGetMD5() throws Exception
  {
    // Don't report failed native library loading
    Native.LOG_NATIVE_FAILURES=false;

    String md5="beec1608cf997d3acb42f0ab772b143f";

    String testUrl = TestUtils.file2url(TestUtils.getTestResource("test-jar-1.0.zip"));

    // verify it will look it up from the base url if the md5 is null
    verifyMD5(md5, testUrl, null);

    // verify it will look it up from the base url if the md5 is empty
    verifyMD5(md5, testUrl, "  ");

    // verify it can use the http reference to md5
    verifyMD5(md5, testUrl,
      testUrl + ".md5");

    // verify that if we give it an actual MD5 it works
    verifyMD5(md5, "http://doesn.not.exist/file.jar", md5);
  }

  void verifyMD5(String md5, String jarUrl, String md5Url)
  {
    PluginWrapper.Package pack = new PluginWrapper.Package("JAR", jarUrl, md5Url, true);
    assertEquals(md5Url, pack.getRawMD5());
    assertEquals(md5, pack.getMD5());
  }
}