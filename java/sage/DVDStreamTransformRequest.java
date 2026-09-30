package sage;

/** Immutable, implementation-neutral parameters for a DVD transform session. */
public final class DVDStreamTransformRequest
{
  private final String transportId;
  private final String videoBitrate;

  public DVDStreamTransformRequest(String transportId, String videoBitrate)
  {
    if (transportId == null || transportId.trim().length() == 0)
      throw new IllegalArgumentException("transportId is required");
    this.transportId = transportId.trim();
    this.videoBitrate = normalizeBitrate(videoBitrate);
  }

  public String getTransportId() { return transportId; }
  public String getVideoBitrate() { return videoBitrate; }

  private static String normalizeBitrate(String value)
  {
    if (value != null && value.matches("[1-9][0-9]*[kKmM]?"))
      return value;
    return "6M";
  }
}
