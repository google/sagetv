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

import java.io.IOException;

public class SDErrorsTest
{
  @Test(groups = {"schedulesDirect", "errors", "mapping"})
  public void testErrorCodeMappings()
  {
    assert SDErrors.getErrorForCode(1003) == SDErrors.USERAGENT_REQUIRED;
    assert SDErrors.getErrorForCode(1004) == SDErrors.TOKEN_MISSING;
    assert SDErrors.getErrorForCode(1007) == SDErrors.EMPTY_REQUEST;
    assert SDErrors.getErrorForCode(1008) == SDErrors.INCORRECT_REQUEST;
    assert SDErrors.getErrorForCode(1010) == SDErrors.TOKEN_INVALID;
    assert SDErrors.getErrorForCode(1011) == SDErrors.INCORRECT_CONTENT_TYPE;
  }

  @Test(groups = {"schedulesDirect", "errors", "throw"})
  public void testThrowErrorForCode() throws IOException
  {
    assertThrowsForCode(1003, SDErrors.USERAGENT_REQUIRED);
    assertThrowsForCode(1007, SDErrors.EMPTY_REQUEST);
    assertThrowsForCode(1008, SDErrors.INCORRECT_REQUEST);
    assertThrowsForCode(1010, SDErrors.TOKEN_INVALID);
    assertThrowsForCode(1011, SDErrors.INCORRECT_CONTENT_TYPE);
  }

  private static void assertThrowsForCode(int code, SDErrors expected) throws IOException
  {
    try
    {
      SDErrors.throwErrorForCode(code);
      assert false : "Expected SDException for code " + code;
    }
    catch (SDException e)
    {
      assert e.ERROR == expected : "Expected " + expected + ", got " + e.ERROR;
    }
  }
}
