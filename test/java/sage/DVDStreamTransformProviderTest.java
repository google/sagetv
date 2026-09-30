package sage;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class DVDStreamTransformProviderTest
{
  @Test
  public void transportIntersectionIsExactAndCaseInsensitive()
  {
    assertTrue(DVDStreamTransformRegistry.supportsTransport(
        "native,dvd_mpegts_v1", "dvd_mpegts_v1"));
    assertTrue(DVDStreamTransformRegistry.supportsTransport(
        "NATIVE, DVD_MPEGTS_V1", "dvd_mpegts_v1"));
    assertFalse(DVDStreamTransformRegistry.supportsTransport(
        "native,dvd_mpegts_v10", "dvd_mpegts_v1"));
    assertFalse(DVDStreamTransformRegistry.supportsTransport(null,
        "dvd_mpegts_v1"));
  }

  @Test
  public void requestBoundsInvalidBitrate()
  {
    assertEquals(new DVDStreamTransformRequest("dvd_mpegts_v1", "8M")
        .getVideoBitrate(), "8M");
    assertEquals(new DVDStreamTransformRequest("dvd_mpegts_v1", "bad value")
        .getVideoBitrate(), "6M");
  }

  @Test
  public void optionalProviderRequiresNegotiatedTransport()
  {
    assertEquals(DVDStreamTransformRegistry.findAvailable(
        "native,test_transform_v1").getTransportId(), "test_transform_v1");
    assertNull(DVDStreamTransformRegistry.findAvailable("native"));
  }
}
