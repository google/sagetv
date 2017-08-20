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

public class SageProperties
{
  // This is used when loading/saving properties files so that we don't get a conflict when accessing the same file in
  // the filesystem (which can happen because of the asynchronicity of how clients are born and die)
  private static final Object GLOBAL_PROPERTY_LOCK = new Object();
  private static final boolean STORE_DEFAULTS = true;
  private static final String FINAL_PROPERTY = "zzz";
  public static final String[] CLIENT_XFER_PROPERTY_PREFIXES = { "epg/channel_lineups", "epg/service_levels",
    "epg/lineup_overrides", "epg/lineup_physical_overrides", "epg/physical_channel_lineups", "epg_data_sources",
    "videoframe/enable_pc", "videoframe/pc_code", "videoframe/pc_restrict", "mmc/video_format_code",
    "security/profile"/*and profiles*/, "security/default_profile",
    "videoframe/force_live_playback_on_currently_airing_programs" };
  public static final String[] CLIENT_DONT_SAVE_PROPERTY_PREFIXES = { "epg/channel_lineups", "epg/service_levels",
    "epg/lineup_overrides", "epg/lineup_physical_overrides", "epg/physical_channel_lineups", "epg_data_sources",
    "mmc/encoders", "videoframe/enable_pc", "videoframe/pc_code", "videoframe/pc_restrict",
    "security/profile"/*and profiles*/, "security/default_profile", "videoframe/force_live_playback_on_currently_airing_programs" };
  public static final String[] EMBEDDED_SYNCED_PROPERTIES = { "AudioOutput", "VideoSupportedModes", "VideoOutputResolution",
    "VideoConnector", "videoframe/display_aspect_ratio", "time_zone", "ui/ui_overscan_correction_perct_width",
    "ui/ui_overscan_correction_perct_height", "ui/ui_overscan_correction_offset_y", "ui/ui_overscan_correction_offset_x",
    "TV_STANDARD", "weather/units", "weather/locID", "mmc/video_format_code" };

  public SageProperties(boolean inClient)
  {
    client = inClient;
  }

  public void setupPrefs(String prefFilename)
  {
    setupPrefs(prefFilename, new java.io.File(Sage.getPath("core",prefFilename + ".defaults")).toString());
  }
  public void setupPrefs(String prefFilename, String defaultsFilename)
  {
    setupPrefs(prefFilename, defaultsFilename, null);
  }
  public void setupPrefs(String prefFilename, String defaultsFilename, SageProperties nextLevelDefault)
  {
    dirty = false;
    java.io.File crashFile = new java.io.File(prefFilename + ".autobackup");
    prefFile = new java.io.File(prefFilename);
    // Check for default prefs
    java.io.File defaultsFile = (defaultsFilename == null) ? null : new java.io.File(defaultsFilename);
    java.util.Properties defaultsPrefs = null;
    if (defaultsFile != null && defaultsFile.isFile())
    {
      java.io.InputStream inStream = null;
      synchronized (GLOBAL_PROPERTY_LOCK)
      {
        try
        {
          inStream = new java.io.BufferedInputStream(new java.io.FileInputStream(defaultsFile));
          defaultsPrefs = (nextLevelDefault != null) ?
              new FastProperties(nextLevelDefault.prefs) : new FastProperties();
              defaultsPrefs.load(inStream);
        }
        catch (java.io.IOException e)
        {
          System.err.println("Cannot load default preference file: " + defaultsFile);
          defaultsPrefs = null;
        }
        finally
        {
          if (inStream != null)
          {
            try{inStream.close();}catch(Exception e){}
          }
        }
      }
    }

    prefs = new FastProperties(defaultsPrefs);
    if (prefFile.isFile() || crashFile.isFile())
    {
      java.io.InputStream inStream = null;
      synchronized (GLOBAL_PROPERTY_LOCK)
      {
        try
        {
          inStream = new java.io.BufferedInputStream(new java.io.FileInputStream(prefFile.isFile() ?
              prefFile : crashFile));
          prefs.load(inStream);
        }
        catch (java.io.IOException e)
        {
          System.out.println("Cannot load preference file: " + (prefFile.isFile() ? prefFile : crashFile) + " e=" + e);
          return;
        }
        finally
        {
          if (inStream != null)
          {
            try{inStream.close();}catch(Exception e){}
          }
        }
      }
      // See if we loaded junk or not. This can happen if the system isn't shutdown right
      // and the disk isn't flushed or something like that....you end up with a file
      // filled with 0's. So it yields a long \u0000 property name.
      // We can also end up with a clients property file zeroed out but the parent root properties file
      // has the zzz set in it, so we need to check for zero size properties as well
      if ((!prefs.containsKey(FINAL_PROPERTY) || prefs.size() == 0) && prefs.getProperty("version", "").indexOf(" 1.") == -1 &&
          crashFile.isFile() && prefFile.isFile())
      {
        System.out.println("CORRUPTED/EMPTY Properties file found. Restoring the backup.");
        synchronized (GLOBAL_PROPERTY_LOCK)
        {
          prefFile.delete();
        }
        setupPrefs(prefFilename, defaultsFilename, nextLevelDefault);
        return;
      }
    }
    if (!prefFile.isFile())
    {
      savePrefs();
      //crashFile.delete();
    }
    if (client)
    {
      // Remove any of the server props from the client props
      String[] allKeys = (String[]) java.util.Collections.list(prefs.keys()).toArray(Pooler.EMPTY_STRING_ARRAY);
      for (int i = 0; i < allKeys.length; i++)
      {
        for (int j = 0; j < CLIENT_DONT_SAVE_PROPERTY_PREFIXES.length; j++)
          if (allKeys[i].startsWith(CLIENT_DONT_SAVE_PROPERTY_PREFIXES[j]))
          {
            prefs.remove(allKeys[i]);
            break;
          }
      }
    }
  }

  public java.util.Properties getAllPrefs()
  {
    return (java.util.Properties) prefs.clone();
  }

  public int getInt(String name, int d)
  {
    String s = prefs.getProperty(name);
    if (s == null)
    {
      if (STORE_DEFAULTS)
      {
        putInt(name, d);
        dirty = true;
      }
      return d;
    }
    try{ return Integer.parseInt(s); }
    catch(NumberFormatException e){return d;}
  }

  public boolean getBoolean(String name, boolean d)
  {
    String s = prefs.getProperty(name);
    if (s == null)
    {
      if (STORE_DEFAULTS)
      {
        putBoolean(name, d);
        dirty = true;
      }
      return d;
    }
    if (s != null && (s.equalsIgnoreCase("t") || s.equalsIgnoreCase("true")))
      return true;
    else
      return false;
  }

  public long getLong(String name, long d)
  {
    String s = prefs.getProperty(name);
    if (s == null)
    {
      if (STORE_DEFAULTS)
      {
        putLong(name, d);
        dirty = true;
      }
      return d;
    }
    try{ return Long.parseLong(s); }
    catch(NumberFormatException e){return d;}
  }

  public float getFloat(String name, float d)
  {
    String s = prefs.getProperty(name);
    if (s == null)
    {
      if (STORE_DEFAULTS)
      {
        putFloat(name, d);
        dirty = true;
      }
      return d;
    }
    try{ return Float.parseFloat(s); }
    catch(NumberFormatException e){return d;}
  }

  public String get(String name, String d)
  {
    String s = prefs.getProperty(name);
    if (s == null)
    {
      if (d != null && STORE_DEFAULTS)
      {
        put(name, d);
        dirty = true;
      }
      return d;
    }
    return s;
  }

  public Object getWithNoDefault(Object key)
  {
    return prefs.get(key);
  }

  public void putInt(String name, int x)
  {
    String newValue = Integer.toString(x);
    Object oldValue = prefs.setProperty(name, newValue);
    dirty |= (oldValue != newValue) && (oldValue == null || !oldValue.equals(newValue));
  }

  public void putBoolean(String name, boolean x)
  {
    String newValue = Boolean.toString(x);
    Object oldValue = prefs.setProperty(name, newValue);
    dirty |= (oldValue != newValue) && (oldValue == null || !oldValue.equals(newValue));
  }

  public void putLong(String name, long x)
  {
    String newValue = Long.toString(x);
    Object oldValue = prefs.setProperty(name, newValue);
    dirty |= (oldValue != newValue) && (oldValue == null || !oldValue.equals(newValue));
  }

  public void putFloat(String name, float x)
  {
    String newValue = Float.toString(x);
    Object oldValue = prefs.setProperty(name, newValue);
    dirty |= (oldValue != newValue) && (oldValue == null || !oldValue.equals(newValue));
  }

  public void put(String name, String x)
  {
    if (x == null)
      dirty |= (prefs.remove(name) != null);
    else
    {
      Object oldValue = prefs.setProperty(name, x);
      dirty |= (oldValue != x) && (oldValue == null || !oldValue.equals(x));
    }
  }

  public void remove(String name)
  {
    dirty |= (prefs.remove(name) != null);
  }

  public String[] childrenNames(String name)
  {
    if (!name.endsWith("/") && name.length() > 0)
      name += '/';
    java.util.HashSet rv = Pooler.getPooledHashSet();
    synchronized (prefs)
    {
      java.util.Enumeration walker = prefs.propertyNames();
      while (walker.hasMoreElements())
      {
        String currElem = (String) walker.nextElement();
        if (currElem.startsWith(name))
        {
          int tempIndex = currElem.indexOf('/', name.length());
          if (tempIndex != -1)
            rv.add(currElem.substring(name.length(),
                tempIndex));
        }
      }
    }
    String[] rv2 = (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
    Pooler.returnPooledHashSet(rv);
    return rv2;
  }

  public String[] keys(String name)
  {
    if (!name.endsWith("/") && name.length() > 0)
      name += '/';
    java.util.HashSet rv = Pooler.getPooledHashSet();
    synchronized (prefs)
    {
      java.util.Enumeration walker = prefs.propertyNames();
      while (walker.hasMoreElements())
      {
        String currElem = (String) walker.nextElement();
        if (currElem.startsWith(name) &&
            (currElem.indexOf('/', name.length()) == -1))
          rv.add(currElem.substring(name.length()));
      }
    }
    String[] rv2 = (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
    Pooler.returnPooledHashSet(rv);
    return rv2;
  }

  public void removeNode(String name)
  {
    if (!name.endsWith("/"))
      name += '/';
    java.util.ArrayList rv = new java.util.ArrayList();
    synchronized (prefs)
    {
      // We can use keys() here because we just want this one level of the Hashtable
      java.util.Enumeration walker = prefs.keys();
      while (walker.hasMoreElements())
      {
        String currElem = (String) walker.nextElement();
        if (currElem.startsWith(name))
          rv.add(currElem);
      }
    }
    for (int i = 0; i < rv.size(); i++)
      prefs.remove(rv.get(i));
    dirty |= !rv.isEmpty();
  }

  public boolean isDirty()
  {
    return dirty;
  }

  private boolean abortSaves = false;
  public void backupPrefs(String backupExtension)
  {
    synchronized (prefs)
    {
      try
      {
        java.io.OutputStream outStream = null;
        java.io.InputStream inStream = null;
        try
        {
          outStream = new java.io.BufferedOutputStream(new
              java.io.FileOutputStream(new java.io.File(prefFile.getAbsolutePath() + backupExtension)));
          inStream = new java.io.BufferedInputStream(new
              java.io.FileInputStream(prefFile));
          byte[] buf = new byte[65536];
          int numRead = inStream.read(buf);
          while (numRead != -1)
          {
            outStream.write(buf, 0, numRead);
            numRead = inStream.read(buf);
          }
        }
        finally
        {
          try
          {
            if (inStream != null)
            {
              inStream.close();
              inStream = null;
            }
          }
          catch (java.io.IOException e) {}
          try
          {
            if (outStream != null)
            {
              outStream.close();
              outStream = null;
            }
          }
          catch (java.io.IOException e) {}
        }
      }
      catch (java.io.IOException e)
      {
        System.out.println("Error performing properties backup of:" + e);
      }
    }
  }
  public void savePrefs()
  {
    if (pendingSave) {
      if (Sage.DBG) System.out.println("Leaving early from savePrefs()...another thread is already waiting to do it");
      return;
    }
    // Back it up first and then save it or we could have a half-file if we crash
    java.io.FileOutputStream outStream = null;
    try
    {
      pendingSave = true;
      synchronized (GLOBAL_PROPERTY_LOCK)
      {
        pendingSave = false;
        // We only need to hold the lock on the actual properties map while we get the values we need
        // to write out. We need to hold the GLOBAL lock the whole time here. By dropping the
        // prefs lock early we then won't block on any getProperty calls while we are waiting for writing out
        // the properties to complete.
        String[] allKeys;
        String[] allValues;
        synchronized (prefs)
        {
          if (abortSaves)
          {
            System.out.println("WARNING: Aborting properties file save!");
            return;
          }
          if (Sage.DBG) System.out.println("Saving properties file to " + prefFile);
          abortSaves = true;
          allKeys = (String[]) java.util.Collections.list(prefs.keys()).toArray(Pooler.EMPTY_STRING_ARRAY);
          java.util.Arrays.sort(allKeys);
          allValues = new String[allKeys.length];
          for (int i = 0; i < allValues.length; i++)
            allValues[i] = (String)prefs.get(allKeys[i]);
        }
        java.io.File backupFile = new java.io.File(prefFile.toString() + ".autobackup");
        backupFile.delete();
        prefFile.renameTo(backupFile);
        java.io.File tmpWriteFile = new java.io.File(prefFile.toString() + ".tmp");
        outStream = new java.io.FileOutputStream(tmpWriteFile);
        java.io.BufferedWriter awriter;
        awriter = new java.io.BufferedWriter(new java.io.OutputStreamWriter(outStream, "8859_1"));
        awriter.write("#Sage Preferences");
        awriter.newLine();
        awriter.write("#" + new java.util.Date().toString());
        awriter.newLine();
        for (int i = 0; i < allKeys.length; i++)
        {
          // Check for a key with all 0x0 values which can happen from crashes while saving.
          // Ignore those since they're junk.
          boolean ignoreKey = true;
          for (int j = 0; j < allKeys[i].length(); j++)
          {
            if (allKeys[i].charAt(j) != 0)
            {
              ignoreKey = false;
              break;
            }
          }
          if (ignoreKey)
            continue;
          // if we're the client, ignore the server transferred properties
          if (client)
          {
            boolean keeper = true;
            for (int j = 0; j < CLIENT_DONT_SAVE_PROPERTY_PREFIXES.length; j++)
              if (allKeys[i].startsWith(CLIENT_DONT_SAVE_PROPERTY_PREFIXES[j]))
              {
                keeper = false;
                break;
              }

            if (!keeper)
              continue;
          }
          savePair(allKeys[i], allValues[i], awriter);
        }
        if (!prefs.containsKey(FINAL_PROPERTY))
        {
          awriter.write(FINAL_PROPERTY + "=true");
          awriter.newLine();
        }
        awriter.flush();
        // force the changes to disk!!!!
        // Narflex - 9/24/12 - We're going back to just using fsync() and not executing 'sync' because it can have too much of
        // a system wide impact to do so.
        outStream.getFD().sync();
        outStream.close();
        tmpWriteFile.renameTo(prefFile);
        if (Sage.DBG) System.out.println("Done writing out the data to the properties file");
        //backupFile.delete();
        abortSaves = false;
        /*if (Sage.EMBEDDED)
        {
          // It *should* be safe to do this on our thread; but just to be careful we'll fork it. We did see a deadlock on this once;
          // although that was from a property retrieval in the logs rolling over....although I'm suspicious this could deadlock as well.
          Pooler.execute(new Runnable()
          {
            public void run()
            {
              // We need to sync the filesystem here (even though we do it above) or it may not
              // have actually written the new data to the flash properly
              IOUtils.exec2("sync", false);
            }
          });
        }*/
        dirty = false;
      }
    }
    catch (Throwable e)
    {
      System.out.println("ERROR Saving preferences:" + e);
      // Restore the backup file or we can lose the whole properties
      java.io.File backupFile = new java.io.File(prefFile.toString() + ".autobackup");
      synchronized (GLOBAL_PROPERTY_LOCK)
      {
        backupFile.renameTo(prefFile);
      }
    }
    finally
    {
      if (outStream != null)
      {
        try{outStream.close();}catch(Exception e){}
      }
    }
  }

  public String[] getMRUList(String listName, int maxCount)
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    String currVal = get("mru/" + listName + "/order", null);
    if (currVal == null)
      return Pooler.EMPTY_STRING_ARRAY;
    currVal = currVal.trim();
    for (int i = 0; i < currVal.length() && i < maxCount; i++)
    {
      char c = currVal.charAt(i);
      String val = get("mru/" + listName + "/values/" + c, null);
      if (val != null)
      {
        rv.add(val);
      }
    }
    return (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public void updateMRUList(String listName, String value, int maxCount)
  {
    if (value == null) return;
    String currOrder = get("mru/" + listName + "/order", "");
    // First search for this value in the existing properties
    String[] usedValues = keys("mru/" + listName + "/values");
    char oldC = 0;
    for (int i = 0; usedValues != null && i < usedValues.length; i++)
    {
      if (value.equals(get("mru/" + listName + "/values/" + usedValues[i], null)))
      {
        oldC = usedValues[i].charAt(0);
        break;
      }
    }
    if (oldC == 0)
    {
      // Not found, must add it
      if (currOrder.length() >= maxCount - 1)
        currOrder = currOrder.substring(0, maxCount - 1);
      char newC = 'a';
      while (currOrder.indexOf(newC) != -1)
        newC++;
      put("mru/" + listName + "/values/" + newC, value);
      put("mru/" + listName + "/order", newC + currOrder);
    }
    else
    {
      int oldIdx = currOrder.indexOf(oldC);
      if (oldIdx != -1)
        currOrder = currOrder.substring(0, oldIdx) + currOrder.substring(oldIdx + 1);
      if (currOrder.length() >= maxCount - 1)
        currOrder = currOrder.substring(0, maxCount - 1);
      put("mru/" + listName + "/order", oldC + currOrder);
    }
  }

  public static void syncClientServerEmbeddedProperties()
  {
    return;
  }

  private static class FastProperties extends java.util.Properties
  {
    public FastProperties()
    {
      this(null);
    }
    public FastProperties(java.util.Properties inDefaults)
    {
      super(inDefaults);
    }
    public java.util.Enumeration propertyNames()
    {
      // We need to make an enumeration object that goes through our keys and then
      // goes up to the next default level recursively until it's done
      return new java.util.Enumeration()
      {
        public boolean hasMoreElements()
        {
          ensureValidEnum();
          return (currEnum != null && currEnum.hasMoreElements());
        }

        private void ensureValidEnum()
        {
          while (currEnum == null || !currEnum.hasMoreElements())
          {
            if (currProps == null)
              return;
            else if (currEnum == null)
              currEnum = currProps.keys();
            else // currEnum is done; go up a level
            {
              currProps = (FastProperties)currProps.defaults;
              currEnum = null;
            }
          }
        }

        public Object nextElement()
        {
          ensureValidEnum();
          if (currEnum == null)
            throw new java.util.NoSuchElementException();
          else
            return currEnum.nextElement();
        }
        FastProperties currProps = FastProperties.this;
        java.util.Enumeration currEnum;
      };
    }
  }

  private static final char[] hexList = {
    '0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'
  };
  private static char genHex(int nibble) {
    return hexList[(nibble & 0xF)];
  }

  public static void savePair(String key, String value, java.io.BufferedWriter awriter) throws java.io.IOException {
    writeWithEscapes(key, true, awriter);
    awriter.write("=");
    writeWithEscapes(value, false, awriter);
    awriter.newLine();
  }

  private static void writeWithEscapes(String str, boolean escapeSpaces, java.io.BufferedWriter awriter) throws java.io.IOException
  {
    int len = str.length();
    for(int i = 0; i < len; i++) {
      char c = str.charAt(i);
      switch (c) {
        case ' ':
          if (i == 0 || escapeSpaces)
            awriter.write('\\');
          awriter.write(' ');
          break;
        case '#':
        case '!':
        case '=':
        case ':':
          awriter.write('\\');
          awriter.write(c);
          break;
        case '\\':
          awriter.write('\\');
          awriter.write('\\');
          break;
        case '\t':
          awriter.write('\\');
          awriter.write('t');
          break;
        case '\n':
          awriter.write('\\');
          awriter.write('n');
          break;
        case '\r':
          awriter.write('\\');
          awriter.write('r');
          break;
        case '\f':
          awriter.write('\\');
          awriter.write('f');
          break;
        default:
          // Outside printable 7-bit ASCII range
          if ((c < 0x0020) || (c > 0x007e)) {
            awriter.write('\\');
            awriter.write('u');
            awriter.write(genHex((c >> 12) & 0xF));
            awriter.write(genHex((c >> 8) & 0xF));
            awriter.write(genHex((c >> 4) & 0xF));
            awriter.write(genHex(c & 0xF));
          } else {
            awriter.write(c);
          }
          break;
      }
    }
  }

  private java.io.File prefFile;
  private FastProperties prefs;
  private boolean client;
  private boolean dirty;
  private volatile boolean pendingSave;
}
