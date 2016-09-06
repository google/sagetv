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
    assertEquals(md5, "beec1608cf997d3acb42f0ab772b143f");
  }
}