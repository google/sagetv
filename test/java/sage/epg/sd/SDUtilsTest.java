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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;
import sage.epg.sd.gson.JsonObject;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

  @Test(groups = {"gson", "schedulesDirect", "stream", "conversion"})
  public void testDecodeGzipStream() throws IOException
  {
    String payload = "{\"code\":0,\"message\":\"OK\"}";
    byte[] gzipPayload = gzip(payload.getBytes(StandardCharsets.ISO_8859_1));
    InputStream decodedStream = SDUtils.getDecodedInputStream("gzip", new ByteArrayInputStream(gzipPayload));
    String decoded = new String(readAll(decodedStream), StandardCharsets.ISO_8859_1);

    assert payload.equals(decoded) : "Expected decoded gzip payload to match original payload.";
  }

  @Test(groups = {"gson", "schedulesDirect", "stream", "conversion"})
  public void testDecodeDeflateStream() throws IOException
  {
    String payload = "{\"code\":0,\"message\":\"OK\"}";
    byte[] deflatePayload = deflate(payload.getBytes(StandardCharsets.ISO_8859_1));
    InputStream decodedStream = SDUtils.getDecodedInputStream("deflate", new ByteArrayInputStream(deflatePayload));
    String decoded = new String(readAll(decodedStream), StandardCharsets.ISO_8859_1);

    assert payload.equals(decoded) : "Expected decoded deflate payload to match original payload.";
  }

  @Test(groups = {"gson", "schedulesDirect", "stream", "conversion"})
  public void testStreamUnicode() throws Exception
  {
    String expected = "Nido de \u00e1guilas \u00f1 \u4e2d\u6587 \ud83d\ude00";
    String[] payloads = {
      "{\"code\":0,\"title\":\"Nido de \\u00e1guilas \\u00f1 \\u4e2d\\u6587 \\ud83d\\ude00\"}",
      "{\"code\":0,\"title\":\"" + expected + "\"}"
    };
    // Exercise debug buffering without opening a log file or changing user settings.
    Field debugWriterField = SDSession.class.getDeclaredField("debugWriter");
    debugWriterField.setAccessible(true);
    Object previousWriter = debugWriterField.get(null);
    try
    {
      for (boolean debug : new boolean[] {false, true})
      {
        StringWriter debugOutput = new StringWriter();
        debugWriterField.set(null, debug ? new PrintWriter(debugOutput) : null);
        for (String payload : payloads)
        {
          for (String encoding : new String[] {"identity", "gzip", "deflate"})
          {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            if ("gzip".equals(encoding))
              bytes = gzip(bytes);
            else if ("deflate".equals(encoding))
              bytes = deflate(bytes);
            HttpsURLConnection connection = mock(HttpsURLConnection.class);
            when(connection.getResponseCode()).thenReturn(200);
            when(connection.getContentEncoding()).thenReturn(encoding);
            when(connection.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
            try (InputStreamReader reader = SDUtils.getStream(connection))
            {
              JsonObject result = SDUtils.GSON.fromJson(reader, JsonObject.class);
              assert expected.equals(result.get("title").getAsString()) :
                "Unicode changed with debug=" + debug + ", encoding=" + encoding;
            }
          }
        }
        if (debug)
          assert debugOutput.toString().contains(expected);
      }
    }
    finally
    {
      debugWriterField.set(null, previousWriter);
    }
  }

  private static byte[] gzip(byte[] source) throws IOException
  {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
    gzipOutputStream.write(source);
    gzipOutputStream.close();
    return outputStream.toByteArray();
  }

  private static byte[] deflate(byte[] source) throws IOException
  {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(outputStream);
    deflaterOutputStream.write(source);
    deflaterOutputStream.close();
    return outputStream.toByteArray();
  }

  private static byte[] readAll(InputStream inputStream) throws IOException
  {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int read;
    while ((read = inputStream.read(buffer)) != -1)
    {
      outputStream.write(buffer, 0, read);
    }
    return outputStream.toByteArray();
  }
}
