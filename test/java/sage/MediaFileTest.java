package sage;

import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.testng.Assert.*;

/**
 * Created by seans on 11/09/16.
 */
public class MediaFileTest
{
  @Test
  public void testCreateValidFilename() throws Throwable
  {
    TestUtils.initializeSageTVForTesting();
    String fname="\u2019Test99: Nam\u00E9\u2019"; // \u00E9 = é
    boolean allowUnicode,extendedFileName;

    // test with unicode
    validateFile(fname, "Test99Nam\u00E9", allowUnicode=true, extendedFileName=false);
    validateFile(fname, "Test99 Nam\u00E9", allowUnicode=true, extendedFileName=true);

    // test no unicode
    validateFile(fname, "Test99Nam", allowUnicode=false, extendedFileName=false);
    validateFile(fname, "Test99 Nam", allowUnicode=false, extendedFileName=true);

    // test filename with all LEGAL Characters (will test if file creation fails - lets hope not)
    validateFile(
      "Test - " + MediaFile.LEGAL_FILE_NAME_CHARACTERS,
      "Test - " + MediaFile.LEGAL_FILE_NAME_CHARACTERS, allowUnicode=false, extendedFileName=true);
  }

  public void validateFile(String origName, String expectedName, boolean allowUnicode, boolean extendedFilename) throws IOException
  {
    Sage.put("allow_unicode_characters_in_generated_filenames", String.valueOf(allowUnicode));
    Sage.put("extended_filenames", String.valueOf(extendedFilename));
    String name = MediaFile.createValidFilename(origName);
    System.out.println("TESTING NAME: " + origName + "; allowUnicode: " + allowUnicode + "; extendedFilename: " + extendedFilename + "; RESULT: " + name);
    assertEquals(name, expectedName);
    File file = File.createTempFile(name,"-temp.file");
    assertTrue(file.exists() && file.isFile());
    file.deleteOnExit();
  }
}
