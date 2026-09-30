package sage;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Discovers optional transform providers without making Core depend on one. */
final class DVDStreamTransformRegistry
{
  private DVDStreamTransformRegistry() {}

  static DVDStreamTransformProvider findAvailable(String clientTransports)
  {
    ClassLoader extensionLoader = Sage.extClassLoader == null ?
        DVDStreamTransformRegistry.class.getClassLoader() : Sage.extClassLoader;
    ServiceLoader<DVDStreamTransformProvider> loader = ServiceLoader.load(
        DVDStreamTransformProvider.class, extensionLoader);
    Iterator<DVDStreamTransformProvider> providers = loader.iterator();
    while (true)
    {
      final DVDStreamTransformProvider provider;
      try
      {
        if (!providers.hasNext())
          return null;
        provider = providers.next();
      }
      catch (ServiceConfigurationError e)
      {
        if (Sage.DBG) System.out.println("Ignoring invalid DVD transform provider: " + e);
        return null;
      }

      try
      {
        String transportId = provider.getTransportId();
        if (supportsTransport(clientTransports, transportId) && provider.isAvailable())
          return provider;
      }
      catch (Throwable t)
      {
        // A broken optional provider must never prevent native DVD playback.
        if (Sage.DBG) System.out.println("Ignoring unavailable DVD transform provider: " + t);
      }
    }
  }

  static boolean supportsTransport(String transports, String requested)
  {
    if (transports == null || requested == null || requested.trim().length() == 0)
      return false;
    String[] values = transports.split(",");
    for (int i = 0; i < values.length; i++)
      if (requested.equalsIgnoreCase(values[i].trim()))
        return true;
    return false;
  }
}
