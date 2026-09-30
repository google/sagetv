package sage;

import java.io.IOException;

/** Test-only provider proving Core's SPI without an external executable. */
public final class TestDVDStreamTransformProvider implements DVDStreamTransformProvider
{
  public String getTransportId() { return "test_transform_v1"; }
  public String getOutputFormat() { return "test"; }
  public boolean isAvailable() { return true; }
  public DVDStreamTransform open(DVDStreamTransformRequest request) throws IOException
  {
    return new DVDStreamTransform()
    {
      public void write(byte[] data, int offset, int length) throws IOException {}
      public byte[] pollOutput(long timeoutMillis) throws IOException { return null; }
      public boolean isOutputEnded() { return true; }
      public void closeInput() {}
      public void close() {}
    };
  }
}
