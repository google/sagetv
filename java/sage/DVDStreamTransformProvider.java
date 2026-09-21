package sage;

import java.io.IOException;

/**
 * Optional server extension that transforms DVD VM MPEG program-stream bytes.
 *
 * <p>Providers are discovered with {@link java.util.ServiceLoader} through
 * SageTV's extension classloader. Core has no dependency on a particular
 * executable, codec library, or plugin. A provider is used only when its
 * transport ID is also advertised by the connected MiniClient.</p>
 */
public interface DVDStreamTransformProvider
{
  /** Stable wire transport identifier describing the provider's output. */
  String getTransportId();

  /** MiniClient container hint, for example {@code mpegts}. */
  String getOutputFormat();

  /** Bounded health/capability check. It must not start a playback job. */
  boolean isAvailable();

  /** Opens a new independent transform session. */
  DVDStreamTransform open(DVDStreamTransformRequest request) throws IOException;
}
