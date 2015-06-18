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
package sage;

import sage.TimeZoneParse;
import java.text.ParseException;
import java.util.SimpleTimeZone;

/**
 * A simple parser sanity test for TimeZoneParse
 */
public class TimeZoneParseTest {
  public static void testFailingString(String tz) {
    try {
      TimeZoneParse parse = new TimeZoneParse(tz).parse();
      throw new RuntimeException("tz=" + tz + " undetected parse error; test failed!");
    } catch (ParseException e) {
      System.out.println("tz=" + tz + " invalid string detected; test passed!");
    }
  }

  public static TimeZoneParse testPassingString(String tz) {
    try {
      TimeZoneParse timeZone = new TimeZoneParse(tz).parse();
      System.out.println("tz=" + tz + " valid string detected; test passed!");
      return timeZone;
    } catch (ParseException e) {
      throw new RuntimeException("tz=" + tz + " unexpected parse error; test FAILED!");
    }
  }

  public static void testPassingSimpleZone(String tz) {
    TimeZoneParse timeZone = testPassingString(tz);
    SimpleTimeZone simpleTimeZone = timeZone.convertToTz();
    if (simpleTimeZone == null) {
      throw new RuntimeException(
          "tz=" + tz + " valid tz parse, error converting to SimpleTimeZone; test FAILED!");
    }
  }

  public static void main(String[] args) {
    testFailingString("");
    testFailingString("CO"); // must be 3
    testFailingString("CODE"); // must be 3
    testFailingString(":CO"); // no leading :
    testFailingString("C0D"); // no numbers
    testFailingString("CO+"); // no +
    testFailingString("C-D"); // no -
    testPassingSimpleZone("CST3"); // DST is optional and stops parsing. Allows for EST+3
    testPassingSimpleZone("CST+3"); // DST is optional and stops parsing. Allows for EST+3
    testPassingSimpleZone("CST-3"); // DST is optional and stops parsing. Allows for EST+3
    testFailingString("CST!"); // offset must be number, optional +/-
    testFailingString("CST+3A"); // DST must be 3 characters
    testFailingString("CST+3AB"); // DST must be 3 characters
    // If you have DST, you must have the rest of the ",start[/time],end[/time]"
    testFailingString("CST6CDT");
    testFailingString("CST+3CDTZ");
    testFailingString("CST+3CDT3");
    testFailingString("CST+3CDT-3");
    testFailingString("CST+3CDT+3");
    testFailingString("CST+3CDT!3");
    testFailingString("CST+3CDT+3:2");
    testFailingString("CST+3CDT+3:2:1");
    testFailingString("CST+3CDT+3:2:1,");
    testFailingString("CST+3CDT+3:2:1,");
    testFailingString("CST+3CDT+3:2:1!");

    testPassingSimpleZone("CST+3CDT-3:2:1,M3.2.0/2:00,M11.1.0/2:00");
    testPassingSimpleZone("CST+3CDT-3:2:1,M3.2.0/2:00,M11.1.0/2");
    testPassingSimpleZone("CST+3CDT-3:2:1,M3.2.0/2:00,M11.1.0/2:0");
    testPassingSimpleZone("CST+3CDT-3:2:1,M3.2.0/2:00,M11.1.0/2:00:00");

    // Mess with 3:2:1
    testFailingString("CST+3CDT-x:2:1,M3.2.0/2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:x:1,M3.2.0/2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:x,M3.2.0/2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:x!M3.2.0/2:00,M11.1.0/2:00:00");

    // Mess with Mm.n.d
    testFailingString("CST+3CDT-3:2:1,M0.2.0/2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:1,M13.2.0/2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:1,Mx.2.0/2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:1,M3.0.0/2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:1,M3.6.0/2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:1,M3.x.0/2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:1,M3.2.7/2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:1,M3.2.x/2:00,M11.1.0/2:00:00");

    // Mess with time of day change
    testFailingString("CST+3CDT-3:2:1,M3.2.0/+2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:1,M3.2.0/-2:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:1,M3.2.0/24:00,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:1,M3.2.0/2:60,M11.1.0/2:00:00");
    testFailingString("CST+3CDT-3:2:1,M3.2.0/2:00:60,M11.1.0/2:00:00");

    testFailingString("CST+3CDT-3:2:1,M3.2.0/2:00:60XM11.1.0/2:00:00");

    // We don't support Jn yet (Julian)
    testFailingString("CST+3CDT-3:2:1,J1,M11.1.0/2:00:00");
    // We don't support 0-365 yet
    testFailingString("CST+3CDT-3:2:1,1,M11.1.0/2:00:00");
  }
}
