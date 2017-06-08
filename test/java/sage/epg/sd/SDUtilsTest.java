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
import sage.epg.sd.SDUtils;

import java.text.ParseException;
import java.util.TimeZone;

public class SDUtilsTest
{
  @Test(groups = {"gson", "schedulesDirect", "schedule", "conversion" })
  public void testStationIdConversions()
  {
    int au = SDUtils.fromStationIDtoSageTV("AU99999999");
    int nz = SDUtils.fromStationIDtoSageTV("NZ99999999");
    int normal = SDUtils.fromStationIDtoSageTV("99999999");

    String auString = SDUtils.fromSageTVtoStationID(au);
    String nzString = SDUtils.fromSageTVtoStationID(nz);
    String normalString = SDUtils.fromSageTVtoStationID(normal);

    assert "AU99999999".equals(auString) : "Expected AU99999999, got " + auString;
    assert "NZ99999999".equals(nzString) : "Expected NZ99999999, got " + nzString;
    assert "99999999".equals(normalString) : "Expected 99999999, got " + normalString;
  }

  @Test(groups = {"gson", "schedulesDirect", "dateTime", "conversion" })
  public void testDateTimeConversion()
  {
    long date = SDUtils.SDFullUTCToMillis("2014-06-28T05:16:29Z");
    assert date == 1403932589000L : "Expected 1403932589000, got " + date;
  }

  @Test(groups = {"gson", "schedulesDirect", "dateTime", "conversion" })
  public void testDateConversion()
  {
    long date = SDUtils.SDDateUTCToMillis("2014-06-28") + TimeZone.getDefault().getRawOffset();
    assert date == 1403956800000L : "Expected 1403956800000, got " + date;
  }

  @Test(groups = {"gson", "schedulesDirect", "removeLeadingZeros", "conversion" })
  public void testRemoveLeadingZeros()
  {
    String cleaned = SDUtils.removeLeadingZeros("000");
    assert "0".equals(cleaned) : "Expected 0, got " + cleaned;
    cleaned = SDUtils.removeLeadingZeros("001");
    assert "1".equals(cleaned) : "Expected 1, got " + cleaned;
    cleaned = SDUtils.removeLeadingZeros("010");
    assert "10".equals(cleaned) : "Expected 10, got " + cleaned;
    cleaned = SDUtils.removeLeadingZeros("100");
    assert "100".equals(cleaned) : "Expected 100, got " + cleaned;
    cleaned = SDUtils.removeLeadingZeros("1000");
    assert "1000".equals(cleaned) : "Expected 1000, got " + cleaned;
    // Radio channels should not be trimmed.
    cleaned = SDUtils.removeLeadingZeros("0000");
    assert "0000".equals(cleaned) : "Expected 0000, got " + cleaned;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "conversion" })
  public void testProgramIDConversion()
  {
    String cleaned = SDUtils.fromProgramToSageTV("EP003829350004");
    assert "EP3829350004".equals(cleaned) : "Expected EP3829350004, got " + cleaned;
    cleaned = SDUtils.fromSageTVtoProgram(cleaned);
    assert "EP003829350004".equals(cleaned) : "Expected EP003829350004, got " + cleaned;
    cleaned = SDUtils.fromProgramToSageTV("EP013829350004");
    assert "EP013829350004".equals(cleaned) : "Expected EP013829350004, got " + cleaned;
    cleaned = SDUtils.fromSageTVtoProgram(cleaned);
    assert "EP013829350004".equals(cleaned) : "Expected EP013829350004, got " + cleaned;
  }
}
