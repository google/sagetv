package sage.dvd;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class VMSafetyTest
{
  @Test
  public void programChainNumberMustFitDeclaredAndParsedTables()
  {
    assertTrue(VM.isValidProgramChainNumber(1, 1, 1));
    assertTrue(VM.isValidProgramChainNumber(8, 8, 8));
    assertFalse(VM.isValidProgramChainNumber(0, 8, 8));
    assertFalse(VM.isValidProgramChainNumber(9, 8, 8));
    assertFalse(VM.isValidProgramChainNumber(8, 7, 8));
    assertFalse(VM.isValidProgramChainNumber(8, 8, 7));
  }
}
