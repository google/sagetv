package sage;

import java.util.TreeSet;

/**
 * Wraps the native library loading so that we can better contain failures.  This was done because some Java test code
 * relies on Sage.java and Sage.java cannot be initialized if it is missing the native parts.  This allows it be
 * initialized since 90% of tests are not using the native parts, but, still needs access to Sage.DBG, etc.
 *
 * Created by seans on 03/09/16.
 */
public class Native
{
  // by default (ie, runtime we'd want to log this) but during testing, we'll set to not log to make it cleaner
  public static boolean LOG_NATIVE_FAILURES = true;

  // if we have any NATIVE failures this goes to true.
  public static boolean NATIVE_FAILED = false;
  public static TreeSet<String> FAILED_NATIVES = new TreeSet<String>();

  /**
   * Load the given system library and optionally log the failure if it cannot be loaded.  This never throws an Exception, but
   * it will set the FAILED_NATIVES to true if there libraries the fail to load.  The logic here is that it's better
   * to log it and let the code carry on, which it may fail later, but at least it will be logged.  Currently most
   * System.loadLibrary calls are in the static section of class initialization, so when it can't find a System Library
   * we end with a Class Initialization error that sometimes masks the real issue (ie, can't load the library)
   *
   * @param lib System Library
   */
  public static void loadLibrary(String lib)
  {
    try
    {
      System.loadLibrary(lib);
    }
    catch (Throwable t)
    {
      NATIVE_FAILED=true;
      if (LOG_NATIVE_FAILURES && !FAILED_NATIVES.contains(lib))
        t.printStackTrace();
      FAILED_NATIVES.add(lib);
    }
  }
}
