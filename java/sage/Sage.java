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

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Label;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Provider;
import java.security.Security;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public final class Sage
{
  // This makes the service fail if TRUE
  private static final boolean REQUIRE_WIN32_LAUNCHER = false;
  // This is used to allow SageTV to run under JProfiler on Windows w/out a launcher
  static final boolean DISABLE_MSG_LOOP_FOR_JPROFILER = false;
  public static boolean WINDOWS_OS = false;
  public static boolean LINUX_OS = false;
  public static boolean LINUX_IS_ROOT = false; // will be true if SageTV Linux is running as 'root'
  public static boolean MAC_OS_X = false;
  public static boolean VISTA_OS = false; // if this is true, then WINDOWS_OS is also true

  /**
   * Testing is set to true when the sage.testing System Property is set.  TESTING has a special meaning
   * in that some function calls/behaviours will be different.  ie, Generally in TESTING the NATIVE parts fail
   * to load, and as such we tend to avoid calling native methods.  Also STDOUT/STDERR are NOT redirected to a
   * file when TESTING is enabled.
   */
  public static boolean TESTING = false;


  public static final boolean PERF_ANALYSIS = false;
  public static final long UI_BUILD_THRESHOLD_TIME = 1;
  public static final long EVALUATE_THRESHOLD_TIME = 1;
  private static long baseSystemTime;
  private static long baseEventTime;
  private static long baseCPUTime;
  private static long cpuFreq;
  private static boolean alwaysUseCPUTime = false;
  private static Thread mainThread;
  public static boolean USE_HIRES_TIME = true;
  static
  {
    TESTING = "true".equalsIgnoreCase(System.getProperty("sage.testing"));
    WINDOWS_OS = System.getProperty("os.name").toLowerCase().indexOf("windows") != -1;
    MAC_OS_X = System.getProperty("os.name").toLowerCase().indexOf("mac os x") != -1;
    LINUX_OS = !WINDOWS_OS && !MAC_OS_X;
    VISTA_OS = WINDOWS_OS && (System.getProperty("os.version").startsWith("6.") || System.getProperty("os.version").startsWith("7."));
    LINUX_IS_ROOT = "root".equals(System.getProperty("user.name"));
    if (WINDOWS_OS)
      sage.Native.loadLibrary("SageTVWin32");
    else
      sage.Native.loadLibrary("Sage");

    baseSystemTime = System.currentTimeMillis();
    // prevents SageTV initialization when the Native libraries are not loaded (or can't be loaded)
    if (!Native.NATIVE_FAILED)
    {
      baseEventTime = getEventTime0();
      baseCPUTime = getCpuTime();
      cpuFreq = getCpuResolution();
    }
    createDFFormats();
  }

  public static final byte CLIENT_COMPATIBLE_MAJOR_VERSION = 9;
  public static final byte CLIENT_COMPATIBLE_MINOR_VERSION = 0;
  public static final byte CLIENT_COMPATIBLE_MICRO_VERSION = 14;

  public static final byte ENCODER_COMPATIBLE_MAJOR_VERSION = 4;
  public static final byte ENCODER_COMPATIBLE_MINOR_VERSION = 1;
  public static final byte ENCODER_COMPATIBLE_MICRO_VERSION = 0;

  public static Locale userLocale = null;
  private static ResourceBundle coreRez = null;

  public static final long MILLIS_PER_MIN = 60*1000L;
  public static final long MILLIS_PER_HR = 60*60*1000L;
  public static final long MILLIS_PER_DAY = 24*60*60*1000L;
  public static final long MILLIS_PER_WEEK = 7*24*60*60*1000L;
  private static final int DOS_BYTE_LIMIT = 10000000;
  public static final int STANDARD_TIMEOUT = 120000; // 2 minutes

  public static final String BYTE_CHARSET = "ISO8859_1";
  public static final String I18N_CHARSET = "UTF-8";

  // Force debug logging on always...this will be so helpful for troubleshooting.
  public static final boolean DBG = true;
  public static long dl = 0; // debugging level, right now used for HDHR driver, reflects above flag (is not obfuscated)

  private static long globalTimeOffset = 0;

  public static long time()
  {
    if (USE_HIRES_TIME)
    {
      long systime = System.currentTimeMillis();
      long cputime = (getCpuTime() - baseCPUTime)*1000/cpuFreq + baseSystemTime;
      // if its off by more than 100 msec then there's something wrong with our base times
      // so recalculate them
      if (!alwaysUseCPUTime && Math.abs(cputime - systime) > 100)
      {
        baseSystemTime = systime;
        baseCPUTime = getCpuTime();
        return systime - globalTimeOffset;
      }
      else
        return cputime - globalTimeOffset;
    }
    else
      return System.currentTimeMillis() - globalTimeOffset;
  }

  public static long eventTime()
  {
    if (USE_HIRES_TIME)
    {
      long systime = getEventTime0();
      long cputime = (getCpuTime() - baseCPUTime)*1000/cpuFreq + baseEventTime;
      // if its off by more than 100 msec then there's something wrong with our base times
      // so recalculate them
      if (!alwaysUseCPUTime && Math.abs(cputime - systime) > 100)
      {
        baseEventTime = systime;
        baseCPUTime = getCpuTime();
        return systime;
      }
      else
        return cputime;
    }
    else
      if (!Native.NATIVE_FAILED)
        return getEventTime0();
      else
        return System.currentTimeMillis();
  }

  public static int getHKEYForName(String s)
  {
    if ("HKCR".equalsIgnoreCase(s) || "HKEY_CLASSES_ROOT".equalsIgnoreCase(s))
      return HKEY_CLASSES_ROOT;
    else if ("HKCC".equalsIgnoreCase(s) || "HKEY_CURRENT_CONFIG".equalsIgnoreCase(s))
      return HKEY_CURRENT_CONFIG;
    else if ("HKCU".equalsIgnoreCase(s) || "HKEY_CURRENT_USER".equalsIgnoreCase(s))
      return HKEY_CURRENT_USER;
    else if ("HKU".equalsIgnoreCase(s) || "HKEY_USERS".equalsIgnoreCase(s))
      return HKEY_USERS;
    else
      return HKEY_LOCAL_MACHINE;
  }
  public static native String readStringValue(int root, String mainKey, String valueName);
  public static native int readDwordValue(int root, String mainKey, String valueName);
  public static native boolean writeStringValue(int root, String mainKey, String valueName,
      String value);
  public static native boolean writeDwordValue(int root, String mainKey, String valueName,
      int value);
  public static native boolean removeRegistryValue(int root, String mainKey, String valueName);
  public static native String[] getRegistryNames(int root, String mainKey);
  public static native String[] getRegistrySubkeys(int root, String mainKey);
  public static native void addTaskbarIcon0(long hwnd);
  static native void updateTaskbarIcon0(long hwnd, String tipText);
  public static native void removeTaskbarIcon0(long hwnd);
  public static native long getCpuResolution();
  public static native long getCpuTime();
  public static native long getEventTime0();
  public static native String getMACAddress0();
  public static native void fixFailedWinDriveMappings();

  public static final int HKEY_CLASSES_ROOT = 1;
  public static final int HKEY_CURRENT_CONFIG = 2;
  public static final int HKEY_CURRENT_USER = 3;
  public static final int HKEY_LOCAL_MACHINE = 4;
  public static final int HKEY_USERS = 5;

  private static void createDFFormats()
  {
    if (Sage.userLocale == null)
    {
      DF_CLEAN = new SimpleDateFormat("EE M/d H:mm");
      DF = new SimpleDateFormat("EE M/d H:mm:ss.SSS");
      DF_LITTLE = new SimpleDateFormat("MMdd.HH:mm");
      DF_FULL = new SimpleDateFormat("EE M/d/yyyy H:mm:ss.SSS");
      DF_TECH = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS zzz");
      DF_STD = new SimpleDateFormat("M/d/yyyy");
      DF_JMEDIUM = DateFormat.getDateInstance(DateFormat.MEDIUM);
      DF_JSHORT =  DateFormat.getDateInstance(DateFormat.SHORT);
      DF_JFULL =  DateFormat.getDateInstance(DateFormat.FULL);
      TF_JFULL =  DateFormat.getTimeInstance(DateFormat.FULL);
      TF_JMEDIUM =  DateFormat.getTimeInstance(DateFormat.MEDIUM);
      TF_JSHORT =  DateFormat.getTimeInstance(DateFormat.SHORT);
      TF_JLONG =  DateFormat.getTimeInstance(DateFormat.LONG);
    }
    else
    {
      DF_CLEAN = new SimpleDateFormat("EE M/d H:mm", Sage.userLocale);
      DF = new SimpleDateFormat("EE M/d H:mm:ss.SSS", Sage.userLocale);
      DF_LITTLE = new SimpleDateFormat("MMdd.HH:mm", Sage.userLocale);
      DF_FULL = new SimpleDateFormat("EE M/d/yyyy H:mm:ss.SSS", Sage.userLocale);
      DF_TECH = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS zzz", Sage.userLocale);
      DF_STD = new SimpleDateFormat("M/d/yyyy", Sage.userLocale);
      DF_JMEDIUM = DateFormat.getDateInstance(DateFormat.MEDIUM, Sage.userLocale);
      DF_JSHORT =  DateFormat.getDateInstance(DateFormat.SHORT, Sage.userLocale);
      DF_JFULL =  DateFormat.getDateInstance(DateFormat.FULL, Sage.userLocale);
      TF_JFULL =  DateFormat.getTimeInstance(DateFormat.FULL, Sage.userLocale);
      TF_JMEDIUM =  DateFormat.getTimeInstance(DateFormat.MEDIUM, Sage.userLocale);
      TF_JSHORT =  DateFormat.getTimeInstance(DateFormat.SHORT, Sage.userLocale);
      TF_JLONG =  DateFormat.getTimeInstance(DateFormat.LONG, Sage.userLocale);
    }
  }
  private static DateFormat DF_CLEAN;
  private static DateFormat DF;
  private static DateFormat DF_LITTLE;
  private static DateFormat DF_FULL;
  private static DateFormat DF_TECH;
  private static DateFormat DF_STD;
  private static DateFormat DF_JMEDIUM;
  private static DateFormat DF_JSHORT;
  private static DateFormat DF_JFULL;
  private static DateFormat TF_JMEDIUM;
  private static DateFormat TF_JSHORT;
  private static DateFormat TF_JFULL;
  private static DateFormat TF_JLONG;
  public static final String df() { return df(Sage.time()); }
  public static final String df(long time)
  {
    synchronized (DF)
    {
      return DF.format(new Date(time));
    }
  }
  public static final String dfFull(long time)
  {
    synchronized (DF_FULL)
    {
      return DF_FULL.format(new Date(time));
    }
  }
  public static final String dfTech(long time)
  {
    synchronized (DF_TECH)
    {
      return DF_TECH.format(new Date(time));
    }
  }
  public static final String dfClean(long time)
  {
    synchronized (DF_CLEAN)
    {
      return DF_CLEAN.format(new Date(time));
    }
  }
  public static final String dfLittle(long time)
  {
    synchronized (DF_LITTLE)
    {
      return DF_LITTLE.format(new Date(time));
    }
  }
  public static final String dfStd(long time)
  {
    synchronized (DF_STD)
    {
      return DF_STD.format(new Date(time));
    }
  }
  public static final String dfjMed(long time)
  {
    synchronized (DF_JMEDIUM)
    {
      return DF_JMEDIUM.format(new Date(time));
    }
  }
  public static final String dfjShort(long time)
  {
    synchronized (DF_JSHORT)
    {
      return DF_JSHORT.format(new Date(time));
    }
  }
  public static final String dfjFull(long time)
  {
    synchronized (DF_JFULL)
    {
      return DF_JFULL.format(new Date(time));
    }
  }
  public static final String tfjMed(long time)
  {
    synchronized (TF_JMEDIUM)
    {
      return TF_JMEDIUM.format(new Date(time));
    }
  }
  public static final String tfjShort(long time)
  {
    synchronized (TF_JSHORT)
    {
      return TF_JSHORT.format(new Date(time));
    }
  }
  public static final String tfjFull(long time)
  {
    synchronized (TF_JFULL)
    {
      return TF_JFULL.format(new Date(time));
    }
  }
  public static final String tfjLong(long time)
  {
    synchronized (TF_JLONG)
    {
      return TF_JLONG.format(new Date(time));
    }
  }
  public static final String durFormat(long milliDur)
  {
    boolean neggy = milliDur < 0;
    if (neggy) milliDur *= -1;
    milliDur /= 1000;
    long h = milliDur/3600;
    milliDur -= 3600*h;
    long m = milliDur/60;
    milliDur -= 60*m;
    long s = milliDur;
    return (neggy ? "-" : "") + h + ":" + (m < 10 ? "0" : "") +
        m + ":" + (s < 10 ? "0" : "") + s;
  }
  public static final String durFormatSlim(long milliDur)
  {
    boolean neggy = milliDur < 0;
    if (neggy) milliDur *= -1;
    milliDur /= 1000;
    long h = milliDur/3600;
    milliDur -= 3600*h;
    long m = milliDur/60;
    milliDur -= 60*m;
    long s = milliDur;
    return (neggy ? "-" : "") + (h == 0 ? "" : (h + ":")) + (m < 10 && h != 0 ? "0" : "") +
        m + ":" + (s < 10 ? "0" : "") + s;
  }
  public static final String durFormatMillis(long milliDur)
  {
    boolean neggy = milliDur < 0;
    if (neggy) milliDur *= -1;
    long ms = milliDur % 1000;
    milliDur /= 1000;
    long h = milliDur/3600;
    milliDur -= 3600*h;
    long m = milliDur/60;
    milliDur -= 60*m;
    long s = milliDur;
    return (neggy ? "-" : "") + h + ":" + (m < 10 ? "0" : "") +
        m + ":" + (s < 10 ? "0" : "") + s + "." +
        (ms < 100 ? (ms < 10 ? "00" : "0") : "") + ms;
  }
  public static final String durFormatHrMinPretty(long milliDur)
  {
    boolean neggy = milliDur < 0;
    if (neggy) milliDur *= -1;
    milliDur /= 1000;
    long h = milliDur/3600;
    milliDur -= 3600*h;
    long m = milliDur/60;
    String str = (neggy ? "-" : "");
    if (h > 0)
      str += "" + h + " " + Sage.rez("Hour_Abbrev");
    if (m > 0)
      str += "" + m + " " + Sage.rez("Minute_Abbrev");
    return str.length() == 0 ? ("0 " + Sage.rez("Minute_Abbrev")) : str;
  }
  public static final String durFormatPretty(long milliDur)
  {
    boolean neggy = milliDur < 0;
    if (neggy) milliDur *= -1;
    milliDur /= 1000;
    long h = milliDur/3600;
    milliDur -= 3600*h;
    long m = milliDur/60;
    String str = (neggy ? "-" : "");
    if (h > 23)
    {
      str += "" + (h/24) + " " + Sage.rez(h>47 ? "Days_Duration" : "Day_Duration") + " ";
      h %= 24;
    }
    if (h > 0)
      str += "" + h + " " + Sage.rez(h > 1 ? "Hours_Duration" : "Hour_Duration") + " ";
    if (m > 0)
      str += "" + m + " " + Sage.rez(m > 1 ? "Minutes_Duration" : "Minute_Duration");
    return str.length() == 0 ? ("0 " + Sage.rez("Minutes_Duration")) : str;
  }
  public static final String durFormatPrettyWithSeconds(long milliDur)
  {
    boolean neggy = milliDur < 0;
    if (neggy) milliDur *= -1;
    milliDur /= 1000;
    long h = milliDur/3600;
    milliDur -= 3600*h;
    long m = milliDur/60;
    milliDur -= 60*m;
    String str = (neggy ? "-" : "");
    if (h > 23)
    {
      str += "" + (h/24) + " " + Sage.rez(h>47 ? "Days_Duration" : "Day_Duration") + " ";
      h %= 24;
    }
    if (h > 0)
      str += "" + h + " " + Sage.rez(h > 1 ? "Hours_Duration" : "Hour_Duration") + " ";
    if (m > 0)
      str += "" + m + " " + Sage.rez(m > 1 ? "Minutes_Duration" : "Minute_Duration") + " ";
    if (milliDur > 0)
      str += "" + milliDur + " " + Sage.rez(milliDur > 1 ? "Seconds_Duration" : "Second_Duration");
    return str.length() == 0 ? ("0 " + Sage.rez("Seconds_Duration")) : str;
  }

  // I've set the min heap free ratio to be 15% for the JVM
  public static final float MEM_USAGE_FOR_GC = 0.80f;
  public static final float MEM_USAGE_FOR_FULL_GC = 0.95f;
  private static long lastGCTime;
  public static final boolean gcPause()
  {
    float initialMemUsage = (1.0f - Runtime.getRuntime().freeMemory()*1.0f/Runtime.getRuntime().totalMemory())
        - 0.01f; // to avoid rounding issues
    if (initialMemUsage > MEM_USAGE_FOR_FULL_GC)
    {
      lastGCTime = Sage.time();
      int sleepCount = 0;
      if (DBG) System.out.println("Sage waiting for GC to free up some memory usage%=" +
          (1.0f - Runtime.getRuntime().freeMemory()*1.0f/Runtime.getRuntime().totalMemory()));
      do
      {
        try{Thread.sleep(20);} catch(Exception e){}
        sleepCount++;
        if (sleepCount == 1 || sleepCount == 150)
          System.gc();
      }
      while ((1.0f - Runtime.getRuntime().freeMemory()*1.0f/Runtime.getRuntime().totalMemory()) >= initialMemUsage &&
          sleepCount < 250);
      if (DBG) System.out.println("Sage done waiting for GC to free up some memory usage%=" +
          (1.0f - Runtime.getRuntime().freeMemory()*1.0f/Runtime.getRuntime().totalMemory()));
      return true;
    }
    else if (initialMemUsage > MEM_USAGE_FOR_GC)
    {
      return gc(false);
    }
    else return false;
  }
  public static final boolean gc()
  {
    return gc(false);
  }
  public static final boolean gc(boolean ignoreTime)
  {
    if (true) return false;
    float memUsage = 1.0f - Runtime.getRuntime().freeMemory()*1.0f/Runtime.getRuntime().totalMemory();
    if ((memUsage > MEM_USAGE_FOR_GC && Runtime.getRuntime().totalMemory() < Runtime.getRuntime().maxMemory() &&
        (ignoreTime || (Sage.time() - lastGCTime > 15000))) || (memUsage > MEM_USAGE_FOR_FULL_GC))
    {
      if (DBG) System.out.println("Sage system GC running...mem usage %=" +
          (1.0f - (Runtime.getRuntime().freeMemory()*1.0f/Runtime.getRuntime().totalMemory())));
      System.gc();
      lastGCTime = Sage.time();
      return true;
    }
    return false;
  }

  public static void setSystemTime(long x)
  {
    if (Sage.WINDOWS_OS)
    {
      Calendar cal = new GregorianCalendar();
      cal.setTimeZone(originalTimeZone);
      cal.setTimeInMillis(x);
      setSystemTime(cal.get(Calendar.YEAR),
          cal.get(Calendar.MONTH) + 1, // win32 is 1- based, java is 0-based
          cal.get(Calendar.DATE),
          cal.get(Calendar.HOUR_OF_DAY),
          cal.get(Calendar.MINUTE),
          cal.get(Calendar.SECOND),
          cal.get(Calendar.MILLISECOND));
    }
    else
    {
      setSystemTime0(x);
      if (LINUX_OS)
      {
        // This is needed to synchronize the hardware clock to the system clock so that our
        // clock update is not lost the next time we reboot.
        IOUtils.exec(new String[] { "hwclock", "--systohc" });
      }
    }
  }
  private static TimeZone originalTimeZone = TimeZone.getDefault();
  //public static native boolean isProcessRunning(String procName);
  private static native void println0(long handle, String s);
  private static native long getDiskFreeSpace0(String disk);
  private static native long getDiskTotalSpace0(String disk);
  public static String getFileSystemTypeX(String root)
  {
    return getFileSystemTypeX(root, false);
  }
  public static String getFileSystemTypeX(String root, boolean skipSeekerSMBCheck)
  {
    String rv = getFileSystemType(root);
    if (rv != null && rv.startsWith("0x"))
    {
      rv = rv.toLowerCase();
      if ("0xadf5".equals(rv))
        return "ADFS";
      else if ("0xff534d42".equals(rv))
        return "CIFS";
      else if ("0x137d".equals(rv))
        return "EXT";
      else if ("0xef51".equals(rv))
        return "EXT2Old";
      else if ("0xef52".equals(rv))
        return "EXT2";
      else if ("0xef53".equals(rv))
        return "EXT3";
      else if ("0x4244".equals(rv))
        return "HFS";
      else if ("0x4d44".equals(rv))
        return "MS-DOS";
      else if ("0x6969".equals(rv))
        return "NFS";
      else if ("0x5346544e".equals(rv))
        return "NTFS";
      else if ("0x52654973".equals(rv))
        return "ReiserFS";
      else if ("0x517b".equals(rv))
        return "SMB";
      else if ("0x9fa2".equals(rv))
        return "USB";
      else if ("0x58465342".equals(rv))
        return "XFS";
      else if ("0x1021994".equals(rv))
        return "TMPFS";
    }
    return rv;
  }
  public static native String getFileSystemType(String root);
  static native void setSystemTime(int year, int month, int day, int hour, int minute, int second, int millisecond);
  // setSystemTime0 is NOT implemented on Windows, only on Linux
  static native void setSystemTime0(long timems);
  private static native void postMessage0(long winID, int msg, int param1, int param2);
  public static native int getFileSystemIdentifier(String root);
  private static native int connectToInternet0();
  private static native void disconnectInternet0();
  protected static native boolean setupSystemHooks0(long systemWinHandle);
  protected static native boolean releaseSystemHooks0(long systemWinHandle);
  static native Rectangle getScreenArea0();

  private static int NUM_OUTPUT_FILES = 3;
  private static final int DID_CONNECT = 0x1;
  private static final int CONNECT_ERR = 0x2;

  public static long getDiskFreeSpace(String disk)
  {
    // Leave 2 GB as room for error and variable bit rate
    //return Math.max(0, getDiskFreeSpace0(disk) - 2000000000L);
    // Fix the pathname for UNC paths
    if (disk.toString().startsWith("\\\\") && (disk.charAt(disk.length() - 1) != '\\'))
    {
      disk += "\\";
    }
    return Math.max(0, getDiskFreeSpace0(disk));
  }
  public static long getDiskTotalSpace(String disk)
  {
    if (disk.toString().startsWith("\\\\") && (disk.charAt(disk.length() - 1) != '\\'))
    {
      disk += "\\";
    }
    return Math.max(0, getDiskTotalSpace0(disk));
  }
  private static boolean createdDialup = false;

  // return is success/failure
  static boolean connectToInternet()
  {
    // This can pop up a UI, so don't let it disturb our display
    int dialRes = connectToInternet0();
    createdDialup |= ((dialRes & DID_CONNECT) != 0);
    boolean err = ((dialRes & CONNECT_ERR) != 0);
    return !err;
  }
  // This is safe to call whether a dial up connection was created or not
  static void disconnectInternet()
  {
    if (createdDialup)
    {
      disconnectInternet0();
      createdDialup = false;
    }
  }
  static long stdOutHandle;
  static void printlnx(String s)
  {
    println0(stdOutHandle, s);
  }

  private static boolean threadDebug = false;

  public static void printStackTrace(Throwable t)
  {
    System.out.println(t);
    StackTraceElement[] stack = t.getStackTrace();
    for (int i = 0; i < stack.length; i++)
      System.out.println(stack[i].toString());
  }

  /*
    paths used by Mac OS X currently:
      "core" = server files, read-only, version dependent, not user modifiable
      "data" = settings, database, other files maintained by the server
      "tools" = helper tools used by the server (eg: ffmpeg, jpegtran, etc..)
      "logs" = where log files go
      "plugins" = third party add-ons, like IR blaster, new hardware, etc...
      "cache" - where generated thumbnail files go

    The paths are determined at startup and set when the JVM is loaded via System.setProperty.
    Each path is prepended with sage.paths. eg: "core" is specified as sage.paths.core
    In the absense of these properties, we'll always fall back on "user.dir"
   */
  public static String getPath(String which)
  {
    String defaultPath = System.getProperty("user.dir");
    String propPath = System.getProperty("sage.paths."+which, defaultPath);

    // slap a separator on the end since we'll be referencing something inside this directory
    propPath += System.getProperty("file.separator");

    return propPath;
  }

  public static String getPath(String which, String name)
  {
    return getPath(which) + name;
  }

  public static String getLogPath(String name)
  {
    String path = System.getProperty("sage.paths.logs", null);
    if(path == null || path.length() == 0)
      path = get("logging_dir", null);    // fall back on Sage.properties if there
    if(path == null || path.length() == 0)
      path = System.getProperty("user.dir");
    else
      new File(path).mkdirs();

    path += System.getProperty("file.separator");
    if(name != null) path += name;

    return path;
  }

  // returns the path to the specified helper tool, eg: ffmpeg or jpegtran
  // on Windows, the .exe extension will be added automatically
  public static String getToolPath(String name)
  {
    String toolPath = getPath("tools");
    toolPath += name;
    if(WINDOWS_OS) toolPath += ".exe";
    return toolPath;
  }

  private static String logFileDir;
  private static long logFileRolloverSize;
  private static boolean loggingFlushEachEntry;
  private static boolean linuxConsumeStdinout;

  // IMPORTANT NOTE: do NOT perform any property retrieval in this function or it can deadlock against the savePrefs function
  private static void rolloverLogs(final String prefix)
  {
    for (int i = NUM_OUTPUT_FILES - 1; i >= 0; i--)
    {
      File srcFile = new File(logFileDir, prefix + "_" + i + ".txt");
      File destFile = new File(logFileDir, prefix + "_" + (i + 1) + ".txt");
      if (!srcFile.isFile()) continue;
      if (destFile.isFile() && !destFile.delete()) continue;
      srcFile.renameTo(destFile);
    }
    PrintStream redir = null;
    final File outputFile = new File(logFileDir, prefix + "_0.txt");

    if(Sage.LINUX_OS && linuxConsumeStdinout)
    { // For Linux daemon mode
      try
      {
        redir = new PrintStream(new BufferedOutputStream(
            new FileOutputStream(outputFile)), loggingFlushEachEntry)
        {
          public synchronized void print(String s)
          {
            super.print(df() + ' ' + (threadDebug ? ("[" + Thread.currentThread().getName() + "@" + Integer.toString(Thread.currentThread().hashCode(), 16) + "] ") : "") + s);
          }
          public synchronized void println(String s)
          {
            super.println(s);
            if (logFileRolloverSize > 0 && outputFile.length() > logFileRolloverSize)
            {
              try
              {
                close();
              }
              catch (Exception e){}
              rolloverLogs(prefix);
            }
          }
        };
        if (!TESTING)
        {
          System.out.close();
          System.err.close();
          System.setOut(redir);
          System.setErr(redir);
        }
      }
      catch (IOException e)
      {
        System.err.println("ERROR redirecting:" + e);
        return;
      }
    }
    else
    {
      try
      {
        redir = new PrintStream(new BufferedOutputStream(
            new FileOutputStream(outputFile)), loggingFlushEachEntry)
        {
          public synchronized void print(String s)
          {
            s = df() + ' ' + (threadDebug ? ("[" + Thread.currentThread().getName() + "@" + Integer.toString(Thread.currentThread().hashCode(), 16) + "] ") : "") + s;
            super.print(s);
            if (stdOutHandle == 0)
              System.err.print(s);
            else
              Sage.printlnx(s);
          }
          public synchronized void println(String s)
          {
            super.println(s);
            if (stdOutHandle == 0)
              System.err.println();
            else
              Sage.printlnx("\r\n");
            if (logFileRolloverSize > 0 && outputFile.length() > logFileRolloverSize)
            {
              try
              {
                close();
              }
              catch (Exception e){}
              rolloverLogs(prefix);
            }
          }
        };
        if (!TESTING)
        {
          System.setOut(redir);
          if (stdOutHandle != 0)
            System.setErr(redir);
        }
      }
      catch (IOException e)
      {
        System.err.println("ERROR redirecting:" + e);
        return;
      }
    }
  }

  public static class MyPrinter extends PrintStream
  {
    public MyPrinter(String p, File f, BufferedOutputStream bos, boolean b)
    {
      super(bos, b);
      outputFile = f;
      prefix = p;
    }
    public synchronized void print(String s)
    {
      s = df() + ' ' + (threadDebug ? ("[" + Thread.currentThread().getName() + "-" + Thread.currentThread().getId() + "] ") : "") + s;
      super.print(s);
      System.err.print(s);
      if (logFileRolloverSize > 0 && outputFile.length() > logFileRolloverSize)
      {
        try
        {
          close();
        }
        catch (Exception e){}
        rolloverLogs(prefix);
      }
      // It's faster for us to do it here then to set it for autoflush since that scans for \n chars in the buffer which is slow
      else if (loggingFlushEachEntry)
        super.flush();
    }
    public synchronized void println(String s)
    {
      s = df() + ' ' + (threadDebug ? ("[" + Thread.currentThread().getName() + "-" + Thread.currentThread().getId() + "] ") : "") + s + ("\r\n");
      super.print(s);
      System.err.print(s);
      if (logFileRolloverSize > 0 && outputFile.length() > logFileRolloverSize)
      {
        try
        {
          close();
        }
        catch (Exception e){}
        rolloverLogs(prefix);
      }
      // It's faster for us to do it here then to set it for autoflush since that scans for \n chars in the buffer which is slow
      else if (loggingFlushEachEntry)
        super.flush();
    }
    private File outputFile;
    private String prefix;
  }

  static void setupRedirections(String prefix)
  {
    logFileRolloverSize = Sage.getLong("logfile_rollover_size", 10000000);
    loggingFlushEachEntry = getBoolean("logging_flush_each_entry", true);
    linuxConsumeStdinout = getBoolean("linux/consume_main_stdout_stdin", true);
    NUM_OUTPUT_FILES = Math.max(2, getInt("num_logfiles_to_keep", 4)) - 1;
    logFileDir = getLogPath(null);
    if (DBG || !Sage.WINDOWS_OS)
    {
      rolloverLogs(prefix);
    }
    else if (stdOutHandle != 0)
    {
      PrintStream redir = null;
      redir = new PrintStream(new OutputStream()
      {
        public void write(int b) {}
      }, true)
      {

        public synchronized void print(String s)
        {
          Sage.printlnx(df() + ' ' + (threadDebug ? ("[" + Thread.currentThread().getName() + "@" + Integer.toString(Thread.currentThread().hashCode(), 16) + "] ") : "") + s);
        }
        public synchronized void println(String s)
        {
          super.println(s);
          Sage.printlnx("\r\n");
        }
      };

      if (!TESTING)
      {
        System.setOut(redir);
        System.setErr(redir);
      }
    }
  }

  public static boolean isHeadless() {
    return Boolean.getBoolean("java.awt.headless")
        || (mainHwnd == 0 && WINDOWS_OS) ||
        getBoolean("force_headless_mode", false);
  }

  static boolean systemStartup = false;
  public static boolean client = false;
  public static String preferredServer = null;
  public static boolean autodiscoveredServer = false;
  static String initialSTV = null;
  public static boolean nonLocalClient = false;

  static SpecialWindow masterWindow;

  public static void main(String[] args) throws Throwable
  {
    System.out.println("Main is starting");
    startup(args);
  }

  public static void b(String[] args) throws Throwable
  {
      startup(args);
  }

  static void startup(String[] args) throws Throwable
  {
    try
    {

      byte[] magicKey = { // The first 16+8 bytes are the blowfish key used for c/s encryption, don't change it
          // if you're doing a new encryption key for the DB, just change everything else
          -94, -34, -9, -31, -120, 35, 59, -114, -124, -101, 77, 1, 22, -89, 121, -118,
          126, 95, 106, -110, -55, 74, -60, -127,
          107, -105, 104, -24, 113, 19, -89, -36, -105, 27, -105,
          -117, -11, 80, -106, 126, 42, -4, 81, 52, -115, 20, 57, -53, -83, -65, 20, 69,
          15, -104, -48, -72, -6, 83, -123, 98, 46, 62, 127, -62, 86, 21, 25, 81, 57, 34,
          25, 57, -121, 8, 56, 8, -121, -67, -106, -60, -56, 37, -63, -102, 109, -117, 49, 113, -104,
          -107, 17, -16, -1, -73, 109, 8, -64, 37, -19, -17, 2, 46, -113, -114, 37, 105,
          -11, -43, -56, -66, 60, 10, 39, 64, 116, 19, -27, 5, 76, 23, -76, -70, -65, 45, -27, 87, 6, -57 };
      q = magicKey;
      mainHwnd = Long.parseLong(args[0]);
      stdOutHandle = Long.parseLong(args[1]);
      int idx = args[3].indexOf(' ');
      String prefFilename = "";
      if (idx != -1)
      {
        appName = args[3].substring(0, idx);
        prefFilename = args[3].substring(idx + 1);
      }
      else
        throw new IllegalArgumentException("Invalid sage arguments.");

      // Special executions for other stuff

      String sysCmdArgs = args[2];

      StringTokenizer argToker = new StringTokenizer(sysCmdArgs, " ");
      systemStartup = false;
      client = false;
      while (argToker.hasMoreTokens())
      {
        String str = argToker.nextToken();
        if (str.equals("-startup"))
          systemStartup = true;
        else if (str.equals("-connect") && argToker.hasMoreTokens())
          preferredServer = argToker.nextToken();
        else if (str.equals("-stv") && argToker.hasMoreTokens())
        {
          initialSTV = argToker.nextToken();
          if (initialSTV.startsWith("\""))
          {
            initialSTV = initialSTV.substring(1);
            while (!initialSTV.endsWith("\"") && argToker.hasMoreTokens())
            {
              initialSTV += " " + argToker.nextToken();
            }
            if (initialSTV.endsWith("\""))
              initialSTV = initialSTV.substring(0, initialSTV.length() - 1);
          }
        }
        else if (str.equals("-properties") && argToker.hasMoreTokens())
        {
          String cmdPrefFilename = null;
          cmdPrefFilename = argToker.nextToken();
          if (cmdPrefFilename.startsWith("\""))
          {
            cmdPrefFilename = cmdPrefFilename.substring(1);
            while (!cmdPrefFilename.endsWith("\"") && argToker.hasMoreTokens())
            {
              cmdPrefFilename += " " + argToker.nextToken();
            }
            if (cmdPrefFilename.endsWith("\""))
              cmdPrefFilename = cmdPrefFilename.substring(0, cmdPrefFilename.length() - 1);
          }
          prefFilename = cmdPrefFilename;
        }
        else if (str.equals("-clockoffset") && argToker.hasMoreTokens())
        {
          try
          {
            globalTimeOffset = Long.parseLong(argToker.nextToken());
          }
          catch (NumberFormatException nfe)
          {
            throw new IllegalArgumentException("Invalid formatting of the -clockoffset option...it must be a long integer.");
          }
        }
      }
      if (appName.equals("sagetvclient"))
      {
        appName = "sagetv";
        client = true;
      }

      mainThread = Thread.currentThread();
      prefs = new SageProperties(client);
      prefs.setupPrefs(prefFilename, Sage.getPath("core","Sage.properties.defaults"));
      alwaysUseCPUTime = Sage.getBoolean("always_use_cpu_time", false);
      //DBG = getBoolean("debug_logging", true);

      if(DBG) dl = 1; // NOTE: exposed to native code to enable excessive debug logging
      setupRedirections(client ? "sagetvclient" : appName);
      threadDebug = getBoolean("thread_debug", true);
      if (false/*Sage.getBoolean("ui/accelerated_rendering", true)*/)
      {
        System.setProperty("sun.java2d.accthreshold", "0");
        System.setProperty("sun.java2d.translaccel", "true");
        // I'm not sure if I want to change how ddforcevram is used
        System.setProperty("sun.java2d.ddforcevram", "true");
        System.setProperty("sun.java2d.ddscale", "true");
        System.setProperty("sun.java2d.trace", "log,count,verbose");
      }
      // Disable accelerated offscreen images in Java, I do this all automatically....but
      // this might not be good for Swing
      System.setProperty("sun.java2d.ddoffscreen", "false");
      System.setProperty("sun.java2d.noddraw", "true"); // needed for knowing how much available VRAM
      System.setProperty("sun.java2d.d3d", "false"); // needed for JRE1.6
      System.setProperty("sun.awt.nopixfmt", "false"); // required if running as a service

      // For linux we'd use
      //-Dsun.java2d.pmoffscreen=true/false

      String myTimeZone = get("time_zone", "");
      if (myTimeZone.length() > 0)
      {
        TimeZone tz = null;
        try {
          tz = new TimeZoneParse(myTimeZone).parse().convertToTz();
          if (DBG) System.out.println("Parsed TZ format:" + myTimeZone + " to: " + tz);
        } catch (ParseException e) {
          if(DBG) System.out.println("Error processing TZ:" + myTimeZone + " threw:" + e);
        }
        if(tz == null) {
          tz = TimeZone.getTimeZone(myTimeZone);
        }
        TimeZone.setDefault(tz);
        if (DBG) System.out.println("Changed default timezone to:" + tz.getDisplayName());
        DF.setTimeZone(tz);
        DF_CLEAN.setTimeZone(tz);
        DF_FULL.setTimeZone(tz);
        DF_LITTLE.setTimeZone(tz);
      }

      String preferredLanguage = Sage.get("ui/translation_language_code", "");
      String preferredCountry = Sage.get("ui/translation_country_code", "");
      if (preferredLanguage.length() > 0)
        userLocale = new Locale(preferredLanguage, preferredCountry);
      else
        userLocale = Locale.getDefault();
      if (TESTING)
      {
        coreRez = new ResourceBundle()
        {
          Vector<String> keys = new Vector<String>();
          @Override
          protected Object handleGetObject(String s)
          {
            keys.add(s);
            return s;
          }

          @Override
          public Enumeration<String> getKeys()
          {
            return keys.elements();
          }
        };
      } else
      {
        coreRez = ResourceBundle.getBundle("SageTVCoreTranslations", userLocale);
      }
      createDFFormats();
      UserEvent.updateNameMaps();

      if (DBG) System.out.println("user.dir2=" + System.getProperty("user.dir"));
      if (DBG) System.out.println("classpath=" + System.getProperty("java.class.path"));
      if (DBG) System.out.println("JVM version=" + System.getProperty("java.version"));
      if (DBG) System.out.println("OS=" + System.getProperty("os.name") + " " + System.getProperty("os.version"));
      if (DBG) System.out.println("client=" + client);
      if (DBG) System.out.println("locale=" + userLocale);
      Sage.putBoolean("client", client);
      long startupDelay = Sage.getLong("startup_delay", 0);
      if (startupDelay > 0)
      {
        try{Thread.sleep(startupDelay);}catch(Exception e){}
      }

      if (appName.equals("sagetv") && !isHeadless() && Sage.WINDOWS_OS)
        Sage.setupSystemHooks0(Sage.mainHwnd);

      exitingJARFiles = new HashSet<String>();
      File[] jarFiles = new File("JARs").listFiles();
      List<URL> jarurls = new ArrayList<URL>(jarFiles == null ? 0 : jarFiles.length);
      for (int i = 0; jarFiles != null && i < jarFiles.length; i++)
      {
        String path = jarFiles[i].getAbsolutePath();
        if (path.toLowerCase().endsWith(".jar"))
        {
          exitingJARFiles.add(path);
        }
      }
      extClassLoader = new MyURLClassLoader(jarurls.toArray(new URL[0]));
      if (!isHeadless() && (!systemStartup || !appName.equals("sagetv") ||
          Sage.getInt("ui/startup_type", 0) != 2) &&
          !Sage.getBoolean("ui/windowless", false))
      {
        masterWindow = new SpecialWindow(appName.equals("recorder") ? "SageTV Recorder Video" :
          (isTrueClient() ? (Sage.rez("SageTV") + " Client") : Sage.rez("SageTV")), getInt("ui/window_title_style", Sage.VISTA_OS ? SageTVWindow.PLATFORM_TITLE_STYLE : 0));
        splashAndLicense();
      }

      if (Sage.DBG)
      {
        new ThreadMonitor(Sage.getLong("thread_cpu_monitor_interval", 5 * MILLIS_PER_MIN));
      }
      if (!TESTING)
        new SageTV();
    }
    catch (Throwable t)
    {
      System.err.println("MAIN THREW AN EXCEPTION: " + t);
      t.printStackTrace();
      throw t;
    }
  }


  public static boolean isTrueClient()
  {
    return client && !"localhost".equals(Sage.preferredServer) && !"127.0.0.1".equals(Sage.preferredServer);
  }
  // This is true for SageTVClient instances that are connected to a server at a different machine....this is slightly different
  // than the isTrueClient functionality because this one detects when the same IP is used. This is for protection relating to
  // plugin installations...we didn't want to change isTrueClient's functionality since that one is intended to detect when SageTV.exe is running as a client instead
  public static boolean isNonLocalClient()
  {
    return nonLocalClient;
  }
  public static void updateJARLoader()
  {
    File[] jarFiles = new File("JARs").listFiles();
    for (int i = 0; i < jarFiles.length; i++)
    {
      String path = jarFiles[i].getAbsolutePath();
      if (path.toLowerCase().endsWith(".jar"))
      {
        if (exitingJARFiles.add(path))
        {
          try
          {
            if (Sage.DBG) System.out.println("Added JAR file to search path of: " + jarFiles[i].getAbsolutePath());
            extClassLoader.addNewURL(jarFiles[i].toURI().toURL());
          }
          catch (MalformedURLException mue)
          {
            System.out.println("INVALID JAR URL, error:" + mue);
          }
        }
      }
    }
  }
  private static Set<String> exitingJARFiles;
  public static MyURLClassLoader extClassLoader;
  private static class MyURLClassLoader extends URLClassLoader
  {
    public MyURLClassLoader(URL[] urls)
    {
      super(urls);
    }
    public void addNewURL(URL url)
    {
      addURL(url);
    }
  }

  private static Window splashWindow;
  private static Label splashText;
  private static void splashAndLicense()
  {
    splashWindow = new Window(masterWindow);
    splashWindow.setLayout(new BorderLayout());
    Image theImage = null;
    String splashImageName;
    if (Sage.get("ui/splash_image", null) != null)
    {
      theImage = Toolkit.getDefaultToolkit().createImage(Sage.get("ui/splash_image", null));
      ImageUtils.ensureImageIsLoaded(theImage);
    }
    else
    {
      theImage = ImageUtils.fullyLoadImage(isTrueClient() ? "images/splashclient.gif" : "images/splash.gif");
    }
    ActiveImage splashImage = new ActiveImage(theImage);
    splashWindow.add(splashImage, "Center");
    splashText = new Label(Sage.rez("Module_Init", new Object[] { "Application" }), Label.CENTER)
    {
      public void paint(Graphics g)
      {
        super.paint(g);
        g.setColor(Color.black);
        g.drawRect(0, 0, getWidth()-1, getHeight()-1);
      }
    };
    splashText.setBackground(new Color(42, 103, 190));
    splashText.setForeground(Color.white);
    splashWindow.add(splashText, "South");
    Dimension scrSize = Toolkit.getDefaultToolkit().getScreenSize();
    splashWindow.pack();
    Point center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
    splashWindow.setLocation(center.x - splashWindow.getWidth()/2, center.y - splashWindow.getHeight()/2);
    splashWindow.setVisible(true);
  }

  private static Object splashLock = new Object();
  static void hideSplash()
  {
    synchronized (splashLock)
    {
      if (splashWindow != null)
      {
        splashWindow.setVisible(false);
        splashWindow.removeAll();
        splashWindow.setBounds(0, 0, 0, 0);
        splashWindow.dispose();
        splashWindow = null;
        splashText = null;
      }
    }
  }

  static void setSplashText(String x)
  {
    Label tempL = splashText;
    if (tempL != null)
      tempL.setText(x);
    if (Sage.DBG) System.out.println("Splash: " + x);
    SageTV.writeOutWatchdogFile();
  }

  public static void setLanguageCode(String x, String countryCode)
  {
    Locale defaultLocale = Locale.getDefault();

    // Don't set the language for English, just use the default locale or we'll unlocalize things in languages we don't support
    // UPDATE: 4/3/06 - If a CoreTranslations properties file exists in the system default locale, then allow setting the
    // language into English. Otherwise set it to "".
    if ("en".equals(x))
    {
      if (new File("SageTVCoreTranslations_" + defaultLocale.getLanguage() + ".properties").isFile() ||
          new File("SageTVCoreTranslations_" + defaultLocale.getLanguage() + "_" +
              defaultLocale.getCountry() + ".properties").isFile())
        x = "en";
      else
        x = "";
    }
    if (x.equals(Sage.get("ui/translation_language_code", "")) && countryCode.equals(Sage.get("ui/translation_country_code", ""))) return;

    if (Sage.DBG) System.out.println("Changing UI language to be: " + x);
    Sage.put("ui/translation_language_code", x);
    String preferredCountry = countryCode;
    Sage.put("ui/translation_country_code", countryCode);
    // Check if the language is different than the default locale. If it's not; then use the default locale.
    if (x.length() > 0 && (!defaultLocale.getLanguage().equals(x) || !defaultLocale.getCountry().equals(countryCode)))
      userLocale = new Locale(x, preferredCountry);
    else
      userLocale = Locale.getDefault();
    coreRez = ResourceBundle.getBundle("SageTVCoreTranslations", userLocale);
    msgFormatRezMap.clear();

    createDFFormats();

    // There's one DB object which has a language dependent attribute we need to translate
    Wizard.getInstance().updateNoDataLang();

    // The role names are in a static array we need to fix
    System.arraycopy(Show.getRoleNames(), 0, Show.ROLE_NAMES, 0, Show.ROLE_NAMES.length);
    // The localized string for checking if the category is a movie needs to be updated.
    Show.movieString = Sage.rez("Movie");

    UserEvent.updateNameMaps();

    Iterator<UIManager> walker = UIManager.getUIIterator();
    while (walker.hasNext())
    {
      UIManager currUI = walker.next();
      currUI.getModuleGroup().retranslate();
      currUI.fullyRefreshCurrUI();
    }
  }

  public static void setTimeZone(String x)
  {
    String myTimeZone = get("time_zone", "");
    if (!myTimeZone.equals(x))
    {
      put("time_zone", x);
      myTimeZone = x;

      TimeZone tz = null;
      try {
        tz = new TimeZoneParse(myTimeZone).parse().convertToTz();
        if (DBG) System.out.println("Parsed TZ format:" + myTimeZone + " to: " + tz);
      } catch (ParseException e) {
        if(DBG) System.out.println("Error processing TZ:" + myTimeZone + " threw:" + e);
      }
      if(tz == null) {
        tz = TimeZone.getTimeZone(myTimeZone);
      }
      TimeZone.setDefault(tz);
      if (DBG) System.out.println("Changed default timezone to:" + tz.getDisplayName());

      // We now recreate the Locale so it's a new object and anything that's dependent upon
      // it will get recalculated then.
      userLocale = new Locale(userLocale.getLanguage(), userLocale.getCountry());

      createDFFormats();
      sage.epg.sd.SDUtils.resetTimeZoneOffset();

      // We need to re-thread this because otherwise we could deadlock on the server side waiting
      // for the quanta update to return from an API request made in the UI as part of refreshing this
      // which was originally triggered by the time zone change in the gfiber properties sent over.
      Pooler.execute(new Runnable()
      {
        public void run()
        {
          // Just refresh the UI fully, we don't need to retranslate all of the widgets
          Iterator<UIManager> walker = UIManager.getUIIterator();
          while (walker.hasNext())
          {
            UIManager theUI = walker.next();
            theUI.fullyRefreshCurrUI();
          }
        }
      });
    }
  }

  // For message formatting
  private static Map<String, MessageFormat> msgFormatRezMap = new HashMap<String, MessageFormat>();
  public static String rez(String rezName, Object[] vars)
  {
    MessageFormat mf = msgFormatRezMap.get(rezName);
    if (mf == null)
    {
      msgFormatRezMap.put(rezName, mf = new MessageFormat(rez(rezName), userLocale));
    }
    String rv;
    try
    {
      synchronized (mf)
      {
        rv = mf.format(vars);
      }
    }
    catch (Exception e)
    {
      System.out.println("ERROR with Sage.rez string=" + rezName + " Error=" + e);
      rv = rezName;
    }
    return rv;
  }
  public static String rez(String rezName)
  {
    // 601 patch
    if (rezName == null) return (null);
    if (rezName.length() == 0) return rezName;
    try
    {
      return coreRez.getString(rezName);
    }
    catch (MissingResourceException e)
    {
      //if (DBG) System.out.println("WARNING - MissingResource: \"" + rezName + "\"");
      return rezName;
    }
  }

  public static void backupPrefs(String backupExtension)
  {
    prefs.backupPrefs(backupExtension);
  }
  static Properties getAllPrefs()
  {
    return prefs.getAllPrefs();
  }

  public static int getInt(String name, int d)
  {
    return prefs.getInt(name, d);
  }

  public static boolean getBoolean(String name, boolean d)
  {
    return (prefs == null) ? d : prefs.getBoolean(name, d);
  }

  public static long getLong(String name, long d)
  {
    return prefs.getLong(name, d);
  }

  public static float getFloat(String name, float d)
  {
    return prefs.getFloat(name, d);
  }

  public static String get(String name, String d)
  {
    return (prefs == null) ? d : prefs.get(name, d);
  }

  public static void putInt(String name, int x)
  {
    prefs.putInt(name, x);
  }

  public static void putBoolean(String name, boolean x)
  {
    prefs.putBoolean(name, x);
  }

  public static void putLong(String name, long x)
  {
    prefs.putLong(name, x);
  }

  public static void putFloat(String name, float x)
  {
    prefs.putFloat(name, x);
  }

  public static void put(String name, String x)
  {
    prefs.put(name, x);
  }

  public static void remove(String name)
  {
    prefs.remove(name);
  }

  public static String[] childrenNames(String name)
  {
    return prefs.childrenNames(name);
  }

  public static String[] keys(String name)
  {
    return prefs.keys(name);
  }

  public static void removeNode(String name)
  {
    prefs.removeNode(name);
  }

  public static void savePrefs()
  {
    prefs.savePrefs();
  }

  // For using this as a default for a UI property set
  public static SageProperties getRawProperties() { return prefs; }

  private static SageProperties prefs;
  static void postKillMsg() { postMessage0(mainHwnd, 0x400 + 667, 0, 0); }
  private static final int DUMMY_MSG = 0;
  private static final int START_ENC_MSG = 1;
  private static final int START_PRV_MSG = 2;
  private static final int SWITCH_ENC_MSG = 3;
  private static final int STOP_ENC_MSG = 4;
  private static final int TUNE_MSG = 5;
  private static final int AUTOTUNE_MSG = 6;
  private static final int SWITCH_CONN_MSG = 7;
  private static final int PLAY_FILE_MSG = 8;
  private static final int SEEK_FILE_MSG = 9;
  private static final int STOP_FILE_MSG = 10;
  private static final int RESIZE_VIDEO_MSG = 11;
  private static final int SIGNAL_STRENGTH_MSG = 12;
  private static final int AUTOSCAN_MSG = 13;
  private static final int AUTOSCAN_MSG_INFO = 14;
  private static final int SETUPHOOKS_MSG = 15;
  private static class EncodingMsg
  {
    public EncodingMsg(DShowCaptureDevice inCapDev, int inType)
    { capDev = inCapDev; msgType = inType; }
    public EncodingMsg(DShowCaptureDevice inCapDev, int inType, String f)
    {
      capDev = inCapDev;
      msgType = inType;
      filename = channel = f;
    }
    public DShowCaptureDevice capDev;

    public EncodingMsg(int inType)
    {
      msgType = inType;
    }
    public EncodingMsg(int inType, MediaPlayer mPlayer, long inTime)
    {msgType = inType; player = mPlayer; time = inTime; }
    public EncodingMsg(int inType, MediaPlayer mPlayer, Rectangle sr,
        Rectangle dr, boolean hc)
    {msgType = inType; player = mPlayer; destRect = dr; srcRect = sr; hideCursor = hc; }
    public String filename;
    public String channel;
    public boolean rv;
    public int rvInt;
    public String rvStr;
    public int msgType;
    public Exception except;
    public MediaPlayer player;
    public long time;
    public Rectangle destRect;
    public Rectangle srcRect;
    public boolean hideCursor;
    public VideoFrame vf;
  }
  // This is -1 so that the launcher is essential for the program to operate; then we just
  // need to piracy protect the launcher
  static long mainHwnd = -1;
  static String appName;
  private static Vector<EncodingMsg> msgQueue = new Vector<EncodingMsg>();

  static void startEncoding(DShowCaptureDevice capDev, String filename) throws EncodingException
  {
    EncodingMsg newMsg = new EncodingMsg(capDev, START_ENC_MSG, filename);
    if (!submitMsg(newMsg))
      capDev.startEncodingSync(filename);
    if (newMsg.except != null) throw (EncodingException) newMsg.except;
  }

  static void switchEncoding(DShowCaptureDevice capDev, String filename) throws EncodingException
  {
    EncodingMsg newMsg = new EncodingMsg(capDev, SWITCH_ENC_MSG, filename);
    if (!submitMsg(newMsg))
      capDev.switchEncodingSync(filename);
    if (newMsg.except != null) throw (EncodingException) newMsg.except;
  }

  static void stopEncoding(DShowCaptureDevice capDev)
  {
    if (!submitMsg(new EncodingMsg(capDev, STOP_ENC_MSG)))
      capDev.stopEncodingSync();
  }

  static void tuneToChannel(DShowCaptureDevice capDev, String num)
  {
    EncodingMsg newMsg = new EncodingMsg(capDev, TUNE_MSG, num);
    if (!submitMsg(newMsg))
      capDev.tuneToChannelSync(num);
  }

  static boolean autoTuneChannel(DShowCaptureDevice capDev, String num)
  {
    EncodingMsg newMsg = new EncodingMsg(capDev, AUTOTUNE_MSG, num);
    if (!submitMsg(newMsg))
      return capDev.autoTuneChannelSync(num);
    else return newMsg.rv;
  }

  static boolean autoScanChannel(DShowCaptureDevice capDev, String num)
  {
    EncodingMsg newMsg = new EncodingMsg(capDev, AUTOSCAN_MSG, num);
    if (!submitMsg(newMsg))
    {
      String str = capDev.autoScanChannelSync(num);
      return (str != null && str.length() > 0) ? true : false;
    }
    else return newMsg.rv;
  }

  static String autoScanChannelInfo(DShowCaptureDevice capDev, String num)
  {
    EncodingMsg newMsg = new EncodingMsg(capDev, AUTOSCAN_MSG_INFO, num);
    if (!submitMsg(newMsg))
      return capDev.autoScanChannelSync(num);
    else return newMsg.rvStr;
  }

  static int getSignalStrength(DShowCaptureDevice capDev)
  {
    EncodingMsg newMsg = new EncodingMsg(capDev, SIGNAL_STRENGTH_MSG);
    if (!submitMsg(newMsg))
      return capDev.getSignalStrengthSync();
    else return newMsg.rvInt;
  }

  static void switchToConnector(DShowCaptureDevice capDev) throws EncodingException
  {
    EncodingMsg newMsg = new EncodingMsg(capDev, SWITCH_CONN_MSG);
    if (!submitMsg(newMsg))
      capDev.switchToConnectorSync();
    if (newMsg.except != null) throw (EncodingException) newMsg.except;
  }

  static void seek(VideoFrame vf, MediaPlayer mPlayer, long time) throws PlaybackException
  {
    EncodingMsg newMsg = new EncodingMsg(SEEK_FILE_MSG, mPlayer, time);
    newMsg.vf = vf;
    if (!submitMsg(newMsg))
      vf.asyncSeek(mPlayer, time);
    if (newMsg.except != null) throw (PlaybackException) newMsg.except;
  }

  static void stop(DShowMediaPlayer mPlayer)
  {
    EncodingMsg newMsg = new EncodingMsg(STOP_FILE_MSG, mPlayer, 0);
    if (!submitMsg(newMsg))
      mPlayer.asyncStop();
  }

  static void setVideoRectangles(MediaPlayer mp, Rectangle videoSrcRect, Rectangle videoDestRect, boolean hideCursor)
  {
    EncodingMsg newMsg = new EncodingMsg(RESIZE_VIDEO_MSG, mp, videoSrcRect, videoDestRect, hideCursor);
    if (!submitMsg(newMsg))
      mp.setVideoRectangles(videoSrcRect, videoDestRect, hideCursor);
  }

  static void setupHooksSync()
  {
    EncodingMsg newMsg = new EncodingMsg(SETUPHOOKS_MSG);
    if (!submitMsg(newMsg))
      Sage.setupSystemHooks0(Sage.mainHwnd);
  }

  // 5/22/09 - Narflex - I've added this new msgThread to fix a problem on Vista that we found
  // where the app would hang unloading the capture graph sometimes. It would only happen in service
  // mode or when entering standby in non-service mode. The conditional below maps to that perfectly.
  // In testing we found out that if we just used the same thread all the time to do the capture device calls
  // like we do in non-service mode then the hangs went away. So now we process those messages in a single thread
  // so its consistent no matter who the caller is.
  private static Thread msgThread = null;
  private static boolean submitMsg(EncodingMsg newMsg)
  {
    if (!DISABLE_MSG_LOOP_FOR_JPROFILER && mainHwnd != 0 && Thread.currentThread() != mainThread && !SageTV.isPoweringDown())
    {
      synchronized (msgQueue)
      {
        msgQueue.addElement(newMsg);
        postMessage0(mainHwnd, 0x400 + 666, 0, 0);
        // Wait for the message to be consumed
        while (msgQueue.contains(newMsg))
        {
          try{msgQueue.wait(1000);}catch(InterruptedException e){}
        }
      }
      return true;
    }
    else
    {
      synchronized (msgQueue)
      {
        msgQueue.addElement(newMsg);
        // Wait for the message to be consumed
        if (msgThread == null)
        {
          msgThread = new Thread("MainMsg")
          {
            public void run()
            {
              while (true)
              {
                synchronized (msgQueue)
                {
                  if (msgQueue.isEmpty())
                  {
                    try{
                      msgQueue.wait(5000);
                    }catch(Exception e){}
                    continue;
                  }
                  processMsg();
                }
              }
            }
          };
          msgThread.setDaemon(true);
          msgThread.start();
        }
        msgQueue.notify();
        while (msgQueue.contains(newMsg))
        {
          try{msgQueue.wait(1000);}catch(InterruptedException e){}
        }
      }
      return true;
    }
    // This still returns true even if we don't have our message loop setup because other
    // people could launch Sage without using the launcher and then bypass the
    // license keying system.
  }

  static void processMsg()
  {
    try
    {
      EncodingMsg nextMsg;
      while (!msgQueue.isEmpty())
      {
        synchronized (msgQueue)
        {
          nextMsg = msgQueue.remove(0);
          try
          {
            switch (nextMsg.msgType)
            {
              case TUNE_MSG:
                nextMsg.capDev.tuneToChannelSync(nextMsg.channel);
                break;
              case AUTOTUNE_MSG:
                nextMsg.rv = nextMsg.capDev.autoTuneChannelSync(nextMsg.channel);
                break;
              case AUTOSCAN_MSG:
                String str = nextMsg.capDev.autoScanChannelSync(nextMsg.channel);
                nextMsg.rv = (str != null && str.length() > 0) ? true : false;
                break;
              case AUTOSCAN_MSG_INFO:
                nextMsg.rvStr = nextMsg.capDev.autoScanChannelSync(nextMsg.channel);
                break;
              case SIGNAL_STRENGTH_MSG:
                nextMsg.rvInt = nextMsg.capDev.getSignalStrengthSync();
                break;
              case SWITCH_CONN_MSG:
                nextMsg.capDev.switchToConnectorSync();
                break;
              case STOP_ENC_MSG:
                nextMsg.capDev.stopEncodingSync();
                break;
              case START_ENC_MSG:
                nextMsg.capDev.startEncodingSync(nextMsg.filename);
                break;
              case SWITCH_ENC_MSG:
                nextMsg.capDev.switchEncodingSync(nextMsg.filename);
                break;
              case SEEK_FILE_MSG:
                nextMsg.vf.asyncSeek(nextMsg.player, nextMsg.time);
                break;
              case STOP_FILE_MSG:
                ((DShowMediaPlayer)nextMsg.player).asyncStop();
                break;
              case RESIZE_VIDEO_MSG:
                nextMsg.player.setVideoRectangles(nextMsg.srcRect, nextMsg.destRect, nextMsg.hideCursor);
                break;
              case SETUPHOOKS_MSG:
                Sage.setupSystemHooks0(Sage.mainHwnd);
                break;
            }
          }
          catch (Exception exc)
          {
            nextMsg.except = exc;
          }
          /**/
          msgQueue.notifyAll();
        }
      }
    }
    catch (Throwable t)
    {
      System.out.println("Error in processing:" + t);
    }
  }

  // do not remove - Jeff
  static Object q;
  static boolean j = true;
  static boolean k = true;
  static boolean z = false;
  static int w = 0;

  static void processInactiveFile(String s)
  {
    if (s == null) return;
    VideoFrame.inactiveFileAll(s);
    NetworkClient.distributeInactiveFile(s);
    HTTPLSServer.notifyOfInactiveFile(s);
  }
  static JWindow poppy;
  static void taskbarAction(boolean nature, final int mouseX, final int mouseY)
  {
    if (nature)
    {
      if (poppy != null)
      {
        if (Sage.getBoolean("dispose_windows", false))
          poppy.dispose();
        else
          poppy.setVisible(false);
      }
      //poppy = null;
      if (UIManager.getLocalUI() != null)
      {
        EventQueue.invokeLater(new Runnable()
        {
          public void run()
          {
            UIManager.getLocalUI().gotoSleep(false);
          }
        });
      }
    }
    else
    {
      if (poppy != null && poppy.isVisible())
      {
        if (Sage.getBoolean("dispose_windows", false))
          poppy.dispose();
        else
          poppy.setVisible(false);
        return;
      }
      if (DBG) System.out.println("Screen pos= " + mouseX + ", " + mouseY);
      if (poppy == null)
      {
        poppy = new JWindow();

        final JMenuItem resty = new JMenuItem(Sage.rez("Restore_SageTV"));
        final JMenuItem exity = new JMenuItem(Sage.rez("Exit_SageTV"));
        final JMenuItem cancely = new JMenuItem(Sage.rez("Cancel"));
        Container pane = poppy.getContentPane();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.add(resty);
        pane.add(new JPopupMenu.Separator());
        pane.add(exity);
        pane.add(new JPopupMenu.Separator());
        pane.add(cancely);
        resty.addMouseListener(new MouseAdapter()
        {
          public void mouseEntered(MouseEvent evt)
          {
            resty.setArmed(true);
          }
          public void mouseExited(MouseEvent evt)
          {
            resty.setArmed(false);
          }
        });
        exity.addMouseListener(new MouseAdapter()
        {
          public void mouseEntered(MouseEvent evt)
          {
            exity.setArmed(true);
          }
          public void mouseExited(MouseEvent evt)
          {
            exity.setArmed(false);
          }
        });
        cancely.addMouseListener(new MouseAdapter()
        {
          public void mouseEntered(MouseEvent evt)
          {
            cancely.setArmed(true);
          }
          public void mouseExited(MouseEvent evt)
          {
            cancely.setArmed(false);
          }
        });
        ((JComponent)pane).setBorder(BorderFactory.createLineBorder(Color.black));
        resty.addActionListener(new ActionListener()
        {
          public void actionPerformed(ActionEvent evt)
          {
            if (Sage.getBoolean("dispose_windows", false))
              poppy.dispose();
            else
              poppy.setVisible(false);
            taskbarAction(true, 0, 0);
          }
        });
        exity.addActionListener(new ActionListener()
        {
          public void actionPerformed(ActionEvent evt)
          {
            if (Sage.getBoolean("dispose_windows", false))
              poppy.dispose();
            else
              poppy.setVisible(false);
            SageTV.exit();
          }
        });
        cancely.addActionListener(new ActionListener()
        {
          public void actionPerformed(ActionEvent evt)
          {
            if (Sage.getBoolean("dispose_windows", false))
              poppy.dispose();
            else
              poppy.setVisible(false);
          }
        });
      }
      poppy.pack();
      EventQueue.invokeLater(new Runnable()
      {
        public void run()
        {
          Rectangle screenRect = getScreenArea0();
          poppy.setLocation(Math.min(screenRect.x + screenRect.width - poppy.getWidth(),
              Math.max(mouseX, screenRect.x)), Math.min(screenRect.y + screenRect.height - poppy.getHeight(),
                  Math.max(mouseY, screenRect.y)));
          poppy.setVisible(true);
          poppy.toFront();
          poppy.repaint();
        }
      });
    }
  }

  public static String[] parseIntRangeString(String s)
  {
    if ((s == null) || (s.length() == 0)) return Pooler.EMPTY_STRING_ARRAY;

    List<String> chanV = new ArrayList<String>();
    StringTokenizer toker = new StringTokenizer(s, ",");
    while (toker.hasMoreTokens())
    {
      String curr = toker.nextToken();
      int index = curr.indexOf('-');
      if (index == -1)
        chanV.add(new String(curr));
      else
      {
        try
        {
          int start = Integer.parseInt(curr.substring(0, index));
          int end = Integer.parseInt(curr.substring(index + 1));
          if (start >= end) // major-minor channel most definitely
            chanV.add(new String(curr));
          else
          {
            for (int i = start; i <= end; i++)
              chanV.add(Integer.toString(i));
          }
        }
        catch (NumberFormatException e){}
      }
    }

    String[] rv = chanV.toArray(Pooler.EMPTY_STRING_ARRAY);
    Arrays.sort(rv);
    return rv;
  }

  // well, it doesn't really do ranges anymore, like it matters anyways
  public static String createIntRangeString(String[] intString)
  {
    if ((intString == null) || (intString.length == 0)) return "";

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < intString.length; i++)
    {
      sb.append(intString[i]);
      sb.append(',');
    }
    return sb.toString();
  }

  public static Set<String> parseCommaDelimSet(String str)
  {
    StringTokenizer toker = new StringTokenizer(str, ",");
    Set<String> rv = new HashSet<String>();
    while (toker.hasMoreTokens())
      rv.add(toker.nextToken());
    return rv;
  }

  public static Set<String> parseDelimSet(String str, String delims)
  {
    StringTokenizer toker = new StringTokenizer(str, delims);
    Set<String> rv = new HashSet<String>();
    while (toker.hasMoreTokens())
      rv.add(toker.nextToken());
    return rv;
  }

  public static Set<Integer> parseCommaDelimIntSet(String str)
  {
    StringTokenizer toker = new StringTokenizer(str, ",");
    Set<Integer> rv = new HashSet<Integer>();
    while (toker.hasMoreTokens())
    {
      String s = toker.nextToken();
      try
      {
        rv.add(new Integer(s));
      }
      catch (NumberFormatException e)
      {}
    }
    return rv;
  }

  public static String createCommaDelimSetString(Set<?> set)
  {
    StringBuilder sb = new StringBuilder();
    for (Object obj : set)
    {
      sb.append(obj);
      sb.append(',');
    }
    return sb.toString();
  }

  public static String createDelimSetString(Set<?> set, String delim)
  {
    StringBuilder sb = new StringBuilder();
    for (Object obj : set)
    {
      sb.append(obj);
      sb.append(delim);
    }
    return sb.toString();
  }

  public static long convertProviderID(String str) throws NumberFormatException
  {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < str.length(); i++)
      if (Character.isLetterOrDigit(str.charAt(i)))
        sb.append(str.charAt(i));
    return Long.parseLong(sb.toString(), 36);
  }

  // Reads a line from a binary stream, terminates on '\r\n' or '\n'.
  // Yeah, it should also terminate on \r itself, but the transfer
  // protocol this is used for requires \r\n termination anyways.
  public static String readLineBytes(DataInput inStream)
      throws InterruptedIOException, IOException {

    synchronized (inStream)
    {
      StringBuilder result = new StringBuilder();
      byte currByte = inStream.readByte();
      int readTillPanic = DOS_BYTE_LIMIT;
      while (true)
      {
        if (currByte == '\r')
        {
          currByte = inStream.readByte();
          if (currByte == '\n')
          {
            return result.toString();
          }
          result.append('\r');
        }
        else if (currByte == '\n')
        {
          return result.toString();
        }
        result.append((char)currByte);
        currByte = inStream.readByte();
        if (readTillPanic-- < 0)
        {
          throw new IOException("TOO MANY BYTES RECIEVED FOR A LINE, BAILING TO PREVENT DOS ATTACK");
        }
      }
    }
  }

  private static Timer globalTimer;
  public static void addTimerTask(TimerTask addMe, long delay, long period)
  {
    if (globalTimer == null)
      globalTimer = new Timer(true);
    if (period == 0)
      globalTimer.schedule(addMe, delay);
    else
      globalTimer.schedule(addMe, delay, period);
  }

  public static void clipSrcDestRects(Rectangle2D.Float clipRect, Rectangle2D.Float srcRect,
      Rectangle2D.Float destRect)
  {
    float scaleX = destRect.width / srcRect.width;
    float scaleY = destRect.height / srcRect.height;
    if (destRect.x < clipRect.x)
    {
      float xDiff = clipRect.x - destRect.x;
      destRect.width -= xDiff;
      srcRect.x += xDiff/scaleX;
      srcRect.width -= xDiff/scaleX;
      destRect.x = clipRect.x;
    }
    if (destRect.y < clipRect.y)
    {
      float yDiff = clipRect.y - destRect.y;
      destRect.height -= yDiff;
      srcRect.y += yDiff/scaleY;
      srcRect.height -= yDiff/scaleY;
      destRect.y = clipRect.y;
    }
    if (destRect.x + destRect.width > clipRect.x + clipRect.width)
    {
      float over = (destRect.x + destRect.width) - (clipRect.x + clipRect.width);
      destRect.width -= over;
      srcRect.width -= over/scaleX;
    }
    if (destRect.y + destRect.height > clipRect.y + clipRect.height)
    {
      float over = (destRect.y + destRect.height) - (clipRect.y + clipRect.height);
      destRect.height -= over;
      srcRect.height -= over/scaleY;
    }
  }
  public static void clipSrcDestRects(Rectangle clipRect, Rectangle srcRect,
      Rectangle destRect)
  {
    int xn = srcRect.width;
    int xd = destRect.width;
    int yn = srcRect.height;
    int yd = destRect.height;
    if (xn == 0 || xd == 0 || yn == 0 || yd == 0)
    {
      srcRect.width = destRect.width = srcRect.height = destRect.height = 0;
      return;
    }
    if (destRect.x < clipRect.x)
    {
      int xDiff = clipRect.x - destRect.x;
      destRect.width -= xDiff;
      srcRect.x += xDiff * xn / xd;
      srcRect.width -= xDiff * xn / xd;
      destRect.x = clipRect.x;
    }
    if (destRect.y < clipRect.y)
    {
      int yDiff = clipRect.y - destRect.y;
      destRect.height -= yDiff;
      srcRect.y += yDiff * yn / yd;
      srcRect.height -= yDiff * yn / yd;
      destRect.y = clipRect.y;
    }
    if (destRect.x + destRect.width > clipRect.x + clipRect.width)
    {
      int over = (destRect.x + destRect.width) - (clipRect.x + clipRect.width);
      destRect.width -= over;
      srcRect.width -= over * xn / xd;
    }
    if (destRect.y + destRect.height > clipRect.y + clipRect.height)
    {
      int over = (destRect.y + destRect.height) - (clipRect.y + clipRect.height);
      destRect.height -= over;
      srcRect.height -= over * yn / yd;
    }
  }

  public static void clipSrcDestRects(sage.geom.Rectangle clipRect, sage.geom.Rectangle srcRect,
      sage.geom.Rectangle destRect)
  {
    if (srcRect.width == 0 || destRect.width == 0 || srcRect.height == 0 || destRect.height == 0)
    {
      srcRect.width = destRect.width = srcRect.height = destRect.height = 0;
      return;
    }
    if (destRect.x < clipRect.x)
    {
      srcRect.x += (clipRect.x - destRect.x) * srcRect.width / destRect.width;
      srcRect.width -= (clipRect.x - destRect.x) * srcRect.width / destRect.width;
      destRect.width -= (clipRect.x - destRect.x);
      destRect.x = clipRect.x;
    }
    if (destRect.y < clipRect.y)
    {
      srcRect.y += (clipRect.y - destRect.y) * srcRect.height / destRect.height;
      srcRect.height -= (clipRect.y - destRect.y) * srcRect.height / destRect.height;
      destRect.height -= clipRect.y - destRect.y;
      destRect.y = clipRect.y;
    }
    if (destRect.x + destRect.width > clipRect.x + clipRect.width)
    {
      srcRect.width -= ((destRect.x + destRect.width) - (clipRect.x + clipRect.width)) * srcRect.width / destRect.width;
      destRect.width -= ((destRect.x + destRect.width) - (clipRect.x + clipRect.width));
    }
    if (destRect.y + destRect.height > clipRect.y + clipRect.height)
    {
      srcRect.height -= ((destRect.y + destRect.height) - (clipRect.y + clipRect.height)) * srcRect.height / destRect.height;
      destRect.height -= ((destRect.y + destRect.height) - (clipRect.y + clipRect.height));
    }
  }
}
