package sage;

import org.testng.annotations.Test;

import static org.testng.Assert.*;
import static sage.TestUtils.*;

/**
 * Created by seans on 03/09/16.
 */
public class IOUtilsTest
{
  @Test
  public void testGetUrlAsString() throws Exception
  {
    String md5 = IOUtils.getUrlAsString(file2url(getTestResource("test-jar-1.0.zip.md5")));
    assertEquals(md5, "beec1608cf997d3acb42f0ab772b143f" + System.lineSeparator());
  }

  @Test
  public void testReadFileAsString() throws Exception
  {
    String md5 = IOUtils.getFileAsString(getTestResource("test-jar-1.0.zip.md5"));
    assertEquals(md5, "beec1608cf997d3acb42f0ab772b143f" + System.lineSeparator());
  }

  @Test
  public void testCalcSHA1()
  {
    String sha1 = IOUtils.calcSHA1("test-hash-test-hash-test-hash");
    assertEquals(sha1, "1930681a53ff9ee8af4dcb1354d0d205027cbb73");
  }
}
