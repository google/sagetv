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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NoMergeScheduler;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.NoLockFactory;
import org.apache.lucene.store.RAMDirectory;

import sage.media.format.ContainerFormat;
import sage.media.format.FormatParser;
import sage.msg.MsgManager;
import sage.msg.SystemMessage;

import sage.io.BufferedSageFile;
import sage.io.EncryptedSageFile;
import sage.io.LocalSageFile;
import sage.io.SageDataFile;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;

/*
 * DB File Format:
 * WIZ<version><data>(n) - version is 1 byte
 * <operation><type>bytes - data ops, type are bytes
 *
 * operations: add, update, remove, size hint (int), full data, index data
 *
 * Usage of Wizard in class: Zap2it for data updates, EPG for maintenance calls,
 * DBObjects use it for interaction, ChannelSetupMenu uses it to get a list of channels,
 * Index & Table use it for interaction, Profiler uses it for interaction, Sage uses it for initialization,
 * Seeker uses it for manual must see & channel surfing & default watch & Airing validation &
 * getting list of airings for creating schedule & getting watches for drawing &
 * getting watches for building recently watched,
 * BigBrother uses it for re-starting a prior watch
 *
 * NOTE: You cannot update an object in a way that would cause the primary index to change.
 * If you do, it'll cause the DB load to fail on that update because it won't be able
 * to find the old object since its position in the primary index will have changed.
 *
 * IMPORTANT RULE: All of the comparators should utilize fields of the object only. They should
 * NOT reference any sub-objects. Otherwise this makes them dependent upon others existence
 * for stability in the indices. We do not want to require this dependence; therefore we must modify
 * all of the comparators to adhere to this rule.
 */
public class Wizard implements EPGDBPublic2
{
  static final int INC_SIZE = 1000;

  public static final int STUPID_SIZE = 10000000;
  private static final long KILL_AGE = Sage.MILLIS_PER_DAY*2;
  private static final long SERVER_KILL_AGE = Sage.MILLIS_PER_DAY*2;
  private static final int NUM_TRANSACTIONS_TO_COMPRESS_ON_LOAD = 2000;
  private static final int NUM_TRANSACTIONS_TO_COMPRESS = 10000;

  public static final int SERVER_INIT_ID_BOUNDARY = 500000000;
  public static final int CLIENT_BASE_ID = SERVER_INIT_ID_BOUNDARY*2;

  private static final int WIZARD_MEDIAMASK_MAINTENANCE_LOCK_TIME = 500; // locking the DB while performing maintenance
  private static final int WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME = 2000; // time between runs on maintenance

  private static final int MAX_DB_ERRORS_TO_LOG = 1000;

  private static boolean validateObjs = true;
  private boolean REPAIR_CATEGORIES = false;

  // 1 did not have lastWatched in Show
  // 2 had Airings by Category, and by Show was primary, now ID is
  // 3 did not have watched by time
  // 4 did not have prime titles
  // 5 did not have shows by title
  // 6 stored the indexes
  // 7 did not have the agents
  // 8 did not have IDMaps & had the old code format
  // VERSION 9 is incompatible with previous versions
  // 10 added likeState to Airing
  // 11 dropped Agent ID in MediaFile
  // 12 added timeslot & slotType to Agent
  // 13 added name to MediaFile
  // 14 added extraInfo & recurrence to ManualRecord
  // 15 added archive to MediaFile
  // 16 changed for the Tribune Data
  // 17 trimmed down Airing's object size, changed provider & stationid to long/int
  // 18 added externalID back to Show
  // 19 added longName to Channel
  // 20 removed shows by titleid index
  // 21 Added primetitle field to Agent
  // 22 Removed IDMap and added shows by extid index
  // 23 Added weakAgents and weakAirings to Agent
  // 24 Added weakAirings to ManualRecord
  // 25 Added quality to Agent
  // 26 Added updateCount to Show
  // 27 Marker for when lastWatched was preserved correctly for Show
  // 28 Added a bunch of fields to Show & Airing
  // 29 Added infoAiring to manualrecord & mf
  // 30 Added manual flag to Wasted
  // 31 Added major/minor DTV channel number
  // 32 Added host to MediaFile
  // 33 Removed providerID from Airing and added encodedBy field
  // 34 Added showID & time to Watched
  // 35 Removed airingID from ManualRecord & MediaFile
  // 36 added MediaType to MediaFile, added forcedComplete to MediaFile
  // 37 added startPad, stopPad to Agent
  // 38 added Widget
  // 39 added thumbnailFile, thumbnailSize & thumbnailOffset to MediaFile
  // 40 added agentFlags
  // 41 added Playlist
  // 42 removed primeTitle
  // 43 added role to Agent
  // 44 added Album genre & year to Playlist
  // 45 removed weakAirings from Agent
  // 46 made Widget properties String values, not String arrays
  // 47 encrypted the database w/ 1024 bit key
  // 48 added more type info to MediaFile
  // 49 picture MediaFiles can have a different thumbnail now
  // 50 changed desc & episode in Show to use byte[] instead of String for storage
  // 51 added streamBufferSize to MediaFile
  // 52 added keyword to Agent
  // 53 switched to using UTF-8 encoding for text
  // 54 added fileFormat to MediaFile
  // 55 added mediaMaskB to DBObject
  // 56 marker for having to autogenerate mediaMaskB
  // 57 added TVEditorial and SeriesInfo
  // 58 added titleNum to Watched
  // 65 added persist byte to Airing
  // 66 added prefixOffset to MediaFile (COMPACT_DB only)
  // 66 extended keep at most bits in Favorite and added auto convert format to Favorite (server)
  // 67 added destination folder for automatic conversions to Agent
  // 68 added generic Favorite (Agent) properties
  // 69 added generic ManualRecord properties
  // 70 added generic Playlist properties
  // 71 added SeriesInfo properties, and generic user records storage
  // 72 added in compact DB mode Show bonuses
  // 73 changed Airing duration to be a long in compact DB mode
  // 74 added season & episode number to Show, variable number of categories, forced unique indicator and artwork bitmasks, channel logo bitmask, 32-bit airing misc
  // 75 added multiple timeslots for Favorites
  // 76 relevant changes reverted
  // 77 added createWatchCount to MediaFile in compact mode, removed deadAir, streamBufferSize and providerID from MediaFile
  // 78 added restrictionMask to Channel, added provider String to PI structure in Airing
  // 79 added Person object, updated Show to have season/series IDs and more imagery, updated SeriesInfo with more imagery
  // 80 added integer array to Channel for the new channel logos
  // 81 relevant changes reverted
  // 82 changed media mask from being bytes to shorts
  // 83 added support for writing out index orders; this is backwards compatible since older versions will skip this section and create their own indices
  // 84 open sourced initial version
  // 85 fixed Wizard bug where we were loading using BYTE_CHARSET instead of I18N_CHARSET
  // 86 added support for encoded Schedules Direct image URLs for Channel, Show and SeriesInfo objects
  // 87 added support for encoded Schedules Direct image URLs for Person
  public static byte VERSION = 0x57;
  public static final byte BAD_VERSION = 0x00;

  // This flag is to deal with the problem where we used 32-bit ints for Airing durations in compact DB mode. For PVR
  // we use them to fill out the EPG with a duration of Long.MAX_VALUE/2; and that was getting read in as a negative number which
  // really screwed things up. If we read in a negative duration; we set this flag and then remove all No Data airing from the database
  // and recreate all of them.
  public static boolean INVALID_AIRING_DURATIONS = false;

  // All commands are preceded by an int which is the length of the
  // operation packet in bytes, the size includes the 4 bytes for the int

  // operation codes
  public static final byte ADD = 0x01;
  public static final byte UPDATE = 0x02;
  public static final byte REMOVE = 0x03;
  public static final byte SIZE = 0x04;
  public static final byte FULL_DATA = 0x05;
  public static final byte INDEX_DATA = 0x06;
  public static final byte XCTS_DONE = 0x10;

  // table codes
  public static final byte NETWORK_CODE = 0x01;
  public static final byte CHANNEL_CODE = 0x02;
  public static final byte TITLE_CODE = 0x03;
  public static final byte PEOPLE_CODE = 0x04;
  public static final byte CATEGORY_CODE = 0x05;
  public static final byte SUBCATEGORY_CODE = 0x06;
  public static final byte RATED_CODE = 0x07;
  public static final byte PR_CODE = 0x08;
  public static final byte ER_CODE = 0x09;
  public static final byte YEAR_CODE = 0x0A;
  public static final byte SHOW_CODE = 0x0B;
  public static final byte AIRING_CODE = 0x0C;
  public static final byte WATCH_CODE = 0x0D;
  public static final byte BONUS_CODE = 0x0E;
  public static final byte PRIME_TITLE_CODE = 0x0F;
  public static final byte AGENT_CODE = 0x10;
  public static final byte IDMAP_CODE = 0x11;
  public static final byte MEDIAFILE_CODE = 0x12;
  public static final byte MANUAL_CODE = 0x13;
  public static final byte WASTED_CODE = 0x14;
  public static final byte WIDGET_CODE = 0x15;
  public static final byte PLAYLIST_CODE = 0x16;
  public static final byte TVEDITORIAL_CODE = 0x17;
  public static final byte SERIESINFO_CODE = 0x18;
  public static final byte USERRECORD_CODE = 0x19;

  // index codes
  public static final byte DEFAULT_INDEX_CODE = 0x00;
  public static final byte TITLES_BY_NAME_CODE = 0x01;
  public static final byte PEOPLE_BY_NAME_CODE = 0x02;
  public static final byte SHOWS_BY_EXTID_CODE = 0x03;
  public static final byte AIRINGS_BY_CT_CODE = 0x05;
  public static final byte AIRINGS_BY_SHOW_CODE = 0x06;
  public static final byte WATCHED_BY_TIME_CODE = 0x07;
  public static final byte PRIME_TITLES_BY_NAME_CODE = 0x08;
  public static final byte SHOWS_BY_TITLEID_CODE = 0x09;
  public static final byte MEDIAFILE_BY_AIRING_ID_CODE = 0x0A;
  public static final byte AGENTS_BY_CARNY_CODE = 0x0B;
  public static final byte MEDIAFILE_BY_FILE_PATH = 0x0C;
  public static final byte SERIESINFO_BY_LEGACYSERIESID_CODE = 0x0D;
  public static final byte USERRECORD_BY_STOREKEY_CODE = 0x0E;
  public static final byte SERIESINFO_BY_SHOWCARDID_CODE = 0x0F;
  public static final byte PEOPLE_BY_EXTID_CODE = 0x10;

  public static final int WRITE_OPT_USE_ARRAY_INDICES = 0x1;

  public enum MaintenanceType {
    FULL,
    PATCH_EPG_HOLES,
    NONE
  }

  private static String getNameForCode(byte code)
  {
    switch (code)
    {
      case NETWORK_CODE:
        return "Network";
      case CHANNEL_CODE:
        return "Channel";
      case TITLE_CODE:
        return "Title";
      case PEOPLE_CODE:
        return "People";
      case CATEGORY_CODE:
        return "Category";
      case SUBCATEGORY_CODE:
        return "SubCategory";
      case RATED_CODE:
        return "Rated";
      case PR_CODE:
        return "ParentalRating";
      case ER_CODE:
        return "ExtendedRating";
      case YEAR_CODE:
        return "Year";
      case SHOW_CODE:
        return "Show";
      case AIRING_CODE:
        return "Airing";
      case WATCH_CODE:
        return "Watched";
      case BONUS_CODE:
        return "Bonus";
      case PRIME_TITLE_CODE:
        return "PrimeTitle";
      case AGENT_CODE:
        return "Agent";
      case MEDIAFILE_CODE:
        return "MediaFile";
      case MANUAL_CODE:
        return "ManualRecord";
      case WASTED_CODE:
        return "Wasted";
      case WIDGET_CODE:
        return "Widget";
      case PLAYLIST_CODE:
        return "Playlist";
      case TVEDITORIAL_CODE:
        return "TVEditorial";
      case SERIESINFO_CODE:
        return "SeriesInfo";
      case USERRECORD_CODE:
        return "UserRecord";
      default:
        return "UNKNOWN CODE" + code;
    }
  }

  private Table getTable(byte tableCode)
  {
    return tables[tableCode];
  }

  public int getSize(byte typeCode)
  {
    Table t = getTable(typeCode);
    if (t == null) return 0;
    else return t.num;
  }

  public int getSize(byte typeCode, int mediaMask)
  {
    Table t = getTable(typeCode);
    if (t == null) return 0;
    Index idx=t.primary;
    int count=0;
    for (int i = 0; i < idx.data.length; i++) {
      DBObject obj = idx.data[i];
      if (obj != null && (obj.getMediaMask() & mediaMask) != 0)
        count++;
    }
    return count;
  }


  public DBObject[] getRawAccess(byte tableCode, byte indexCode)
  {
    return getIndex(tableCode, indexCode).data;
  }

  // Avoid the DB locks if we are loading as we do that single threaded so we have
  // no need for actual locks at that point in time.
  void acquireReadLock(byte code)
  {
    if (!loading)
      getTable(code).acquireReadLock();
  }

  void acquireWriteLock(byte code)
  {
    if (!loading)
      getTable(code).acquireWriteLock();
  }

  void releaseReadLock(byte code)
  {
    if (!loading)
      getTable(code).releaseReadLock();
  }

  void releaseWriteLock(byte code)
  {
    if (!loading)
      getTable(code).releaseWriteLock();
  }

  void check(byte code)
  {
    getTable(code).check();
  }

  private Index getIndex(byte tableCode)
  {
    return getTable(tableCode).primary;
  }
  private Index getIndex(byte tableCode, byte indexCode)
  {
    if (indexCode <= 0)
      return getTable(tableCode).primary;
    else
      return getTable(tableCode).getIndex(indexCode);
  }

  public static final String WIZARD_KEY = "wizard";
  private static final String DB_FILE = "db_file";
  private static final String DB_BACKUP_FILE = "db_backup_file";
  public static final String WIDGET_DB_FILE = "widget_db_file";
  private static final String NEXT_UID = "next_uid";
  static final String CLEAR_PROFILE = "clearprofile";
  static final String CLEAR_WATCHED = "clearwatched";
  private static final String NOSHOW_ID = "noshow_id";
  private static final String LAST_MAINTENANCE = "last_maintenance";
  private static final String NODATA_MAX_LEN = "nodata_max_len";
  private static final String NODATA_DUR_FOR_MAXRULE = "nodata_dur_for_maxrule";

  // This indicates that the mediaMaskB in DBObject should be generated by ALL DBObjects during this load
  public static boolean GENERATE_MEDIA_MASK = false;

  /*
   * IMPORTANT NOTE: Manual & MediaFile have an interdependence that is worsened
   * by the fact that it can change over time. Therefore we have to wait to validate
   * these objects until the DB is fully loaded. Actually, if we did that in general
   * it would resolve all validation issues related to loading order and allow circularities
   * in the database.
   */
  private static byte[] WRITE_ORDER;

  static
  {
    WRITE_ORDER = new byte[] { YEAR_CODE, NETWORK_CODE, TITLE_CODE, CHANNEL_CODE, BONUS_CODE, PEOPLE_CODE, SUBCATEGORY_CODE,
      RATED_CODE, PR_CODE, ER_CODE, CATEGORY_CODE, PRIME_TITLE_CODE,
      SHOW_CODE, AIRING_CODE, MANUAL_CODE, MEDIAFILE_CODE, WATCH_CODE, AGENT_CODE,
      WASTED_CODE, PLAYLIST_CODE, TVEDITORIAL_CODE, SERIESINFO_CODE, USERRECORD_CODE };
 }
  private static final Object instanceLock = new Object();

  private static class WizardHolder
  {
    public static final Wizard instance = new Wizard();
  }

  /**
   * Initialize the Wizard object
   *
   * @return
   */
  public static Wizard prime()
  {
    Wizard instance = getInstance();
    synchronized (instanceLock)
    {
      instance.initWizInTables();
      instance.init(null, null, false);
    }
    return instance;
  }

  // For standalone DB operation, ala Warlock
  public static Wizard prime(String dbFilename, String dbBackupFilename)
  {
    Wizard instance = getInstance();
    synchronized (instanceLock)
    {
      instance.initWizInTables();
      instance.init(dbFilename, dbBackupFilename, true);
    }
    return instance;
  }

  public static Wizard getInstance()
  {
    return WizardHolder.instance;
  }

  private Wizard()
  {
    prefsRoot = WIZARD_KEY + '/';
    nextID = Sage.getInt(prefsRoot + NEXT_UID, 1);
    // on embedded the clock may not be set yet so don't bork this setting
    lastMaintenance = Math.min(Sage.time(), Sage.getLong(prefsRoot + LAST_MAINTENANCE, 0));
    noDataMaxLen = Sage.getLong(prefsRoot + NODATA_MAX_LEN, 2*Sage.MILLIS_PER_HR);
    noDataMaxRuleDur = Sage.getLong(prefsRoot + NODATA_DUR_FOR_MAXRULE, 2*Sage.MILLIS_PER_DAY);
    listeners = new Vector<XctSyncClient>();
    pendingWriteXcts = new Vector<XctObject>();
    if (Sage.client)
      nextID = CLIENT_BASE_ID;

    tables = new Table[] { null,
      new Table(NETWORK_CODE, new Index(DBObject.ID_COMPARATOR)),
      new Table(CHANNEL_CODE, new Index(Channel.STATION_ID_COMPARATOR)),
      new Table(TITLE_CODE, new Index(DBObject.ID_COMPARATOR),
        new Index[] { new Index(TITLES_BY_NAME_CODE, Stringer.NAME_COMPARATOR) }),
      new PersonTable(PEOPLE_CODE, new Index(DBObject.ID_COMPARATOR),
        new Index[] { new Index(PEOPLE_BY_NAME_CODE, Person.NAME_COMPARATOR),
          new Index(PEOPLE_BY_EXTID_CODE, Person.EXTID_COMPARATOR)}),
      new Table(CATEGORY_CODE, new Index(DBObject.ID_COMPARATOR)),
      new Table(SUBCATEGORY_CODE, new Index(DBObject.ID_COMPARATOR)),
      new Table(RATED_CODE, new Index(DBObject.ID_COMPARATOR)),
      new Table(PR_CODE, new Index(DBObject.ID_COMPARATOR)),
      new Table(ER_CODE, new Index(DBObject.ID_COMPARATOR)),
      new Table(YEAR_CODE, new Index(DBObject.ID_COMPARATOR)),
      new ShowTable(SHOW_CODE, new Index(DBObject.ID_COMPARATOR),
        new Index[] { new Index(SHOWS_BY_EXTID_CODE, Show.EXTID_COMPARATOR) }),
      new Table(AIRING_CODE, new Index(DBObject.ID_COMPARATOR),
        new Index[] { new Index(AIRINGS_BY_SHOW_CODE, Airing.SHOW_ID_COMPARATOR),
          new Index(AIRINGS_BY_CT_CODE, Airing.CHANNEL_TIME_COMPARATOR),
          /*new Index(AIRINGS_BY_TC_CODE, Airing.TIME_CHANNEL_COMPARATOR)*/ }),
      new Table(WATCH_CODE, new Index(Watched.SHOW_ID_COMPARATOR),
        new Index[] { new Index(WATCHED_BY_TIME_CODE, Watched.TIME_COMPARATOR) }),
      new Table(BONUS_CODE, new Index(DBObject.ID_COMPARATOR)),
      new Table(PRIME_TITLE_CODE, new Index(DBObject.ID_COMPARATOR),
        new Index[] { new Index(PRIME_TITLES_BY_NAME_CODE, Stringer.NAME_COMPARATOR) }),
      new Table(AGENT_CODE, new Index(DBObject.ID_COMPARATOR),
        new Index[] { new Index(AGENTS_BY_CARNY_CODE, Carny.AGENT_SORTER) }),
      null,
      new Table(MEDIAFILE_CODE, new Index(DBObject.ID_COMPARATOR),
        new Index[] { new Index(MEDIAFILE_BY_AIRING_ID_CODE, MediaFile.AIRING_ID_COMPARATOR),
          new Index(MEDIAFILE_BY_FILE_PATH, MediaFile.FILE_PATH_COMPARATOR)}),
      new Table(MANUAL_CODE, new Index(DBObject.ID_COMPARATOR)),
      new Table(WASTED_CODE, new Index(Wasted.AIRING_ID_COMPARATOR)),
      new Table(WIDGET_CODE, new Index(DBObject.ID_COMPARATOR)),
      new Table(PLAYLIST_CODE, new Index(DBObject.ID_COMPARATOR)),
      new Table(TVEDITORIAL_CODE, new Index(TVEditorial.SHOW_ID_COMPARATOR)),
      new Table(SERIESINFO_CODE, new Index(DBObject.ID_COMPARATOR),
        new Index[] { new Index(SERIESINFO_BY_LEGACYSERIESID_CODE, SeriesInfo.LEGACY_SERIES_ID_COMPARATOR),
        new Index(SERIESINFO_BY_SHOWCARDID_CODE, SeriesInfo.SHOWCARD_ID_COMPARATOR)}),
      new Table(USERRECORD_CODE, new Index(DBObject.ID_COMPARATOR),
        new Index[] { new Index(USERRECORD_BY_STOREKEY_CODE, UserRecord.USERRECORD_COMPARATOR) }),
    };
  }

  private void initWizInTables() {
    for (Table tab : tables) {
      if (tab != null)
        tab.setWizard(this);
    }
  }

  private void init(String dbFilename, String dbBackupFilename, boolean inStandalone)
  {
    if (!primed)
    {
      god = Carny.getInstance();
      standalone = inStandalone;
      String fileStr = (dbFilename != null) ? dbFilename : Sage.get(prefsRoot + DB_FILE, null);
      if (fileStr == null || fileStr.length() == 0)
      {
        dbFile = new File(System.getProperty("user.dir"), "Wiz.bin");
        Sage.put(prefsRoot + DB_FILE, dbFile.toString());
      }
      else
      {
        dbFile = new File(fileStr);
      }
      if (!dbFile.isFile() && dbFile.getParentFile() != null)
      {
        dbFile.getParentFile().mkdirs();
      }
      fileStr = (dbBackupFilename != null) ? dbBackupFilename : Sage.get(prefsRoot + DB_BACKUP_FILE, null);
      if (fileStr == null || fileStr.length() == 0)
      {
        dbBackupFile = new File(System.getProperty("user.dir"), "Wiz.bak");
        Sage.put(prefsRoot + DB_BACKUP_FILE, dbBackupFile.toString());
      }
      else
      {
        dbBackupFile = new File(fileStr);
      }
      if (Sage.DBG) System.out.println("dbFile=" + dbFile + "(" + dbFile.length() + ") dbBackupFile=" + dbBackupFile +
          "(" + dbBackupFile.length() + ")");
      if (!dbBackupFile.isFile() && dbBackupFile.getParentFile() != null)
      {
        dbBackupFile.getParentFile().mkdirs();
      }
      // On a new version revert to the OriginalV2.stv to prevent problems with the STV on upgrade
      fileStr = (Sage.WINDOWS_OS && SageTV.upgrade && Sage.getBoolean("wizard/revert_stv_on_upgrade", true)) ? null : Sage.get(prefsRoot + WIDGET_DB_FILE, null);
      if (Sage.initialSTV != null && new File(Sage.initialSTV).isFile())
        fileStr = Sage.initialSTV;
      if (fileStr == null)
      {
        widgetDBFile = new File(System.getProperty("user.dir"),
            "STVs" + File.separatorChar + ("SageTV7" + File.separatorChar + "SageTV7.xml"));
        Sage.put(prefsRoot + WIDGET_DB_FILE, widgetDBFile.toString());
      }
      else
      {
        widgetDBFile = new File(fileStr);
      }

      if (widgetDBFile != null)
      {
        // Make sure we have a Widgets file loaded, check 3 levels deep from where the Wiz file is stored
        if (!widgetDBFile.isFile())
        {
          File foundSTVFile = searchForSTVFile(dbFile.getParentFile(), 3);
          if (foundSTVFile != null)
          {
            widgetDBFile = foundSTVFile;
          }
        }

        if (!widgetDBFile.isFile() && widgetDBFile.getParentFile() != null)
        {
          widgetDBFile.getParentFile().mkdirs();
        }
      }
    }
    noShowID = Sage.getInt(prefsRoot + NOSHOW_ID, -1);

    /*
     * IMPORTANT NOTE JAK 8/25/05:
     * There's no reason to not do this every time since the servers don't
     * support streaming from other servers yet. This is only a source of problems.
     */
    if (!Sage.client)
    {
      MediaFile.MAKE_ALL_MEDIAFILES_LOCAL = true;
    }
    startSeq(true);

    refreshNoShow();

    if (INVALID_AIRING_DURATIONS)
    {
      if (Sage.DBG) System.out.println("WARNING: INVALID AIRING DURATIONS DETECTED...CLEAN THE DATABASE!");
      maintenance();
    }
    INVALID_AIRING_DURATIONS = false;

    if (!primed && !Sage.client)
    {
      Thread flusher = new Thread("Flusher")
      {
        @Override
        public void run()
        {
          while (true)
          {
            try{Thread.sleep(60000);}catch(Exception e){}
            flushDbOut();
          }
        }
      };
      flusher.setDaemon(true);
      flusher.start();
    }
    primed = true;
  }

  void updateNoDataLang()
  {
    if (noShow == null) return;
    try {
      acquireWriteLock(SHOW_CODE);
      noShow.title = getTitleForName(Sage.rez("EPG_Cell_No_Data"));
      logUpdate(noShow, SHOW_CODE);
    } finally {
      releaseWriteLock(SHOW_CODE);
    }
  }

  public void flushDbOut(){
    synchronized (outLock)
    {
      if (dbout != null)
      {
        try
        {
          dbout.sync();
        }
        catch (Exception e){}
      }
    }
  }

  private File searchForSTVFile(File startDir, int depth)
  {
    File[] subfiles = startDir.listFiles();
    for (int i = 0; subfiles != null && i < subfiles.length; i++)
    {
      if (subfiles[i].getName().toLowerCase().endsWith(".stv"))
        return subfiles[i];
      if (depth > 0 && subfiles[i].isDirectory())
      {
        File goodsub = searchForSTVFile(subfiles[i], depth - 1);
        if (goodsub != null)
          return goodsub;
      }
    }
    return null;
  }

  // Because we protect against redundant Show adds, this is a valid way to figure out which
  // is the NoShow
  void refreshNoShow()
  {
    noShow = addShow(Sage.rez("EPG_Cell_No_Data"), null, null, null, 0, null, null, null, null, null, null, null, null, "NoShow",
        null, 0,  0, (short) 0, (short) 0, false, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
    Sage.putInt(prefsRoot + NOSHOW_ID, noShowID = noShow.id);
  }

  void mpause() { if (primed) { try{Thread.sleep(50);}catch(Exception e){} } }

  void maintenance()
  {
    maintenance(MaintenanceType.FULL);
  }
  void maintenance(MaintenanceType maintenanceType)
  {
    if (Sage.client)
    {
      Sage.putLong(prefsRoot + LAST_MAINTENANCE, lastMaintenance = Sage.time());
      return;
    }
    if (disableDatabase) return;
    if ( maintenanceType == MaintenanceType.NONE) return;

    if (!Sage.getBoolean("wizard/disable_maintenance", false))
    {
      // Narflex - 3/9/2012 - You MUST save the database after maintenance, even on embedded.
      // Otherwise the media mask changes we make to the database during this process will NOT
      // get saved out as well as any of our other transactions because we're going to reset
      // the maintenance timer here!
      boolean saveAfter = true;
      try
      {
        performingMaintenance = true;

        if (Sage.DBG) System.out.println("Performing maintenanance type: "+maintenanceType);
        /*
         * IMPORTANT NOTE: All calls made without logUpdate set to true are not
         * propogated to the clients. But since these are all maintenance calls,
         * there's no reason to really propogate them anyways since its OK
         * if the client has more data then it needs.
         *
         * Get the ID counter before we start this, and anything we find
         * that's above that number do NOT throw away.
         */
        // I think that this was the cause of the overlapping airings...it's one of the few diffs with embedded in this part of the code
        // and I know that maintenance must be the cause...so we'll set this to true and see if it comes back.
        boolean logMaintenanceXcts = true;
        int maintainStartID = nextID;
        // Remove all airings more than a week in the past.
        Set<Airing> toRemove = new HashSet<Airing>(5000);
        long killTime = Sage.time() - (standalone ? SERVER_KILL_AGE : KILL_AGE);
        Set<Airing> toSave = new HashSet<Airing>();
        MediaFile[] theFiles = getFiles();
        for (int i = 0; i < theFiles.length; i++)
        {
          if (theFiles[i].infoAiringID != 0 && theFiles[i].infoAiringID != theFiles[i].id)
          {
            toSave.add(getAiringForID(theFiles[i].infoAiringID));
          }
        }
        mpause();
        ManualRecord[] mrs = getManualRecords();
        for (int i = 0; i < mrs.length; i++)
        {
          if (mrs[i].infoAiringID != 0)
          {
            toSave.add(getAiringForID(mrs[i].infoAiringID));
          }
        }
        mpause();
        // NOTE FOR EMBEDDED: I've updated this to no longer force saving of old Airings that are linked to
        // unviewable fractional Watched objects. These can grow the database to an unsustainable size although that
        // would not realistically occur for customers; it has on test systems. We only really
        // care about retaining the fractional watched information for things that can be
        // resumed.
        Index watchIndex = getIndex(WATCH_CODE, (byte)0);
        for (int i = 0; i < watchIndex.table.num; i++)
        {
          Watched currWatch = (Watched) watchIndex.data[i];
          if (currWatch != null)
          {
            Airing watchAir = currWatch.getAiring();
            if (watchAir != null)
            {
              if (watchAir.isTV())
              {
                toSave.add(watchAir);
              }
              else if (watchAir.isVideo())
              {
                // Check to make sure that the MediaFile still exists for this video file. If not, then
                // the Watched object should be removed and the airing is up for GC, although a Playlist
                // could still retain that Airing
                if (getFileForAiring(watchAir) == null)
                {
                  removeWatched(currWatch);
                  i--;
                }
                else
                  toSave.add(watchAir);
              }
            } else { // if the Airing is dead, then remove the Watched object (happens on DB validation on startup too)
              removeWatched(currWatch);
              i--;
            }
          }
        }

        mpause();
        Index wasteIndex = getIndex(WASTED_CODE, (byte)0);
        // Expire any non-manual wasted objects that are over a year old
        long wasteExpireTime = Sage.time() - 52*Sage.MILLIS_PER_WEEK;
        for (int i = 0; i < wasteIndex.table.num; i++)
        {
          Wasted currWaste = (Wasted) wasteIndex.data[i];
          if (currWaste != null)
          {
            Airing wasteAir = currWaste.getAiring();
            if (wasteAir != null && wasteAir.isTV())
            {
              toSave.add(wasteAir);
            }
          }
        }
        mpause();
        // Retain all of the playlist airings even though they might not resolve to files, this is
        // because it takes work for the user to create these so we don't want to delete them automatically
        Playlist[] thePlaylists = getPlaylists();
        for (int i = 0; i < thePlaylists.length; i++)
        {
          for (int j = 0; j < thePlaylists[i].getNumSegments(); j++)
          {
            if (thePlaylists[i].getSegmentType(j) == Playlist.AIRING_SEGMENT)
            {
              toSave.add((Airing) thePlaylists[i].getSegment(j));
            }
          }
        }

        mpause();
        // Get all of the valid station IDs
        EPGDataSource[] epgdss = EPG.getInstance().getDataSources();
        Set<Integer> usedStations = new HashSet<Integer>();
        Set<Integer> viewableStations = new HashSet<Integer>();
        boolean removeAirsOnUnviewable = Sage.getBoolean("wizard/remove_airings_on_unviewable_channels", true);
        for (int i = 0; i < epgdss.length; i++)
        {
          int[] allStats = EPG.getInstance().getAllStations(epgdss[i].getProviderID());
          for (int j = 0; j < allStats.length; j++)
          {
            usedStations.add(allStats[j]);
            if (!removeAirsOnUnviewable || epgdss[i].canViewStation(allStats[j]))
              viewableStations.add(allStats[j]);
          }
        }
        usedStations.add(0); // for the Channel we use on imported MFs
        Set<Integer> viewableStationsWithoutAirings = new HashSet<Integer>(viewableStations);

        Sage.gcPause();

        boolean obeyAiringPersistence = Sage.getBoolean("wizard/retain_airings_from_completed_recordings", true);

        Index airIndex = getIndex(AIRING_CODE, AIRINGS_BY_CT_CODE);
        boolean removedThisAir;
        boolean removedLastAir = false;
        int currRemovalMask = 0;
        Airing lastAir = null;
        for (int i = 0; i < airIndex.table.num; i++)
        {
          Airing iAir = (Airing) airIndex.data[i];
          if (iAir != null && iAir.id < maintainStartID)
          {
            boolean saveTheAir = toSave.contains(iAir) || (obeyAiringPersistence && iAir.persist != 0);
						if (!saveTheAir && !viewableStations.contains(iAir.stationID))
            {
              // unlinked airings on unviewable stations
              removedThisAir = true;
            }
            else if (!saveTheAir && ((INVALID_AIRING_DURATIONS && noShowID == iAir.showID) ||
                (lastAir != null && lastAir.stationID == iAir.stationID && lastAir.getEndTime() > iAir.time)))
            {
              // Remove all No Data airings in this case so they can all be repaired;
              // also remove any overlapping airings due to the bug w/ 32-bit durations in Airings in compact DB mode
              removedThisAir = true;
            }
            else if (!saveTheAir && iAir.time < killTime && noShowID != iAir.showID)
            {
              // unlinked expired airings
              removedThisAir = true;
            }
            else if (!saveTheAir && iAir.time < killTime && noShowID == iAir.showID)
            {
              // We only remove old "No Data" airings if they're next to a hole
              // or next to another "No Data" airing (and we remove both of them)
              if (lastAir == null || iAir.stationID != lastAir.stationID)
              {
                // This noshow must be the first on the channel to be kept
                removedThisAir = iAir.time != 0;
              }
              else if (removedLastAir)
              {
                // Next to a hole, remove it
                removedThisAir = true;
              }
              else if (lastAir != null && lastAir.showID == noShowID)
              {
                // back to back no data's
                removedThisAir = true;
              }
              else
                removedThisAir = false;
            }
            else
              removedThisAir = false;

            if (removedThisAir)
            {
              toRemove.add(iAir);
              currRemovalMask = ((currRemovalMask| iAir.getMediaMask() ) & DBObject.MEDIA_MASK_ALL);
            }
            else if (iAir.stationID != 0)// don't remove the Channel if its in use
            {
              usedStations.add(iAir.stationID);
            }

            // Check if there's a hole after the last air and if its no data, if so
            // we need to remove it
            if (lastAir != null && lastAir.stationID == iAir.stationID && lastAir.showID == noShowID &&
                !removedLastAir && (removedThisAir || iAir.time > lastAir.getEndTime()))
            {
              toRemove.add(lastAir);
              currRemovalMask = ((currRemovalMask | lastAir.getMediaMask() ) & DBObject.MEDIA_MASK_ALL);
            }

            removedLastAir = removedThisAir;
            lastAir = iAir;
          }
          else
          {
            removedLastAir = false;
            lastAir = null;
          }
          if (i % 5000 == 0)
            mpause();
        }

        mpause();

        Table airTable = getTable(AIRING_CODE);
        if (Sage.DBG) System.out.println("Wizard removing " + toRemove.size() + " old airings of " +
            airTable.num + " total...");
        if (!toRemove.isEmpty())
        {
          airTable.massRemove(toRemove, logMaintenanceXcts);
          updateLastModified(currRemovalMask);
        }

        compressDBIfNeeded();
        waitUntilDBClientSyncsComplete();
        Sage.gcPause();

        // We need to fix all of the no data stuff here, consider it to
        // be completely screwed up at this point, essentially full of holes we need to plug.
        if (Sage.DBG) System.out.println("Wizard locating time holes in airing table...");
        List<Airing> noDataToFixDur = new ArrayList<Airing>();
        long noDataRuleCoverage = Sage.time() + noDataMaxRuleDur;
        lastAir = null;
        List<Airing> noShowAirsToAdd = new ArrayList<Airing>();
        for (int i = 0; i < airIndex.table.num; i++)
        {
          Airing iAir = (Airing) airIndex.data[i];
          if (iAir == null)
            break;
          // Do this AFTER we remove all the airings from the DB so we don't get tricked and leave stations
          // where all the airings got removed w/ nothing on them
          viewableStationsWithoutAirings.remove(iAir.stationID);
          if (lastAir == null || iAir.stationID != lastAir.stationID)
          {
            // Skip junk stations, there's only ever one airing on each of them anyhow
            // Changed the station, be sure we got the end of the last one
            if (lastAir != null && lastAir.getEndTime() < Long.MAX_VALUE/2 && lastAir.stationID != 0 &&
                viewableStations.contains(lastAir.stationID))
            {
              Airing noShowAdd = new Airing(getNextWizID());
              noShowAdd.showID = noShow.id;
              noShowAdd.stationID = lastAir.stationID;
              noShowAdd.time = lastAir.getEndTime();
              noShowAdd.duration = Long.MAX_VALUE/2 - noShowAdd.time;
              noShowAdd.setMediaMask(DBObject.MEDIA_MASK_TV);
              noShowAirsToAdd.add(noShowAdd);
              noDataToFixDur.add(noShowAdd);
            }
            if (iAir.time > 0 && iAir.stationID != 0 && viewableStations.contains(iAir.stationID))
            {
              Airing noShowAdd = new Airing(getNextWizID());
              noShowAdd.showID = noShow.id;
              noShowAdd.stationID = iAir.stationID;
              noShowAdd.time = 0;
              noShowAdd.duration = iAir.time;
              noShowAdd.setMediaMask(DBObject.MEDIA_MASK_TV);
              noShowAirsToAdd.add(noShowAdd);
              noDataToFixDur.add(noShowAdd);
            }
          }
          else if (lastAir.getEndTime() < iAir.time && iAir.stationID != 0 &&
              viewableStations.contains(iAir.stationID))
          {
            Airing noShowAdd = new Airing(getNextWizID());
            noShowAdd.showID = noShow.id;
            noShowAdd.stationID = iAir.stationID;
            noShowAdd.time = lastAir.getEndTime();
            noShowAdd.duration = iAir.time - noShowAdd.time;
            noShowAdd.setMediaMask(DBObject.MEDIA_MASK_TV);
            noShowAirsToAdd.add(noShowAdd);
            noDataToFixDur.add(noShowAdd);
          }
          if (iAir.showID == noShow.id && iAir.getDuration() > noDataMaxLen &&
              iAir.getStartTime() < noDataRuleCoverage && iAir.getEndTime() > Sage.time())
            noDataToFixDur.add(iAir);
          lastAir = iAir;

          if (i % 5000 == 0)
            mpause();
        }
        if (lastAir != null && lastAir.getEndTime() < Long.MAX_VALUE/2 && lastAir.stationID != 0 &&
            viewableStations.contains(lastAir.stationID))
        {
          Airing noShowAdd = new Airing(getNextWizID());
          noShowAdd.showID = noShow.id;
          noShowAdd.stationID = lastAir.stationID;
          noShowAdd.time = lastAir.getEndTime();
          noShowAdd.duration = Long.MAX_VALUE/2 - noShowAdd.time;
          noShowAdd.setMediaMask(DBObject.MEDIA_MASK_TV);
          noShowAirsToAdd.add(noShowAdd);
          noDataToFixDur.add(noShowAdd);
        }
        mpause();
        if (Sage.DBG) System.out.println("Wizard plugging the " + noShowAirsToAdd.size() + " time holes in the airing table...");
        for (int i = 0; i < noShowAirsToAdd.size(); i++)
        {
          airTable.add(noShowAirsToAdd.get(i), logMaintenanceXcts);
          if (i % 300 == 0 || getMaxPendingClientXcts() > 8)
            mpause();
          waitUntilDBClientSyncsComplete();
        }
        mpause();
        if (Sage.DBG) System.out.println("Wizard fixing duration for " + noDataToFixDur.size() + " noshow airings...");
        for (int i = 0; i < noDataToFixDur.size(); i++)
        {
          fixNoDataDuration(noDataToFixDur.get(i), logMaintenanceXcts);
          if (i % 300 == 0 || getMaxPendingClientXcts() > 8)
            mpause();
          waitUntilDBClientSyncsComplete();
        }

        Sage.gcPause();

        // Add "No Data" to channels that are viewable but don't have any EPG data (so we missed them above)
        for (int statID : viewableStationsWithoutAirings)
        {
          if (Sage.DBG) System.out.println("Resetting airings for station ID: " + statID);
          resetAirings(statID);
          if (getMaxPendingClientXcts() > 8)
            mpause();
          waitUntilDBClientSyncsComplete();
        }

        mpause();

        // Channel fixin'
        // Remove all of the Channels that have no airings associated with them.
        Index chanIdx = getIndex(CHANNEL_CODE);
        List<DBObject> killChans = new ArrayList<DBObject>();
        try {
          chanIdx.table.acquireReadLock();
          for (int i = 0; i < chanIdx.table.num; i++)
            if (!usedStations.contains(((Channel) chanIdx.data[i]).stationID) &&
                chanIdx.data[i].id < maintainStartID)
              killChans.add(chanIdx.data[i]);
        } finally {
          chanIdx.table.releaseReadLock();
        }
        mpause();
        if (Sage.DBG) System.out.println("Wizard removing " + killChans.size() + " expired Channels of " +
            chanIdx.table.num + " total...");
        for (int i = 0; i < killChans.size(); i++)
        {
          chanIdx.table.remove(killChans.get(i), logMaintenanceXcts);
        }
        if (!killChans.isEmpty())
        {
          updateLastModified(DBObject.MEDIA_MASK_TV);
        }
        killChans = null;
        mpause();

        if ( maintenanceType == MaintenanceType.FULL ){

          // Show fixin'
          // First remove all of the Shows that don't have any like or watch information and have no airings or editorials
          Index showIdx = getIndex(SHOW_CODE);
          Set<Show> killShows = new HashSet<Show>();
          currRemovalMask = 0;
          try {
            showIdx.table.acquireReadLock();
            for (int i = 0; i < showIdx.table.num; i++)
            {
              Show iShow = (Show) showIdx.data[i];
              if ((iShow.lastWatched == 0 || iShow.isMusic()) &&
                (iShow != noShow) && (iShow.id < maintainStartID) &&
                (getAirings(iShow, 0, true) == null) && (getEditorial(iShow) == null))
              {
                killShows.add(iShow);
                currRemovalMask = ((currRemovalMask | iShow.getMediaMask() ) & DBObject.MEDIA_MASK_ALL);
              }
            }
          } finally {
            showIdx.table.releaseReadLock();
          }
          mpause();
          if (Sage.DBG) System.out.println("Wizard removing " + killShows.size() + " expired Shows of " +
              showIdx.table.num + " total...");
          if (!killShows.isEmpty())
          {
            showIdx.table.massRemove(killShows, logMaintenanceXcts);
            updateLastModified(currRemovalMask);
          }
          killShows = null;
          mpause();
          compressDBIfNeeded();

          // Remove all unrated titles & people & categories & bonuses that aren't referenced by
          // a Show or Agent or editorial or SeriesInfo
          Set<Integer> keeperIDs = new HashSet<Integer>();
          try {
            showIdx.table.acquireReadLock();
            for (int i = 0; i < showIdx.table.num; i++)
            {
              Show iShow = (Show) showIdx.data[i];
              if (iShow.title != null)
                keeperIDs.add(iShow.title.id);
              for (int j = 0; j < iShow.categories.length; j++)
              {
                keeperIDs.add(iShow.categories[j].id);
              }
              for (int j = 0; j < iShow.people.length; j++)
              {
                keeperIDs.add(iShow.people[j].id);
              }
              for (int j = 0; j < iShow.bonuses.length; j++)
              {
                keeperIDs.add(iShow.bonuses[j].id);
              }
              if (iShow.language != null)
                keeperIDs.add(iShow.language.id);
            }
          } finally {
            showIdx.table.releaseReadLock();
          }
          mpause();
          // Keep all of the awards & birthplaces for People and all the aliases for people
          Index peopleIdx = getIndex(PEOPLE_CODE);
          try {
            peopleIdx.table.acquireReadLock();
            for (int i = 0; i < peopleIdx.table.num; i++)
            {
              Person p = (Person) peopleIdx.data[i];
              if (p.awardNames != null)
              {
                for (int j = 0; j < p.awardNames.length; j++)
                  keeperIDs.add(p.awardNames[j].id);
              }
              if (p.birthPlace != null)
                keeperIDs.add(p.birthPlace.id);
              if (p.extID < 0)
              {
                Person alias = p.getOriginalAlias();
                if (alias != null)
                  keeperIDs.add(alias.id);
              }
            }
          } finally {
            peopleIdx.table.releaseReadLock();
          }
          // Keep all of the Agent templates
          Index agentIdx = getIndex(AGENT_CODE);
          try {
            agentIdx.table.acquireReadLock();
            for (int i = 0; i < agentIdx.table.num; i++)
            {
              Agent bond = (Agent) agentIdx.data[i];
              if (bond.title != null)
                keeperIDs.add(bond.title.id);
              if (bond.person != null)
                keeperIDs.add(bond.person.id);
              if (bond.category != null)
                keeperIDs.add(bond.category.id);
              if (bond.subCategory != null)
                keeperIDs.add(bond.subCategory.id);
              // Agents don't use Bonus
            }
          } finally {
            agentIdx.table.releaseReadLock();
          }
          mpause();
          // Retain any titles, genres, people or years used by the Playlists in keeping an Album record
          thePlaylists = getPlaylists();
          for (int i = 0; i < thePlaylists.length; i++)
          {
            for (int j = 0; j < thePlaylists[i].getNumSegments(); j++)
            {
              if (thePlaylists[i].getSegmentType(j) == Playlist.ALBUM_SEGMENT)
              {
                Album allie = (Album) thePlaylists[i].getSegment(j);
                DBObject ts = allie.getTitleStringer();
                if (ts != null)
                  keeperIDs.add(ts.id);
                ts = allie.getArtistObj();
                if (ts != null)
                  keeperIDs.add(ts.id);
                ts = allie.getGenreStringer();
                if (ts != null)
                  keeperIDs.add(ts.id);
                ts = allie.getYearStringer();
                if (ts != null)
                  keeperIDs.add(ts.id);
              }
            }
          }
          mpause();
          // Retain any titles or networks used by TVEditorials, and remove any expired ones
          TVEditorial[] editorials = getEditorials();
          DateFormat editorialDateParser = new SimpleDateFormat("yyyy-MM-dd");
          for (int i = 0; i < editorials.length; i++)
          {
            try
            {
              Date theDate = editorialDateParser.parse(editorials[i].getAirdate());
              if (theDate != null && theDate.getTime() < (Sage.time() - Sage.MILLIS_PER_WEEK))
              {
                removeEditorial(editorials[i]);
                continue;
              }
            }
            catch (Exception e){}
            if (editorials[i].title != null)
              keeperIDs.add(editorials[i].title.id);
            if (editorials[i].network != null)
              keeperIDs.add(editorials[i].network.id);
          }
          mpause();
          // Retain any title/network/people used by SeriesInfo
          SeriesInfo[] allSeries = getAllSeriesInfo();
          for (int i = 0; i < allSeries.length; i++)
          {
            if (allSeries[i].title != null)
              keeperIDs.add(allSeries[i].title.id);
            if (allSeries[i].network != null)
              keeperIDs.add(allSeries[i].network.id);
            for (int j = 0; j < allSeries[i].people.length; j++)
              if (allSeries[i].people[j] != null)
                keeperIDs.add(allSeries[i].people[j].id);
          }
          mpause();

          if (Sage.DBG) System.out.println("Retaining " + keeperIDs.size() +
              " total title & people & category & bonus ids.");
          Set<DBObject> currRemoveSet = new HashSet<DBObject>();
          Index strIndex = getIndex(TITLE_CODE);
          currRemovalMask = 0;
          try {
            strIndex.table.acquireReadLock();
            for (int i = 0; i < strIndex.table.num; i++)
            {
              Stringer currStr = (Stringer) strIndex.data[i];
              if (!keeperIDs.contains(currStr.id) &&
                  currStr.id < maintainStartID)
              {
                currRemoveSet.add(currStr);
                currRemovalMask =((currRemovalMask | currStr.getMediaMask() ) & DBObject.MEDIA_MASK_ALL);
              }
            }
          } finally {
            strIndex.table.releaseReadLock();
          }
          if (Sage.DBG) System.out.println("Clearing out " + currRemoveSet.size() +
              " titles of " + strIndex.table.num + " total.");
          if (!currRemoveSet.isEmpty())
          {
            getTable(TITLE_CODE).massRemove(currRemoveSet, logMaintenanceXcts);
            updateLastModified(currRemovalMask);
            currRemoveSet.clear();
          }

          mpause();
          strIndex = getIndex(PRIME_TITLE_CODE);
          currRemovalMask = 0;
          try {
            strIndex.table.acquireReadLock();
            for (int i = 0; i < strIndex.table.num; i++)
            {
              Stringer currStr = (Stringer) strIndex.data[i];
              if (!keeperIDs.contains(currStr.id) &&
                  currStr.id < maintainStartID)
              {
                currRemoveSet.add(currStr);
                currRemovalMask = ((currRemovalMask | currStr.getMediaMask() ) & DBObject.MEDIA_MASK_ALL);
              }
            }
          } finally {
            strIndex.table.releaseReadLock();
          }
          if (Sage.DBG) System.out.println("Clearing out " + currRemoveSet.size() +
              " prime titles of " + strIndex.table.num + " total.");
          if (!currRemoveSet.isEmpty())
          {
            getTable(PRIME_TITLE_CODE).massRemove(currRemoveSet, logMaintenanceXcts);
            updateLastModified(currRemovalMask);
            currRemoveSet.clear();
          }
          mpause();

          // Keep the "Various" person constant we use
          Person var = getPersonForName(Sage.rez("Various_Artists"));
          if (var != null)
            keeperIDs.add(var.id);

          strIndex = getIndex(PEOPLE_CODE);
          currRemovalMask = 0;
          try {
            strIndex.table.acquireReadLock();
            for (int i = 0; i < strIndex.table.num; i++)
            {
              Person currStr = (Person) strIndex.data[i];
              if (!keeperIDs.contains(currStr.id) && currStr.id < maintainStartID)
              {
                currRemoveSet.add(currStr);
                currRemovalMask =((currRemovalMask | currStr.getMediaMask()) & DBObject.MEDIA_MASK_ALL);
              }
            }
          } finally {
            strIndex.table.releaseReadLock();
          }

          if (Sage.DBG) System.out.println("Clearing out " + currRemoveSet.size() +
              " people of " + strIndex.table.num + " total.");
          if (!currRemoveSet.isEmpty())
          {
            getTable(PEOPLE_CODE).massRemove(currRemoveSet, logMaintenanceXcts);
            updateLastModified(currRemovalMask);
            currRemoveSet.clear();
          }
          mpause();

          strIndex = getIndex(CATEGORY_CODE);
          currRemovalMask = 0;
          try {
            strIndex.table.acquireReadLock();
            for (int i = 0; i < strIndex.table.num; i++)
            {
              Stringer currStr = (Stringer) strIndex.data[i];
              if (!keeperIDs.contains(currStr.id) && currStr.id < maintainStartID)
              {
                currRemoveSet.add(currStr);
                currRemovalMask =  ((currRemovalMask | currStr.getMediaMask() ) & DBObject.MEDIA_MASK_ALL);
              }
            }
          } finally {
            strIndex.table.releaseReadLock();
          }
          if (Sage.DBG) System.out.println("Clearing out " + currRemoveSet.size() +
              " categories of " + strIndex.table.num + " total.");
          if (!currRemoveSet.isEmpty())
          {
            getTable(CATEGORY_CODE).massRemove(currRemoveSet, logMaintenanceXcts);
            updateLastModified(currRemovalMask);
            currRemoveSet.clear();
          }
          mpause();

          strIndex = getIndex(SUBCATEGORY_CODE);
          currRemovalMask = 0;
          try {
            strIndex.table.acquireReadLock();
            for (int i = 0; i < strIndex.table.num; i++)
            {
              Stringer currStr = (Stringer) strIndex.data[i];
              if (!keeperIDs.contains(currStr.id) && currStr.id < maintainStartID)
              {
                currRemoveSet.add(currStr);
                currRemovalMask = ((currRemovalMask | currStr.getMediaMask() ) & DBObject.MEDIA_MASK_ALL);
              }
            }
          } finally {
            strIndex.table.releaseReadLock();
          }
          if (Sage.DBG) System.out.println("Clearing out " + currRemoveSet.size() +
              " subcategories of " + strIndex.table.num + " total.");
          if (!currRemoveSet.isEmpty())
          {
            getTable(SUBCATEGORY_CODE).massRemove(currRemoveSet, logMaintenanceXcts);
            updateLastModified(currRemovalMask);
            currRemoveSet.clear();
          }
          mpause();

          strIndex = getIndex(BONUS_CODE);
          currRemovalMask = 0;
          try {
            strIndex.table.acquireReadLock();
            for (int i = 0; i < strIndex.table.num; i++)
            {
              Stringer currStr = (Stringer) strIndex.data[i];
              if (!keeperIDs.contains(currStr.id) && currStr.id < maintainStartID)
              {
                currRemoveSet.add(currStr);
                currRemovalMask =  ((currRemovalMask | currStr.getMediaMask() ) & DBObject.MEDIA_MASK_ALL);
              }
            }
          } finally {
            strIndex.table.releaseReadLock();
          }

          if (Sage.DBG) System.out.println("Clearing out " + currRemoveSet.size() +
              " bonuses of " + strIndex.table.num + " total.");
          if (!currRemoveSet.isEmpty())
          {
            getTable(BONUS_CODE).massRemove(currRemoveSet, logMaintenanceXcts);
            updateLastModified(currRemovalMask);
            currRemoveSet.clear();
          }
          mpause();

          compressDBIfNeeded();

          /*
           * NEW MAINTENANCE:
           * With the addition of the mediaMaskB to DBObject, we are also responsible now for maintaining
           * the correct value of that mask as it changes due to garbage collection or other changes in the MF/Airing/Show linkage.
           *
           * We can be sure that the MediaFile masks are correct. We can also be sure that all of the Airing masks are correct.
           * We know this because Airings are always created with the proper mask EXCEPT when they're linked to a MediaFile
           * after their creation (i.e. the MF didn't create the Airing). And this is fixed in MF when that linkage is done. It should
           * also be mentioned that when a MF is created based on an existing TV airing; that Airing does NOT have its mask updated
           * to include the Video bit. However, if the MF is created and then the TV airing is linked to it, the Video + TV bits will
           * then be propogated down.  So we can have a slight ambiguity in the Video bit for DBObjects....but this is also useful
           * because the Video bit will only be set on Airings that are actually imported videos OR are MFs that had their Airings messed
           * with through a plugin.
           *
           * Watched objects don't really matter for media mask maintenance, they're already GC'd.
           *
           * Show & Stringer are the objects that need to be updated. We can do each Show pretty easily because we can
           * just go through the Airings by Show Code index in parallel with the Show table and update them all that way by
           * checking what kind of Airings we have for each Show. We'll need to lock the Airing table when we do this, but this
           * operation should move fairly quickly (no worse than an index sort I'd think) so it should be fine. We'll also
           * need to lock the Show table so it's not modified during this process either. An Airing->Show nested lock is safe (I think).
           * We'll also need to clear all of the existing Show masks before we re-generate them.
           *
           * For the Stringers, we'll have to lock the Show table while we process each Stringer type, and also
           * lock that Stringer's table too. (we do that same nested locking when adding a Show, so it's OK)
           * Then we can clear all the masks and just regenerate them in-place.
           */
          airIndex = getIndex(AIRING_CODE, AIRINGS_BY_SHOW_CODE);
          if (Sage.DBG) System.out.println("Wizard is fixing the media mask on the Show objects...");
          long startMediaMaskTime = 0;
          long mediaMaskUpdateTime;
          int totalUpdates;
          int currentUpdates;
          int loops;

          loops = 0;
          totalUpdates = 0;
          mediaMaskUpdateTime = 0;
          startMediaMaskTime = Sage.eventTime();
          long lockTime = 0;
          long lockStartTime = 0;
          // We only grab the locks below when we are actually going to change something. This operation previously was
          // holding DB locks for at least 4 seconds and sometimes up to 15 seconds, which is way too long. So we removed
          // holding the locks and now have mod counters on the DB tables. If nothing has changed in the tables, then we know
          // we are safe with our analysis. If something did change, we detect that and then start the whole process over.
          while(true) {
            loops++;
            int iair = 0;
            int ishow = 0;
            int currMask = 0;
            Show lastShow = null;
            long airModCount = airIndex.table.getModCount();
            long showModCount = showIdx.table.getModCount();
            while (iair < airIndex.table.num && ishow < showIdx.table.num)
            {
              if (airIndex.table.getModCount() != airModCount ||
                  showIdx.table.getModCount() != showModCount)
              {
                break;
              }
              Airing currAir = (Airing) airIndex.data[iair];
              Show currShow = (Show) showIdx.data[ishow];
              if (currAir == null)
              {
                iair++;
                continue;
              }
              if (currShow == null)
              {
                ishow++;
                continue;
              }
              if (currAir.showID > currShow.id)
              {
                if (lastShow != null && lastShow.getMediaMask() != currMask)
                {
                  lockStartTime = Sage.eventTime();
                  try {
                    showIdx.table.acquireWriteLock();
                    try {
                      airIndex.table.acquireReadLock();
                      if (airIndex.table.getModCount() != airModCount ||
                          showIdx.table.getModCount() != showModCount)
                      {
                        break;
                      }
                      lastShow.setMediaMask(currMask);
                      logUpdate(lastShow, SHOW_CODE);
                      showModCount = showIdx.table.getModCount();
                    } finally {
                      airIndex.table.releaseReadLock();
                    }
                  } finally {
                    showIdx.table.releaseWriteLock();
                  }
                  lockTime += Sage.eventTime() - lockStartTime;
                  totalUpdates++;
                }
                ishow++;
                lastShow = null;
                currMask = 0;
              }
              else if (currAir.showID < currShow.id)
              {
                if (lastShow != null && lastShow.getMediaMask() != currMask)
                {
                  lockStartTime = Sage.eventTime();
                  try {
                    showIdx.table.acquireWriteLock();
                    try {
                      airIndex.table.acquireReadLock();
                      if (airIndex.table.getModCount() != airModCount ||
                          showIdx.table.getModCount() != showModCount)
                      {
                        break;
                      }
                      lastShow.setMediaMask(currMask);
                      logUpdate(lastShow, SHOW_CODE);
                      showModCount = showIdx.table.getModCount();
                    } finally {
                      airIndex.table.releaseReadLock();
                    }
                  } finally {
                    showIdx.table.releaseWriteLock();
                  }
                  lockTime += Sage.eventTime() - lockStartTime;
                  totalUpdates++;
                }
                iair++;
                lastShow = null;
                currMask = 0;
              }
              else
              {
                currMask =((currMask | currAir.getMediaMask() ) & DBObject.MEDIA_MASK_ALL);
                iair++;
                lastShow = currShow;
              }
              if (getMaxPendingClientXcts() > 8)
                mpause();
            }
            if (airIndex.table.getModCount() != airModCount)
            {
              if (Sage.DBG) System.out.println("Airing table was modified during media mask fixing...sleep and then retry...");
              try{Thread.sleep(WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME);}catch(Exception e){}
              continue;
            }
            if (showIdx.table.getModCount() != showModCount)
            {
              if (Sage.DBG) System.out.println("Show table was modified during media mask fixing...sleep and then retry...");
              try{Thread.sleep(WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME);}catch(Exception e){}
              continue;
            }
            if (lastShow != null && lastShow.getMediaMask() != currMask)
            {
              lockStartTime = Sage.eventTime();
              try {
                showIdx.table.acquireWriteLock();
                try {
                  airIndex.table.acquireReadLock();
                  if (airIndex.table.getModCount() != airModCount ||
                      showIdx.table.getModCount() != showModCount)
                  {
                    continue;
                  }
                  lastShow.setMediaMask(currMask);
                  logUpdate(lastShow, SHOW_CODE);
                  showModCount = showIdx.table.getModCount();
                } finally {
                  airIndex.table.releaseReadLock();
                }
              } finally {
                showIdx.table.releaseWriteLock();
              }
              lockTime += Sage.eventTime() - lockStartTime;
              totalUpdates++;
            }
            // Final check that nothing was changed at all
            if (airIndex.table.getModCount() != airModCount ||
                showIdx.table.getModCount() != showModCount)
              continue;
            else
              break; // we get to here and we're done.
          }
          System.out.println("MaintenceMetric(MediaMaskUpdateShow): " + (Sage.eventTime() - startMediaMaskTime) + "ms for " +
              totalUpdates + " items in " + loops + " loops, lock time " + lockTime + " ms");
          waitUntilDBClientSyncsComplete();
          mpause();

          // We create this array to use for figuring out which media masks actually changed so we only distribute
          // updates on changed objects, otherwise we can lock these tables for too long if there are connected
          // clients.
          int[] mmCache = new int[100000];

          if (Sage.DBG) System.out.println("Wizard is fixing the media mask on the Titles...");
          strIndex = getIndex(TITLE_CODE);
          loops = 0;
          mediaMaskUpdateTime = 0;
          totalUpdates = 0;
          while(true) {
            currentUpdates = 0;
            loops++;
            try {
              try {
                showIdx.table.acquireReadLock();
                try {
                  strIndex.table.acquireWriteLock();
                  // First copy all of the masks in this table, reallocate cache array if not big enough
                  // Also zero out all of the existing ones so they can be created fresh
                  if (mmCache.length < strIndex.table.num)
                    mmCache = new int[strIndex.table.num];
                  for (int i = 0; i < strIndex.table.num; i++) {
                    if (strIndex.data[i] != null) {
                      mmCache[i] = strIndex.data[i].getMediaMask();
                      strIndex.data[i].setMediaMask(0);
                    }
                  }
                  // Now propagate all of the Show media masks down to the title table
                  for (int i = 0; i < showIdx.table.num; i++) {
                    Show currShow = (Show) showIdx.data[i];
                    if (currShow != null && currShow.getMediaMask() != 0 && currShow.title != null) {
                      if (!currShow.title.hasMediaMask(currShow.getMediaMask())) {
                        currShow.title.addMediaMask(currShow.getMediaMask());
                      }
                    }
                  }
                  startMediaMaskTime = Sage.eventTime();
                  // Now go through and distribute the actual updates for any changes
                  // that occurred
                  for (int i = 0; i < strIndex.table.num; i++) {
                    if (strIndex.data[i] != null && strIndex.data[i].getMediaMask() != mmCache[i]) {
                      logUpdate(strIndex.data[i], TITLE_CODE);
                      currentUpdates++;
                      if((Sage.eventTime() - startMediaMaskTime) > WIZARD_MEDIAMASK_MAINTENANCE_LOCK_TIME) {
                        // Before breaking out, reset the media masks moving forward from this element.
                        // By doing this, we're inherently undoing the propagation that was done to each
                        // show/element above that didn't get logged.
                        for (i++; i < strIndex.table.num; i++)
                          if (strIndex.data[i] != null)
                            strIndex.data[i].setMediaMask(mmCache[i]);
                        throw new InterruptedException();
                      }
                      if (getMaxPendingClientXcts() > 8)
                        mpause();
                    }
                  }
                } finally {
                  strIndex.table.releaseWriteLock();
                }
              } finally {
                showIdx.table.releaseReadLock();
              }
            } catch (InterruptedException e) {
              totalUpdates += currentUpdates;
              mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
              System.out.println("MaintenceMetric(MediaMaskUpdateTitle): " + currentUpdates + " / " + totalUpdates + " items in " + loops + " loops; ");
              try { Thread.sleep(WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME); } catch (InterruptedException ignore) {}
              continue;
            }
            totalUpdates += currentUpdates;
            mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
            break; // done
          }
          System.out.println("MaintenceMetric(MediaMaskUpdateTitle): " + mediaMaskUpdateTime + "ms for " + totalUpdates + " items in " + loops + " loops");
          waitUntilDBClientSyncsComplete();
          mpause();

          if (Sage.DBG) System.out.println("Wizard is fixing the media mask on the Years...");
          strIndex = getIndex(YEAR_CODE);
          loops = 0;
          mediaMaskUpdateTime = 0;
          totalUpdates = 0;
          while(true) {
            currentUpdates = 0;
            loops++;
            try {
              try {
                showIdx.table.acquireReadLock();
                try {
                  strIndex.table.acquireWriteLock();
                  // First copy all of the masks in this table, reallocate cache array if not big enough
                  // Also zero out all of the existing ones so they can be created fresh
                  if (mmCache.length < strIndex.table.num)
                    mmCache = new int[strIndex.table.num];
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null)
                    {
                      mmCache[i] = strIndex.data[i].getMediaMask();
                      strIndex.data[i].setMediaMask(0);
                    }
                  }
                  // Now propagate all of the Show media masks down to the year table
                  for (int i = 0; i < showIdx.table.num; i++)
                  {
                    Show currShow = (Show) showIdx.data[i];
                    if (currShow != null && currShow.getMediaMask() != 0 && currShow.year != null)
                    {
                      if (!currShow.year.hasMediaMask(currShow.getMediaMask()))
                      {
                        currShow.year.addMediaMask(currShow.getMediaMask());
                      }
                    }
                  }
                  startMediaMaskTime = Sage.eventTime();
                  // Now go through and distribute the actual updates for any changes
                  // that occurred
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null && strIndex.data[i].getMediaMask() != mmCache[i])
                    {
                      logUpdate(strIndex.data[i], YEAR_CODE);
                      currentUpdates++;
                      if((Sage.eventTime() - startMediaMaskTime) > WIZARD_MEDIAMASK_MAINTENANCE_LOCK_TIME) {
                        // Before breaking out, reset the media masks moving forward from this element.
                        // By doing this, we're inherently undoing the propagation that was done to each
                        // show/element above that didn't get logged.
                        for (i++; i < strIndex.table.num; i++)
                          if (strIndex.data[i] != null)
                            strIndex.data[i].setMediaMask(mmCache[i]);
                        throw new InterruptedException();
                      }
                    }
                  }
                } finally {
                  strIndex.table.releaseWriteLock();
                }
              } finally {
                showIdx.table.releaseReadLock();
              }
            } catch (InterruptedException e) {
              totalUpdates += currentUpdates;
              mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
              System.out.println("MaintenceMetric(MediaMaskUpdateYears): " + currentUpdates + " / " + totalUpdates + " items in " + loops + " loops; ");
              try { Thread.sleep(WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME); } catch (InterruptedException ignore) {}
              continue;
            }
            totalUpdates += currentUpdates;
            mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
            break; // done if we get to here
          }
          System.out.println("MaintenceMetric(MediaMaskUpdateYears): " + mediaMaskUpdateTime + "ms for " + totalUpdates + " items in " + loops + " loops");
          mpause();
          waitUntilDBClientSyncsComplete();

          if (Sage.DBG) System.out.println("Wizard is fixing the media mask on the Peoples...");
          strIndex = getIndex(PEOPLE_CODE);
          loops = 0;
          mediaMaskUpdateTime = 0;
          totalUpdates = 0;
          while(true) {
            currentUpdates = 0;
            loops++;
            try {
              try {
                showIdx.table.acquireReadLock();
                try {
                  strIndex.table.acquireWriteLock();
                  // First copy all of the masks in this table, reallocate cache array if not big enough
                  // Also zero out all of the existing ones so they can be created fresh
                  if (mmCache.length < strIndex.table.num)
                    mmCache = new int[strIndex.table.num];
                  for (int i = 0; i < strIndex.table.num; i++) {
                    if (strIndex.data[i] != null) {
                      mmCache[i] = strIndex.data[i].getMediaMask();
                      strIndex.data[i].setMediaMask(0);
                    }
                  }
                  // Now propagate all of the Show media masks down to the people table
                  for (int i = 0; i < showIdx.table.num; i++)
                  {
                    Show currShow = (Show) showIdx.data[i];
                    if (currShow != null && currShow.getMediaMask() != 0 &&
                        currShow.people != null && currShow.people.length > 0) {
                      for (int j = 0; j < currShow.people.length; j++) {
                        if (!currShow.people[j].hasMediaMask(currShow.getMediaMask())) {
                          currShow.people[j].addMediaMask(currShow.getMediaMask());
                        }
                      }
                    }
                  }
                  startMediaMaskTime = Sage.eventTime();
                  // Now go through and distribute the actual updates for any changes
                  // that occurred
                  for (int i = 0; i < strIndex.table.num; i++) {
                    if (strIndex.data[i] != null && strIndex.data[i].getMediaMask() != mmCache[i]) {
                      logUpdate(strIndex.data[i], PEOPLE_CODE);
                      currentUpdates++;
                      if((Sage.eventTime() - startMediaMaskTime) > WIZARD_MEDIAMASK_MAINTENANCE_LOCK_TIME) {
                        // Before breaking out, reset the media masks moving forward from this element.
                        // By doing this, we're inherently undoing the propagation that was done to each
                        // show/element above that didn't get logged.
                        for (i++; i < strIndex.table.num; i++)
                          if (strIndex.data[i] != null)
                            strIndex.data[i].setMediaMask(mmCache[i]);
                        throw new InterruptedException();
                      }
                      if (getMaxPendingClientXcts() > 8)
                        mpause();
                    }
                  }
                } finally {
                  strIndex.table.releaseWriteLock();
                }
              } finally {
                showIdx.table.releaseReadLock();
              }
            } catch (InterruptedException e) {
              totalUpdates += currentUpdates;
              mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
              System.out.println("MaintenceMetric(MediaMaskUpdatePeoples): " + currentUpdates + " / " + totalUpdates + " items in " + loops + " loops; ");
              try { Thread.sleep(WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME); } catch (InterruptedException ignore) {}
              continue;
            }
            totalUpdates += currentUpdates;
            mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
            break; // done
          }
          System.out.println("MaintenceMetric(MediaMaskUpdatePeoples): " + mediaMaskUpdateTime + "ms for " + totalUpdates + " items in " + loops + " loops");
          mpause();
          waitUntilDBClientSyncsComplete();

          if (Sage.DBG) System.out.println("Wizard is fixing the media mask on the Categories...");
          strIndex = getIndex(CATEGORY_CODE);
          loops = 0;
          mediaMaskUpdateTime = 0;
          totalUpdates = 0;
          mediaMaskUpdateTime = 0;
          while(true) {
            currentUpdates = 0;
            loops++;
            try {
             try {
                showIdx.table.acquireReadLock();
                try {
                  strIndex.table.acquireWriteLock();
                   // First copy all of the masks in this table, reallocate cache array if not big enough
                  // Also zero out all of the existing ones so they can be created fresh
                  if (mmCache.length < strIndex.table.num)
                    mmCache = new int[strIndex.table.num];
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null)
                    {
                      mmCache[i] = strIndex.data[i].getMediaMask();
                      strIndex.data[i].setMediaMask(0);
                    }
                  }
                  // Now propagate all of the Show media masks down to the stringer table
                  for (int i = 0; i < showIdx.table.num; i++)
                  {
                    Show currShow = (Show) showIdx.data[i];
                    if (currShow != null && currShow.getMediaMask() != 0 && currShow.categories.length > 0)
                    {
                      if (!currShow.categories[0].hasMediaMask(currShow.getMediaMask()))
                      {
                        currShow.categories[0].addMediaMask(currShow.getMediaMask());
                      }
                    }
                  }
                  startMediaMaskTime = Sage.eventTime();
                  // Now go through and distribute the actual updates for any changes
                  // that occurred
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null && strIndex.data[i].getMediaMask() != mmCache[i])
                    {
                      logUpdate(strIndex.data[i], CATEGORY_CODE);
                      currentUpdates++;
                      if((Sage.eventTime() - startMediaMaskTime) > WIZARD_MEDIAMASK_MAINTENANCE_LOCK_TIME) {
                        // Before breaking out, reset the media masks moving forward from this element.
                        // By doing this, we're inherently undoing the propagation that was done to each
                        // show/element above that didn't get logged.
                        for (i++; i < strIndex.table.num; i++)
                          if (strIndex.data[i] != null)
                            strIndex.data[i].setMediaMask(mmCache[i]);
                        throw new InterruptedException();
                      }
                    }
                  }
                } finally {
                  strIndex.table.releaseWriteLock();
                }
              } finally {
                showIdx.table.releaseReadLock();
              }
            } catch (InterruptedException e) {
              totalUpdates += currentUpdates;
              mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
              System.out.println("MaintenceMetric(MediaMaskUpdateCategories): " + currentUpdates + " / " + totalUpdates + " items in " + loops + " loops; ");
              try { Thread.sleep(WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME); } catch (InterruptedException ignore) {}
              continue;
            }
            totalUpdates += currentUpdates;
            mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
            break; // done
          }
          System.out.println("MaintenceMetric(MediaMaskUpdateCategories): " + mediaMaskUpdateTime + "ms for " + totalUpdates + " items in " + loops + " loops");
          mpause();
          waitUntilDBClientSyncsComplete();

          if (Sage.DBG) System.out.println("Wizard is fixing the media mask on the SubCategories...");
          strIndex = getIndex(SUBCATEGORY_CODE);
          loops = 0;
          mediaMaskUpdateTime = 0;
          totalUpdates = 0;
          mediaMaskUpdateTime = 0;
          while(true) {
            currentUpdates = 0;
            loops++;
            try {
             try {
                showIdx.table.acquireReadLock();
                try {
                  strIndex.table.acquireWriteLock();
                   // First copy all of the masks in this table, reallocate cache array if not big enough
                  // Also zero out all of the existing ones so they can be created fresh
                  if (mmCache.length < strIndex.table.num)
                    mmCache = new int[strIndex.table.num];
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null)
                    {
                      mmCache[i] = strIndex.data[i].getMediaMask();
                      strIndex.data[i].setMediaMask(0);
                    }
                  }
                  // Now propagate all of the Show media masks down to the stringer table
                  for (int i = 0; i < showIdx.table.num; i++)
                  {
                    Show currShow = (Show) showIdx.data[i];
                    if (currShow != null && currShow.getMediaMask() != 0 &&
                        currShow.categories != null && currShow.categories.length > 1)
                    {
                      for (int j = 1; j < currShow.categories.length; j++)
                      {
                        if (!currShow.categories[j].hasMediaMask(currShow.getMediaMask()))
                        {
                          currShow.categories[j].addMediaMask(currShow.getMediaMask());
                        }
                      }
                    }
                  }
                  startMediaMaskTime = Sage.eventTime();
                  // Now go through and distribute the actual updates for any changes
                  // that occurred
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null && strIndex.data[i].getMediaMask() != mmCache[i])
                    {
                      logUpdate(strIndex.data[i], SUBCATEGORY_CODE);
                      currentUpdates++;
                      if((Sage.eventTime() - startMediaMaskTime) > WIZARD_MEDIAMASK_MAINTENANCE_LOCK_TIME) {
                        // Before breaking out, reset the media masks moving forward from this element.
                        // By doing this, we're inherently undoing the propagation that was done to each
                        // show/element above that didn't get logged.
                        for (i++; i < strIndex.table.num; i++)
                          if (strIndex.data[i] != null)
                            strIndex.data[i].setMediaMask(mmCache[i]);
                        throw new InterruptedException();
                      }
                    }
                  }
               } finally {
                  strIndex.table.releaseWriteLock();
                }
              } finally {
                showIdx.table.releaseReadLock();
              }
             } catch (InterruptedException e) {
              totalUpdates += currentUpdates;
              mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
              System.out.println("MaintenceMetric(MediaMaskUpdateSubCategories): " + currentUpdates + " / " + totalUpdates + " items in " + loops + " loops; ");
              try { Thread.sleep(WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME); } catch (InterruptedException ignore) {}
              continue;
            }
            totalUpdates += currentUpdates;
            mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
            break; // done
          }
          System.out.println("MaintenceMetric(MediaMaskUpdateSubCategories): " + mediaMaskUpdateTime + "ms for " + totalUpdates + " items in " + loops + " loops");
          mpause();
          waitUntilDBClientSyncsComplete();

          if (Sage.DBG) System.out.println("Wizard is fixing the media mask on the Rated...");
          strIndex = getIndex(RATED_CODE);
          loops = 0;
          mediaMaskUpdateTime = 0;
          totalUpdates = 0;
          mediaMaskUpdateTime = 0;
          while(true) {
            currentUpdates = 0;
            loops++;
            try {
             try {
                showIdx.table.acquireReadLock();
                try {
                  strIndex.table.acquireWriteLock();
                   // First copy all of the masks in this table, reallocate cache array if not big enough
                  // Also zero out all of the existing ones so they can be created fresh
                  if (mmCache.length < strIndex.table.num)
                    mmCache = new int[strIndex.table.num];
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null)
                    {
                      mmCache[i] = strIndex.data[i].getMediaMask();
                      strIndex.data[i].setMediaMask(0);
                    }
                  }
                  // Now propagate all of the Show media masks down to the stringer table
                  for (int i = 0; i < showIdx.table.num; i++)
                  {
                    Show currShow = (Show) showIdx.data[i];
                    if (currShow != null && currShow.getMediaMask() != 0 && currShow.rated != null)
                    {
                      if (!currShow.rated.hasMediaMask(currShow.getMediaMask()))
                      {
                        currShow.rated.addMediaMask(currShow.getMediaMask());
                      }
                    }
                  }
                  startMediaMaskTime = Sage.eventTime();
                  // Now go through and distribute the actual updates for any changes
                  // that occurred
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null && strIndex.data[i].getMediaMask() != mmCache[i])
                    {
                      logUpdate(strIndex.data[i], RATED_CODE);
                      currentUpdates++;
                      if((Sage.eventTime() - startMediaMaskTime) > WIZARD_MEDIAMASK_MAINTENANCE_LOCK_TIME) {
                        // Before breaking out, reset the media masks moving forward from this element.
                        // By doing this, we're inherently undoing the propagation that was done to each
                        // show/element above that didn't get logged.
                        for (i++; i < strIndex.table.num; i++)
                          if (strIndex.data[i] != null)
                            strIndex.data[i].setMediaMask(mmCache[i]);
                        throw new InterruptedException();
                      }
                    }
                  }
               } finally {
                  strIndex.table.releaseWriteLock();
                }
              } finally {
                showIdx.table.releaseReadLock();
              }
             } catch (InterruptedException e) {
              totalUpdates += currentUpdates;
              mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
              System.out.println("MaintenceMetric(MediaMaskUpdateRated): " + currentUpdates + " / " + totalUpdates + " items in " + loops + " loops; ");
              try { Thread.sleep(WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME); } catch (InterruptedException ignore) {}
              continue;
            }
            totalUpdates += currentUpdates;
            mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
            break; // done
          }
          System.out.println("MaintenceMetric(MediaMaskUpdateRated): " + mediaMaskUpdateTime + "ms for " + totalUpdates + " items in " + loops + " loops");
          mpause();
          waitUntilDBClientSyncsComplete();

          if (Sage.DBG) System.out.println("Wizard is fixing the media mask on the PR...");
          strIndex = getIndex(PR_CODE);
          loops = 0;
          mediaMaskUpdateTime = 0;
          totalUpdates = 0;
          mediaMaskUpdateTime = 0;
          while(true) {
            currentUpdates = 0;
            loops++;
            try {
             try {
                showIdx.table.acquireReadLock();
                try {
                  strIndex.table.acquireWriteLock();
                   // First copy all of the masks in this table, reallocate cache array if not big enough
                  // Also zero out all of the existing ones so they can be created fresh
                  if (mmCache.length < strIndex.table.num)
                    mmCache = new int[strIndex.table.num];
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null)
                    {
                      mmCache[i] = strIndex.data[i].getMediaMask();
                      strIndex.data[i].setMediaMask(0);
                    }
                  }
                  // Now propagate all of the Show media masks down to the stringer table
                  for (int i = 0; i < showIdx.table.num; i++)
                  {
                    Show currShow = (Show) showIdx.data[i];
                    if (currShow != null && currShow.getMediaMask() != 0 && currShow.pr != null)
                    {
                      if (!currShow.pr.hasMediaMask(currShow.getMediaMask()))
                      {
                        currShow.pr.addMediaMask(currShow.getMediaMask());
                      }
                    }
                  }
                  startMediaMaskTime = Sage.eventTime();
                  // Now go through and distribute the actual updates for any changes
                  // that occurred
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null && strIndex.data[i].getMediaMask() != mmCache[i])
                    {
                      logUpdate(strIndex.data[i], PR_CODE);
                      currentUpdates++;
                      if((Sage.eventTime() - startMediaMaskTime) > WIZARD_MEDIAMASK_MAINTENANCE_LOCK_TIME) {
                        // Before breaking out, reset the media masks moving forward from this element.
                        // By doing this, we're inherently undoing the propagation that was done to each
                        // show/element above that didn't get logged.
                        for (i++; i < strIndex.table.num; i++)
                          if (strIndex.data[i] != null)
                            strIndex.data[i].setMediaMask(mmCache[i]);
                        throw new InterruptedException();
                      }
                    }
                  }
               } finally {
                  strIndex.table.releaseWriteLock();
                }
              } finally {
                showIdx.table.releaseReadLock();
              }
             } catch (InterruptedException e) {
              totalUpdates += currentUpdates;
              mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
              System.out.println("MaintenceMetric(MediaMaskUpdatePRs): " + currentUpdates + " / " + totalUpdates + " items in " + loops + " loops; ");
              try { Thread.sleep(WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME); } catch (InterruptedException ignore) {}
              continue;
            }
            totalUpdates += currentUpdates;
            mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
            break; // done
          }
          System.out.println("MaintenceMetric(MediaMaskUpdatePRs): " + mediaMaskUpdateTime + "ms for " + totalUpdates + " items in " + loops + " loops");
          mpause();
          waitUntilDBClientSyncsComplete();

          if (Sage.DBG) System.out.println("Wizard is fixing the media mask on the ERs...");
          strIndex = getIndex(ER_CODE);
          loops = 0;
          mediaMaskUpdateTime = 0;
          totalUpdates = 0;
          mediaMaskUpdateTime = 0;
          while(true) {
            currentUpdates = 0;
            loops++;
            try {
             try {
                showIdx.table.acquireReadLock();
                try {
                  strIndex.table.acquireWriteLock();
                   // First copy all of the masks in this table, reallocate cache array if not big enough
                  // Also zero out all of the existing ones so they can be created fresh
                  if (mmCache.length < strIndex.table.num)
                    mmCache = new int[strIndex.table.num];
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null)
                    {
                      mmCache[i] = strIndex.data[i].getMediaMask();
                      strIndex.data[i].setMediaMask(0);
                    }
                  }
                  // Now propogate all of the Show media masks down to the people table
                  for (int i = 0; i < showIdx.table.num; i++)
                  {
                    Show currShow = (Show) showIdx.data[i];
                    if (currShow != null && currShow.getMediaMask() != 0 &&
                        currShow.ers != null && currShow.ers.length > 0)
                    {
                      for (int j = 0; j < currShow.ers.length; j++)
                      {
                        if (!currShow.ers[j].hasMediaMask(currShow.getMediaMask()))
                        {
                          currShow.ers[j].addMediaMask(currShow.getMediaMask());
                        }
                      }
                    }
                  }
                  startMediaMaskTime = Sage.eventTime();
                  // Now go through and distribute the actual updates for any changes
                  // that occurred
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null && strIndex.data[i].getMediaMask() != mmCache[i])
                    {
                      logUpdate(strIndex.data[i], ER_CODE);
                      currentUpdates++;
                      if((Sage.eventTime() - startMediaMaskTime) > WIZARD_MEDIAMASK_MAINTENANCE_LOCK_TIME) {
                        // Before breaking out, reset the media masks moving forward from this element.
                        // By doing this, we're inherently undoing the propagation that was done to each
                        // show/element above that didn't get logged.
                        for (i++; i < strIndex.table.num; i++)
                          if (strIndex.data[i] != null)
                            strIndex.data[i].setMediaMask(mmCache[i]);
                        throw new InterruptedException();
                      }
                    }
                  }
               } finally {
                  strIndex.table.releaseWriteLock();
                }
              } finally {
                showIdx.table.releaseReadLock();
              }
             } catch (InterruptedException e) {
              totalUpdates += currentUpdates;
              mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
              System.out.println("MaintenceMetric(MediaMaskUpdateERs): " + currentUpdates + " / " + totalUpdates + " items in " + loops + " loops; ");
              try { Thread.sleep(WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME); } catch (InterruptedException ignore) {}
              continue;
            }
            totalUpdates += currentUpdates;
            mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
            break; // done
          }
          System.out.println("MaintenceMetric(MediaMaskUpdateERs): " + mediaMaskUpdateTime + "ms for " + totalUpdates + " items in " + loops + " loops");
          mpause();
          waitUntilDBClientSyncsComplete();

          if (Sage.DBG) System.out.println("Wizard is fixing the media mask on the Bonus...");
          strIndex = getIndex(BONUS_CODE);
          loops = 0;
          mediaMaskUpdateTime = 0;
          totalUpdates = 0;
          mediaMaskUpdateTime = 0;
          while(true) {
            currentUpdates = 0;
            loops++;
            try {
             try {
                showIdx.table.acquireReadLock();
                try {
                  strIndex.table.acquireWriteLock();
                   // First copy all of the masks in this table, reallocate cache array if not big enough
                  // Also zero out all of the existing ones so they can be created fresh
                  if (mmCache.length < strIndex.table.num)
                    mmCache = new int[strIndex.table.num];
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null)
                    {
                      mmCache[i] = strIndex.data[i].getMediaMask();
                      strIndex.data[i].setMediaMask(0);
                    }
                  }
                  // Now propagate all of the Show media masks down to the stringer table
                  for (int i = 0; i < showIdx.table.num; i++)
                  {
                    Show currShow = (Show) showIdx.data[i];
                    if (currShow != null && currShow.getMediaMask() != 0)
                    {
                      if (currShow.bonuses != null && currShow.bonuses.length > 0)
                      {
                        for (int j = 0; j < currShow.bonuses.length; j++)
                        {
                          if (!currShow.bonuses[j].hasMediaMask(currShow.getMediaMask()))
                          {
                            currShow.bonuses[j].addMediaMask(currShow.getMediaMask());
                          }
                        }
                      }
                      if (currShow.language != null)
                      {
                        if (!currShow.language.hasMediaMask(currShow.getMediaMask()))
                        {
                          currShow.language.addMediaMask(currShow.getMediaMask());
                        }
                      }
                    }
                  }
                  startMediaMaskTime = Sage.eventTime();
                  // Now go through and distribute the actual updates for any changes
                  // that occurred
                  for (int i = 0; i < strIndex.table.num; i++)
                  {
                    if (strIndex.data[i] != null && strIndex.data[i].getMediaMask() != mmCache[i])
                    {
                      logUpdate(strIndex.data[i], BONUS_CODE);
                      currentUpdates++;
                      if((Sage.eventTime() - startMediaMaskTime) > WIZARD_MEDIAMASK_MAINTENANCE_LOCK_TIME) {
                        // Before breaking out, reset the media masks moving forward from this element.
                        // By doing this, we're inherently undoing the propagation that was done to each
                        // show/element above that didn't get logged.
                        for (i++; i < strIndex.table.num; i++)
                          if (strIndex.data[i] != null)
                            strIndex.data[i].setMediaMask(mmCache[i]);
                        throw new InterruptedException();
                      }
                    }
                  }
               } finally {
                  strIndex.table.releaseWriteLock();
                }
              } finally {
                showIdx.table.releaseReadLock();
              }
             } catch (InterruptedException e) {
              totalUpdates += currentUpdates;
              mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
              System.out.println("MaintenceMetric(MediaMaskUpdateBonus): " + currentUpdates + " / " + totalUpdates + " items in " + loops + " loops; ");
              try { Thread.sleep(WIZARD_MEDIAMASK_MAINTENANCE_SLEEP_TIME); } catch (InterruptedException ignore) {}
              continue;
            }
            totalUpdates += currentUpdates;
            mediaMaskUpdateTime += Sage.eventTime() - startMediaMaskTime;
            break; // done
          }
          System.out.println("MaintenceMetric(MediaMaskUpdateBonus): " + mediaMaskUpdateTime + "ms for " + totalUpdates + " items in " + loops + " loops");

        }
        Sage.gcPause();
        mpause();
        mpause();

        /*
         * 7/20/03
         * I changed this so the outLock is held for the whole backup/write procedue
         * I also enabled logging all of the xcts during maintenance. These were
         * optimizations that no longer are valid in the async library import & client/server
         * scenarios.
         */
        if (saveAfter)
        {
          saveDBFile();
          if (Sage.DBG) System.out.println("Wizard DONE saving database info.");
        }
      } catch (Throwable e) {
        System.out.println("Exception during Maintenance:" + e);
        e.printStackTrace(System.out);
      } finally {
        performingMaintenance = false;
      }
    }

    god.submitJob(new Object[] { Carny.STD_JOB, null });

    // Only update maintenance time for a Full maintenance
    if ( maintenanceType == MaintenanceType.FULL ){
      lastMaintenance = Sage.time();
      Sage.putLong(prefsRoot + LAST_MAINTENANCE, lastMaintenance);
    }
    version = VERSION;
  }

  private void fixNoDataDuration(Airing fixMe, boolean logTX)
  {
    if (fixMe.duration < noDataMaxLen || fixMe.getEndTime() < Sage.time()) return;

    Table airTable = getTable(AIRING_CODE);
    try {
      airTable.acquireWriteLock();
      long noDataRuleCoverage = Sage.time() + noDataMaxRuleDur;
      long currStart = fixMe.getStartTime();
      long theEnd = fixMe.getEndTime();
      airTable.remove(fixMe, logTX);
      long endBlock;
      if (currStart + noDataMaxLen < Sage.time())
      {
        endBlock = Sage.time();
      }
      else
      {
        endBlock = currStart + noDataMaxLen;
      }
      endBlock -= endBlock % noDataMaxLen;
      Airing airClone = new Airing(getNextWizID());
      airClone.time = currStart;
      airClone.duration = endBlock - currStart;
      airClone.stationID = fixMe.stationID;
      airClone.showID = noShow.id;
      airClone.setMediaMask(DBObject.MEDIA_MASK_TV);
      airTable.add(airClone, logTX);
      currStart = endBlock;
      while (currStart < theEnd && currStart < noDataRuleCoverage)
      {
        Airing noShowAdd = new Airing(getNextWizID());
        noShowAdd.showID = noShow.id;
        noShowAdd.stationID = fixMe.stationID;
        noShowAdd.time = currStart;
        noShowAdd.duration = Math.min(theEnd - currStart, noDataMaxLen);
        noShowAdd.setMediaMask(DBObject.MEDIA_MASK_TV);
        airTable.add(noShowAdd, logTX);
        currStart += noDataMaxLen;
      }
      if (currStart < theEnd)
      {
        // Fill out the end of it
        Airing noShowAdd = new Airing(getNextWizID());
        noShowAdd.showID = noShow.id;
        noShowAdd.stationID = fixMe.stationID;
        noShowAdd.time = currStart;
        noShowAdd.duration = theEnd - currStart;
        noShowAdd.setMediaMask(DBObject.MEDIA_MASK_TV);
        airTable.add(noShowAdd, logTX);
      }
    } finally {
      airTable.releaseWriteLock();
    }
  }

  void startSeq(boolean recover)
  {
    if (!Sage.client && SageTV.upgrade && !"".equals(SageTV.upgradedFromVersion))
    {
      if (Sage.DBG) System.out.println("Backing up DB file for upgrade...");
      try
      {
        IOUtils.copyFile(dbFile, new File(dbFile.getAbsolutePath() + "." + SageTV.upgradedFromVersion));
      }
      catch (Exception e)
      {
        System.out.println("Error backing up Wiz DB file for upgrade of:" + e);
      }
    }
    try
    {
      long loadStart = Sage.time();
      if (Sage.DBG) System.out.println("Wizard starting to load database info...");
      Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("Object_Database_Source") }));
      if (dbFile != null && (!dbFile.isFile() || dbFile.length() == 0) && dbBackupFile != null && dbBackupFile.isFile())
      {
        try
        {
          if (Sage.DBG) System.out.println("Replacing DB file with backup file since it is non-existent or empty...");
          IOUtils.copyFile(dbBackupFile, dbFile);
        }
        catch (Exception e)
        {
          System.out.println("Error restoring backup of Wiz DB file of:" + e);
        }
      }
      boolean saveNow = !loadDBFile();
      // If there's no backup file then create one
      if (!dbBackupFile.isFile() || dbBackupFile.length() == 0)
        saveNow = true;
      long loadEnd = Sage.time();
      if (!Sage.client)
      {
        if (Sage.getBoolean(prefsRoot + CLEAR_PROFILE, false))
        {
          getTable(AGENT_CODE).clear();
          getTable(WATCH_CODE).clear();
          getTable(WASTED_CODE).clear();
          Table sTable = getTable(SHOW_CODE);
          try {
            sTable.acquireWriteLock();
            for (int i = 0; i < sTable.num; i++) {
              ((Show) sTable.primary.data[i]).lastWatched = 0;
            }
          } finally {
            sTable.releaseWriteLock();
          }
          saveNow = true;
        }
        Sage.putBoolean(prefsRoot + CLEAR_PROFILE, false);
        if (Sage.getBoolean(prefsRoot + CLEAR_WATCHED, false))
        {
          getTable(WATCH_CODE).clear();
          Table sTable = getTable(SHOW_CODE);
          try {
            sTable.acquireWriteLock();
            for (int i = 0; i < sTable.num; i++) {
              ((Show) sTable.primary.data[i]).lastWatched = 0;
            }
          } finally {
            sTable.releaseWriteLock();
          }
          saveNow = true;
        }
        Sage.putBoolean(prefsRoot + CLEAR_WATCHED, false);
      }
      if (Sage.DBG) System.out.println("Wizard DONE loading database info. loadTime=" + ((loadEnd -
          loadStart)/1000.0) + " sec");
      if ((saveNow && !Sage.client) || Sage.getBoolean("db_perf_analysis", false))
      {
        Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("Object_Database_Backup") }));
        saveDBFile();
        if (Sage.DBG) System.out.println("Wizard DONE saving database info.");
      }
      else if (!Sage.client && dbFile != null)
      {
        String mode = "rwd";
        // 128k was chosen from observing most random writes are typically less than this distance
        // behind the actual write buffer. The original default of 64k would often miss even with
        // the optimizer trying to compensate for the last miss since the misses were more than 64k
        // behind the latest write.
        dbout = new SageDataFile(new BufferedSageFile(
            new LocalSageFile(dbFile, mode),
            BufferedSageFile.READ_BUFFER_SIZE, 131072),
            Sage.I18N_CHARSET);
        dbout.seek(dbout.length());
      }
      if (Sage.getBoolean("db_perf_analysis", false))
        System.exit(0);
      Sage.setSplashText(Sage.rez("Module_Init_Progress", new Object[] { Sage.rez("Object_Database"), new Double(1.0) }));
    }
    catch (Throwable e)
    {
      System.out.println("thrown " + e);
      e.printStackTrace(System.out);
      if (recover)
      {
        // There's an error in the file cause we quit too early last time, restore
        // the backup file if there and try again.
        System.out.println("Error with DB file:" + e + ", attempting to restore backup.");
        restoreBackup();
        {
          // All of the data we ran through before that didn't cause a crash
          // will be in here still, so we need to clear it out.
          for (int i = 0; i < tables.length; i++)
          {
            if (tables[i] != null)
              tables[i].clear();
          }
          startSeq(false);
          return;
        }
      }

      // We're screwed loading this time.
      if (Sage.DBG) System.out.println("Error accessing file system:" + e);
      if (Sage.DBG) e.printStackTrace();
    }
    updateLastModified(DBObject.MEDIA_MASK_ALL);

    Sage.gcPause();
    if (Sage.getBoolean("dump_db_on_startup", false))
    {
      try {
        dumpDB();
      } catch (Throwable th) {
        System.out.println("ERROR dumping DB of:" + th);
      }
    }
  }

  private void dumpDB() throws Throwable
  {
    File dump = new File(dbFile.getParentFile(), "Wiz.dump");
    if (Sage.DBG) System.out.println("Dumping the database to file " + dump);
    PrintWriter dumpStream = null;
    try
    {
      dumpStream = new PrintWriter(new BufferedWriter(new
          FileWriter(dump)));
      Table t = getTable(NETWORK_CODE);
      for (int i = 0; i < t.num; i++)
      {
        dumpStream.println("Network: " + t.primary.data[i].getMediaMaskString() + ' ' + t.primary.data[i].toString());
      }
      t = getTable(CHANNEL_CODE);
      for (int i = 0; i < t.num; i++)
      {
        dumpStream.println(t.primary.data[i].toString());
      }

      Index indy = getIndex(TITLE_CODE, TITLES_BY_NAME_CODE);
      for (int i = 0; i < indy.table.num; i++)
        dumpStream.println("Title: " + indy.data[i].getMediaMaskString() + ' ' + indy.data[i].toString());

      indy = getIndex(PRIME_TITLE_CODE, PRIME_TITLES_BY_NAME_CODE);
      for (int i = 0; i < indy.table.num; i++)
        dumpStream.println("PrimeTitle: " + indy.data[i].getMediaMaskString() + ' ' + indy.data[i].toString());

      indy = getIndex(PEOPLE_CODE, PEOPLE_BY_NAME_CODE);
      for (int i = 0; i < indy.table.num; i++)
        dumpStream.println("Person: " + indy.data[i].getMediaMaskString() + ' ' + ((Person)indy.data[i]).getFullString());

      t = getTable(CATEGORY_CODE);
      for (int i = 0; i < t.num; i++)
      {
        dumpStream.println("Category: " + t.primary.data[i].getMediaMaskString() + ' ' + t.primary.data[i].toString());
      }
      t = getTable(SUBCATEGORY_CODE);
      for (int i = 0; i < t.num; i++)
      {
        dumpStream.println("SubCategory: " + t.primary.data[i].getMediaMaskString() + ' ' + t.primary.data[i].toString());
      }
      t = getTable(RATED_CODE);
      for (int i = 0; i < t.num; i++)
      {
        dumpStream.println("Rated: " + t.primary.data[i].getMediaMaskString() + ' ' + t.primary.data[i].toString());
      }
      t = getTable(PR_CODE);
      for (int i = 0; i < t.num; i++)
      {
        dumpStream.println("PR: " + t.primary.data[i].getMediaMaskString() + ' ' + t.primary.data[i].toString());
      }
      t = getTable(ER_CODE);
      for (int i = 0; i < t.num; i++)
      {
        dumpStream.println("ER: " + t.primary.data[i].getMediaMaskString() + ' ' + t.primary.data[i].toString());
      }
      t = getTable(YEAR_CODE);
      for (int i = 0; i < t.num; i++)
      {
        dumpStream.println("Year: " + t.primary.data[i].getMediaMaskString() + ' ' + t.primary.data[i].toString());
      }
      t = getTable(BONUS_CODE);
      for (int i = 0; i < t.num; i++)
      {
        dumpStream.println("Bonus: " + t.primary.data[i].getMediaMaskString() + ' ' + t.primary.data[i].toString());
      }

      indy = getIndex(SHOW_CODE);
      for (int i = 0; i < indy.table.num; i++)
        dumpStream.println(indy.data[i].getMediaMaskString() + ' ' + indy.data[i].toString());

      indy = getIndex(AIRING_CODE, AIRINGS_BY_CT_CODE);
      for (int i = 0; i < indy.table.num; i++)
        dumpStream.println(indy.data[i].getMediaMaskString() + ' ' + indy.data[i].toString());

      t = getTable(AGENT_CODE);
      for (int i = 0; i < t.num; i++)
        dumpStream.println(t.primary.data[i].toString());

      t = getTable(MEDIAFILE_CODE);
      for (int i = 0; i < t.num; i++)
        dumpStream.println(t.primary.data[i].getMediaMaskString() + ' ' + t.primary.data[i].toString());

      t = getTable(MANUAL_CODE);
      for (int i = 0; i < t.num; i++)
        dumpStream.println(t.primary.data[i].toString());

      t = getTable(WASTED_CODE);
      for (int i = 0; i < t.num; i++)
        dumpStream.println(t.primary.data[i].getMediaMaskString() + ' ' + t.primary.data[i].toString());

      t = getTable(WATCH_CODE);
      for (int i = 0; i < t.num; i++)
        dumpStream.println(t.primary.data[i].getMediaMaskString() + ' ' + t.primary.data[i].toString());

      t = getTable(WIDGET_CODE);
      for (int i = 0; i < t.num; i++)
        dumpStream.println(t.primary.data[i].toString());

      t = getTable(SERIESINFO_CODE);
      for (int i = 0; i < t.num; i++)
        dumpStream.println(t.primary.data[i].toString());

      t = getTable(TVEDITORIAL_CODE);
      for (int i = 0; i < t.num; i++)
        dumpStream.println(t.primary.data[i].toString());
    }
    finally
    {
      if (dumpStream != null)
        dumpStream.close();
    }
    if (Sage.DBG) System.out.println("DONE Dumping the database to file " + dump);
  }

  int getCurrWizID() { return nextID - 1; }
  public synchronized int getNextWizID()
  {
    nextID++;
    Sage.putInt(prefsRoot + NEXT_UID, nextID);
    return nextID - 1;
  }

  /*
   * This ENSURES we don't have problems with duplicate IDs ever.
   */
  protected void notifyOfID(int idExists)
  {
    // We are single threaded on load so we don't need the sync lock then; minor performance gain from this
    if (loading)
    {
      if (idExists >= nextID)
      {
        nextID = idExists + 1;
        Sage.putInt(prefsRoot + NEXT_UID, nextID);
      }
    }
    else
    {
      synchronized (this)
      {
        if (idExists >= nextID)
        {
          nextID = idExists + 1;
          Sage.putInt(prefsRoot + NEXT_UID, nextID);
        }
      }
    }
  }

  /*
   * Publicly Exposed Methods
   */
  // This is only for adds/removes and channel updates
  public long getLastModified()
  {
    return getLastModified(DBObject.MEDIA_MASK_ALL);
  }
  public long getLastModified(int mediaMask)
  {
    long rv = 0;
    if ((mediaMask & DBObject.MEDIA_MASK_DVD) == DBObject.MEDIA_MASK_DVD)
      rv = Math.max(rv, lastModifiedDVD);
    if ((mediaMask & DBObject.MEDIA_MASK_BLURAY) == DBObject.MEDIA_MASK_BLURAY)
      rv = Math.max(rv, lastModifiedBluRay);
    if ((mediaMask & DBObject.MEDIA_MASK_MUSIC) == DBObject.MEDIA_MASK_MUSIC)
      rv = Math.max(rv, lastModifiedMusic);
    if ((mediaMask & DBObject.MEDIA_MASK_PICTURE) == DBObject.MEDIA_MASK_PICTURE)
      rv = Math.max(rv, lastModifiedPicture);
    if ((mediaMask & DBObject.MEDIA_MASK_TV) == DBObject.MEDIA_MASK_TV)
      rv = Math.max(rv, lastModifiedTV);
    if ((mediaMask & DBObject.MEDIA_MASK_VIDEO) == DBObject.MEDIA_MASK_VIDEO)
      rv = Math.max(rv, lastModifiedVideo);
    return rv;
  }

  public Channel[] getChannels()
  {
    if (loading) return new Channel[0];

    Table t = getTable(CHANNEL_CODE);
    try {
      t.acquireReadLock();
      Channel[] rv = new Channel[t.num];
      System.arraycopy(t.primary.data, 0, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  public boolean addChannelPublic(String name, String longName, String network, int stationID)
  {
    boolean[] didAdd = new boolean[1];
    Channel c = addChannel(name, longName, network, stationID, 0, didAdd);
    if (didAdd[0])
      resetAirings(stationID);
    return (c != null);
  }
  public Channel addChannel(String name, String longName, String network, int stationID, int logoMask, byte[] logoURL, boolean [] didAdd)
  {
    return addChannel(name, longName, network, stationID, logoMask, Pooler.EMPTY_INT_ARRAY, logoURL, didAdd);
  }
  // 601 Channel addChannel(...
  public Channel addChannel(String name, String longName, String network, int stationID, int logoMask, boolean [] didAdd)
  {
    return addChannel(name, longName, network, stationID, logoMask, Pooler.EMPTY_INT_ARRAY, Pooler.EMPTY_BYTE_ARRAY, didAdd);
  }
  public Channel addChannel(String name, String longName, String network, int stationID, int logoMask, int[] imageList, byte[] logoURL,
      boolean [] didAdd)
  {
    if (stationID == 0 && (name == null || name.length() > 0 || longName == null || longName.length() > 0))
    {
      // Do allow our own internal adding of the zero station ID channel, we use a zero length string for the names in that case
      if (Sage.DBG) System.out.println("ERROR Invalid request to add a channel with a zero stationID: name=" + name + " desc=" + longName + " network=" + network);
      return null;
    }
    // Check if the channel already exists
    try {
      acquireWriteLock(CHANNEL_CODE);
      Channel c = getChannelForStationID(stationID);
      if (didAdd != null && didAdd.length > 0)
        didAdd[0] = (c == null);
      if (c == null)
      {
        c = new Channel(getNextWizID());
        c.name = new String(name == null ? "USER" : name);
        c.longName = new String(longName);
        c.stationID = stationID;
        c.network = getNetworkForName(network);
        c.logoMask = logoMask;
        c.logoImages = (imageList == null) ? Pooler.EMPTY_INT_ARRAY : imageList;
        c.logoURL = (logoURL == null || logoURL.length == 0) ? Pooler.EMPTY_BYTE_ARRAY : logoURL;
        getTable(CHANNEL_CODE).add(c, true);
        updateLastModified(DBObject.MEDIA_MASK_TV);
        if (Sage.DBG) System.out.println("Added:" + c);
      }
      else
      {
        //if ((c.network == null) && (network != null) && (network.length() > 0))
        Channel newC = null;
        String oldNetName = null;
        if (c.network != null)
          oldNetName = c.network.toString();
        if (oldNetName != network && (oldNetName == null || !oldNetName.equals(network)))
        {
          //System.out.println("Updating:" + c + " oldNetwork=" + c.network + " newNetwork=" + network);
          if (newC == null)
            newC = (Channel) c.clone();
          newC.network = getNetworkForName(network);
        }
        if (name != null && !name.equals(c.name))
        {
          //System.out.println("Updating:" + c + " oldName=" + c.name + " newName=" + name);
          if (newC == null)
            newC = (Channel) c.clone();
          newC.name = name;
        }
        if (longName != null && !longName.equals(c.longName))
        {
          //System.out.println("Updating:" + c + " oldlongName=" + c.longName + " newlongName=" + longName);
          if (newC == null)
            newC = (Channel) c.clone();
          newC.longName = longName;
        }
        if (logoMask != c.logoMask)
        {
          if (newC == null)
            newC = (Channel) c.clone();
          newC.logoMask = logoMask;
        }
        if (c.logoImages != imageList && !Arrays.equals(imageList, c.logoImages))
        {
          if (newC == null)
            newC = (Channel) c.clone();
          newC.logoImages = imageList;
        }
        if (c.logoURL != logoURL && !Arrays.equals(logoURL, c.logoURL))
        {
          if (newC == null)
            newC = (Channel) c.clone();
          newC.logoURL = logoURL;
        }
        if (newC != null)
        {
          updateLastModified(DBObject.MEDIA_MASK_TV);
          getTable(CHANNEL_CODE).update(c, newC, true);
        }
      }
      return c;
    } finally {
      releaseWriteLock(CHANNEL_CODE);
    }
  }

  public Playlist addPlaylist(String playlistName)
  {
    Table t = getTable(PLAYLIST_CODE);
    try {
      t.acquireWriteLock();
      Playlist p = new Playlist(getNextWizID());
      p.name = playlistName;
      t.add(p, true);
      updateLastModified(DBObject.MEDIA_MASK_ALL);
      if (Sage.DBG) System.out.println("Added:" + p);
      return p;
    } finally {
      t.releaseWriteLock();
    }
  }

  public void removePlaylist(Playlist removeMe)
  {
    if (removeMe == null) return;
    getTable(PLAYLIST_CODE).remove(removeMe, true);
    updateLastModified(DBObject.MEDIA_MASK_ALL);
  }

  public Playlist[] getPlaylists()
  {
    if (loading) return new Playlist[0];

    Table t = getTable(PLAYLIST_CODE);
    try {
      t.acquireReadLock();
      Playlist[] rv = new Playlist[t.num];
      System.arraycopy(t.primary.data, 0, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  public Playlist[] getMusicPlaylists()
  {
    if (loading) return new Playlist[0];

    Table t = getTable(PLAYLIST_CODE);
    ArrayList<Playlist> rv = new ArrayList<Playlist>();
    try {
      t.acquireReadLock();
      for (int i = 0; i < t.num; i++)
        if (((Playlist) t.primary.data[i]).isMusicPlaylist())
          rv.add((Playlist)t.primary.data[i]);
    } finally {
      t.releaseReadLock();
    }
    return rv.toArray(new Playlist[0]);
  }

  public Playlist[] getVideoPlaylists()
  {
    if (loading) return new Playlist[0];

    Table t = getTable(PLAYLIST_CODE);
    ArrayList<Playlist> rv = new ArrayList<Playlist>();
    try {
      t.acquireReadLock();
      for (int i = 0; i < t.num; i++)
        if (!((Playlist) t.primary.data[i]).isMusicPlaylist())
          rv.add((Playlist)t.primary.data[i]);
    } finally {
      t.releaseReadLock();
    }
    return rv.toArray(new Playlist[0]);
  }

  public TVEditorial addEditorial(String extID, String title, String airdate, String network, String description, String imageURL)
  {
    Show theShow = getShowForExternalID(extID);
    if (theShow == null) return null;
    TVEditorial oldEd = getEditorial(theShow);
    TVEditorial ed;
    try {
      acquireWriteLock(TVEDITORIAL_CODE);
      ed = (oldEd != null) ? (TVEditorial) oldEd.clone() : new TVEditorial(getNextWizID());
      ed.title = getTitleForName(title);
      ed.description = new String(description);
      ed.network = getNetworkForName(network);
      ed.showID = theShow.id;
      ed.imageURL = new String(imageURL);
      ed.airdate = new String(airdate);
      if (oldEd != null)
      {
        getTable(TVEDITORIAL_CODE).update(oldEd, ed, true);
        return oldEd;
      }
      else
      {
        getTable(TVEDITORIAL_CODE).add(ed, true);
      }
    } finally {
      releaseWriteLock(TVEDITORIAL_CODE);
    }
    return ed;
  }

  public TVEditorial getEditorial(Show iShow)
  {
    if (iShow == null) return null;
    Index indy = getIndex(TVEDITORIAL_CODE);
    int showID = iShow.id;
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        TVEditorial midVal = (TVEditorial) indy.data[mid];
        int cmp = midVal.showID - showID;

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          return midVal;
        }
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return null;
  }

  public TVEditorial[] getEditorials()
  {
    if (loading) return new TVEditorial[0];

    Table t = getTable(TVEDITORIAL_CODE);
    try {
      t.acquireReadLock();
      TVEditorial[] rv = new TVEditorial[t.num];
      System.arraycopy(t.primary.data, 0, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  public void removeEditorial(TVEditorial removeMe)
  {
    if (removeMe == null) return;
    getTable(TVEDITORIAL_CODE).remove(removeMe, true);
  }

  public boolean addSeriesInfoPublic(int legacySeriesID, String title, String network, String description, String history, String premiereDate,
      String finaleDate, String airDOW, String airHrMin, String imageURL, String[] people, String[] characters)
  {
    return addSeriesInfo(legacySeriesID, title, network, description, history, premiereDate, finaleDate, airDOW, airHrMin, imageURL, people, characters) != null;
  }
  public SeriesInfo addSeriesInfo(int legacySeriesID, int showcardID, String title, String network, String description, String history, String premiereDate,
      String finaleDate, String airDOW, String airHrMin, Person[] people, String[] characters, byte[][] imageURLs)
  {
    return addSeriesInfo(legacySeriesID, showcardID, title, network, description, history, premiereDate, finaleDate, airDOW, airHrMin,
      "", people, characters, Pooler.EMPTY_INT_ARRAY, Pooler.EMPTY_LONG_ARRAY, imageURLs);
  }
  public SeriesInfo addSeriesInfo(int legacySeriesID, int showcardID, String title, String network, String description, String history, String premiereDate,
      String finaleDate, String airDOW, String airHrMin, String[] people, String[] characters, byte[][] imageURLs)
  {
    Person[] peeps = (people == null || people.length == 0) ? Pooler.EMPTY_PERSON_ARRAY : new Person[people.length];
    for (int i = 0; i < peeps.length; i++)
      peeps[i] = getPersonForName(people[i]);
    return addSeriesInfo(legacySeriesID, showcardID, title, network, description, history, premiereDate, finaleDate, airDOW, airHrMin,
        "", peeps, characters, Pooler.EMPTY_INT_ARRAY, Pooler.EMPTY_LONG_ARRAY, imageURLs);
  }
  public SeriesInfo addSeriesInfo(int legacySeriesID, String title, String network, String description, String history, String premiereDate,
      String finaleDate, String airDOW, String airHrMin, String imageURL, String[] people, String[] characters)
  {
    Person[] peeps = (people == null || people.length == 0) ? Pooler.EMPTY_PERSON_ARRAY : new Person[people.length];
    for (int i = 0; i < peeps.length; i++)
      peeps[i] = getPersonForName(people[i]);
    return addSeriesInfo(legacySeriesID, 0, title, network, description, history, premiereDate, finaleDate, airDOW, airHrMin,
        imageURL, peeps, characters, Pooler.EMPTY_INT_ARRAY, Pooler.EMPTY_LONG_ARRAY, Pooler.EMPTY_2D_BYTE_ARRAY);
  }
  public SeriesInfo addSeriesInfo(int legacySeriesID, int showcardID, String title, String network, String description, String history, String premiereDate,
      String finaleDate, String airDOW, String airHrMin, String imageUrl, Person[] people, String[] characters, int[] seriesImages, long[] castImages)
  {
    return addSeriesInfo(legacySeriesID, showcardID, title, network, description, history, premiereDate, finaleDate, airDOW, airHrMin, imageUrl, people,
        characters, seriesImages, castImages, Pooler.EMPTY_2D_BYTE_ARRAY);
  }
  public SeriesInfo addSeriesInfo(int legacySeriesID, int showcardID, String title, String network, String description, String history, String premiereDate,
      String finaleDate, String airDOW, String airHrMin, String imageUrl, Person[] people, String[] characters, int[] seriesImages, long[] castImages, byte[][] imageURLs)
  {
    SeriesInfo oldSeries = (showcardID == 0) ? getSeriesInfoForLegacySeriesID(legacySeriesID) : getSeriesInfoForShowcardID(showcardID);
    SeriesInfo series;
    // If this is the first time we've seen this showcardID + legacySeriesID combination, we should go through and fix all the
    // existing Show objects and update them with the proper showcardID
    boolean fixupShows = false;
    try {
      acquireWriteLock(SERIESINFO_CODE);
      series = (oldSeries != null) ? (SeriesInfo) oldSeries.clone() : new SeriesInfo(getNextWizID());
      series.legacySeriesID = legacySeriesID;
      series.showcardID = showcardID;
      series.title = getTitleForName(title);
      series.description = new String(description);
      series.network = getNetworkForName(network);
      series.history = (history == null) ? "" : new String(history);
      series.premiereDate = (premiereDate == null) ? "" : new String(premiereDate);
      series.finaleDate = (finaleDate == null) ? "" : new String(finaleDate);
      if ("N/A".equals(airDOW))
        airDOW = null;
      series.airDow = (airDOW == null) ? "" : new String(airDOW);
      series.airHrMin = (airHrMin == null) ? "" : new String(airHrMin);
      series.imageUrl = (imageUrl == null) ? "" : new String(imageUrl);
      if (people == null || people.length == 0)
      {
        series.people = Pooler.EMPTY_PERSON_ARRAY;
        series.characters = Pooler.EMPTY_STRING_ARRAY;
      }
      else
      {
        series.people = new Person[people.length];
        series.characters = new String[characters.length];
        if (people.length != characters.length)
          throw new InternalError("ERROR length of people & character arrays do not match!");
        for (int i = 0; i < people.length; i++)
        {
          series.people[i] = people[i];
          series.characters[i] = new String(characters[i]);
        }
      }
      if (seriesImages == null || seriesImages.length == 0)
        series.seriesImages = Pooler.EMPTY_INT_ARRAY;
      else
        series.seriesImages = seriesImages.clone();
      if (imageURLs == null || imageURLs.length == 0)
        series.imageURLs = Pooler.EMPTY_2D_BYTE_ARRAY;
      else
        series.imageURLs = imageURLs.clone();
      if (castImages == null || castImages.length == 0)
        series.castImages = Pooler.EMPTY_LONG_ARRAY;
      else
        series.castImages = castImages.clone();
      if (oldSeries != null)
      {
        if (!series.isSeriesInfoIdentical(oldSeries))
        {
          if (series.showcardID != 0 && (oldSeries.legacySeriesID != series.legacySeriesID || oldSeries.showcardID != series.showcardID))
            fixupShows = true;
          getTable(SERIESINFO_CODE).update(oldSeries, series, true);
        }
        series = oldSeries;
      }
      else
      {
        fixupShows = true;
        getTable(SERIESINFO_CODE).add(series, true);
      }
    } finally {
      releaseWriteLock(SERIESINFO_CODE);
    }
    // only fix legacy shows on non-embedded
    if (fixupShows)
    {
      if (Sage.DBG) System.out.println("Fixing existing Show objects for new SeriesInfo data legacyID=" + series.legacySeriesID + " showcardID=" + series.showcardID +
          " " + series);
      String seriesIDStr = Integer.toString(series.legacySeriesID);
      while (seriesIDStr.length() < 6)
        seriesIDStr = "0" + seriesIDStr;
      if (seriesIDStr.length() == 7)
        seriesIDStr = "0" + seriesIDStr;
      for (int x = 0; x < 4; x++)
      {
        String currPrefix = ((x % 2) == 0 ? "EP" : "SH") + seriesIDStr;
        Show[] theShows = getShowsForExternalIDPrefix(currPrefix);
        if (theShows.length > 0)
        {
          int numFixed = fixShowcardIDs(series, theShows);
          if (Sage.DBG) System.out.println("Fixed " + numFixed + " " + currPrefix + " prefixed entries of " + theShows.length);
        }
        if (x == 1)
        {
          if (seriesIDStr.length() == 6)
            seriesIDStr = "00" + seriesIDStr;
          else
            break;
        }
      }
    }
    return series;
  }

  private int fixShowcardIDs(SeriesInfo series, Show[] shows)
  {
    int numFixed = 0;
    for (int i = 0; i < shows.length; i++)
    {
      if (shows[i].showcardID == 0)
      {
        try {
          acquireWriteLock(SHOW_CODE);
          shows[i].showcardID = series.showcardID;
          logUpdate(shows[i], SHOW_CODE);
          numFixed++;
        } finally {
          releaseWriteLock(SHOW_CODE);
        }
      }
    }
    return numFixed;
  }

  public SeriesInfo[] getAllSeriesInfo()
  {
    if (loading) return new SeriesInfo[0];

    Table t = getTable(SERIESINFO_CODE);
    try {
      t.acquireReadLock();
      SeriesInfo[] rv = new SeriesInfo[t.num];
      System.arraycopy(t.primary.data, 0, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  public Person[] getAllPeople()
  {
    if (loading) return new Person[0];

    Table t = getTable(PEOPLE_CODE);
    try {
      t.acquireReadLock();
      Person[] rv = new Person[t.num];
      System.arraycopy(t.primary.data, 0, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  public Show[] getAllShows()
  {
    if (loading) return new Show[0];

    Table t = getTable(SHOW_CODE);
    try {
      t.acquireReadLock();
      Show[] rv = new Show[t.num];
      System.arraycopy(t.primary.data, 0, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  public SeriesInfo getSeriesInfoForLegacySeriesID(int seriesID)
  {
    Index indy = getIndex(SERIESINFO_CODE, SERIESINFO_BY_LEGACYSERIESID_CODE);
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        SeriesInfo midVal = (SeriesInfo) indy.data[mid];
        int cmp = midVal.legacySeriesID - seriesID;

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          return midVal;
        }
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return null;
  }

  public SeriesInfo getSeriesInfoForShowcardID(int showcardID)
  {
    Index indy = getIndex(SERIESINFO_CODE, SERIESINFO_BY_SHOWCARDID_CODE);
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        SeriesInfo midVal = (SeriesInfo) indy.data[mid];
        int cmp = midVal.showcardID - showcardID;

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          return midVal;
        }
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return null;
  }

  public SeriesInfo getSeriesInfoForID(int id)
  {
    if (id == 0) return null;
    Index indy = getIndex(SERIESINFO_CODE);
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        SeriesInfo midVal = (SeriesInfo) indy.data[mid];
        int cmp = midVal.id - id;

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          return midVal;
        }
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return null;
  }

  public UserRecord addUserRecord(String store, String key)
  {
    if (store == null || key == null || store.length() == 0 || key.length() == 0) return null;
    try {
      acquireWriteLock(USERRECORD_CODE);
      UserRecord oldRecord = getUserRecord(store, key);
      if (oldRecord != null)
        return oldRecord;
      UserRecord newRecord = new UserRecord(getNextWizID());
      newRecord.store = store.intern();
      newRecord.key = key;
      getTable(USERRECORD_CODE).add(newRecord, true);
      return newRecord;
    } finally {
      releaseWriteLock(USERRECORD_CODE);
    }
  }

  public UserRecord[] getAllUserRecords(String store)
  {
    if (loading) return new UserRecord[0];

    if (store == null) return new UserRecord[0];
    Index indy = getIndex(USERRECORD_CODE, USERRECORD_BY_STOREKEY_CODE);
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        UserRecord midVal = (UserRecord) indy.data[mid];
        int cmp;
        if (midVal == null)
          cmp = 1;
        else
          cmp = midVal.store.compareTo(store);

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          // We found one with a matching store, now we need to walk backwards to find the first record w/ a matching store and also walk forwards to
          // find the last one w/ a matching store
          int startIdx = mid;
          int endIdx = mid;
          while (startIdx > 0)
          {
            if (store.equals(((UserRecord) indy.data[startIdx - 1]).store))
              startIdx--;
            else
              break;
          }
          high = indy.table.num;
          while (endIdx < high - 1)
          {
            if (store.equals(((UserRecord) indy.data[endIdx + 1]).store))
              endIdx++;
            else
              break;
          }
          UserRecord[] rv = new UserRecord[endIdx - startIdx + 1];
          System.arraycopy(indy.data, startIdx, rv, 0, rv.length);
          return rv;
        }
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return new UserRecord[0];
  }

  public String[] getAllUserStores()
  {
    if (loading) return Pooler.EMPTY_STRING_ARRAY;

    ArrayList<String> storeList = new ArrayList<String>();
    Table t = getTable(USERRECORD_CODE);
    try {
      t.acquireReadLock();
      // There's probably a faster way than walking the whole list, but this should not be called very often I'd think
      String lastStore = null;
      for (int i = 0; i < t.num; i++)
      {
        UserRecord currRec = (UserRecord)t.primary.data[i];
        if (currRec.store != null && (lastStore == null || !lastStore.equals(currRec.store)))
        {
          storeList.add(currRec.store);
          lastStore = currRec.store;
        }
      }
    } finally {
      t.releaseReadLock();
    }
    return storeList.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public UserRecord getUserRecord(String store, String key)
  {
    if (store == null || key == null) return null;
    Index indy = getIndex(USERRECORD_CODE, USERRECORD_BY_STOREKEY_CODE);
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        UserRecord midVal = (UserRecord) indy.data[mid];
        int cmp;
        if (midVal == null)
          cmp = 1;
        else
        {
          cmp = midVal.store.compareTo(store);
          if (cmp == 0)
            cmp = midVal.key.compareTo(key);
        }

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          return midVal;
        }
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return null;
  }

  public UserRecord getUserRecordForID(int id)
  {
    if (id == 0) return null;
    Index indy = getIndex(USERRECORD_CODE);
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        UserRecord midVal = (UserRecord) indy.data[mid];
        int cmp = midVal.id - id;

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          return midVal;
        }
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return null;
  }

  public boolean removeUserRecord(UserRecord removeMe)
  {
    if (removeMe == null) return false;
    Table t = getTable(USERRECORD_CODE);
    return t.remove(removeMe, true);
  }

  public void resetAirings(int stationID)
  {
    if (!standalone)
    {
      // Put in the noShow Airing for this Channel & fix up its curr dur
      fixNoDataDuration(addAiring(noShow, stationID, 0, Long.MAX_VALUE/2, (byte)0, (byte)0, (byte)0, DBObject.MEDIA_MASK_TV), true);
    }
  }

  MediaFile addMediaFile(Airing basedOn, long recStart, String videoDirectory, String encodedBy, long providerID,
      int mediaMask, ContainerFormat fileFormat)
  {
    if (basedOn instanceof ManualRecord.FakeAiring)
    {
      basedOn = ((ManualRecord.FakeAiring) basedOn).getManualRecord().getContentAiring();
    }
    MediaFile mf;
    try {
      acquireWriteLock(MEDIAFILE_CODE);
      mf = new MediaFile(getNextWizID());
      mf.infoAiringID = basedOn.id;
      mf.myAiring = basedOn;
      mf.videoDirectory = videoDirectory;
      mf.encodedBy = encodedBy;
      mf.setMediaMask(mediaMask);
      mf.fileFormat = fileFormat;
      //mf.mediaType = mediaType;
      //mf.mediaSubtype = mediaSubtype;
      mf.generalType = MediaFile.MEDIAFILE_TV;
      mf.initialize(recStart);
      getTable(MEDIAFILE_CODE).add(mf, true);
    } finally {
      releaseWriteLock(MEDIAFILE_CODE);
    }
    if (Sage.DBG) System.out.println("Added:" + mf);
    return mf;
  }

  public MediaFile addMediaFile(File rawFile, String namePrefix, byte acquisitionTech)
  {
    MediaFile mf;
    // First check for duplication; this can occur due to manually adding a file during the import process
    mf = getFileForFilePath(rawFile);
    if (mf != null)
    {
      if (Sage.DBG) System.out.println("Attempted to add already existing file path to database: " + rawFile + " returning exisitng MF of: " + mf);
      return mf;
    }
    ContainerFormat newFormat = FormatParser.getFileFormat(rawFile);
    try {
      acquireWriteLock(MEDIAFILE_CODE);
      mf = new MediaFile(getNextWizID());
      mf.infoAiringID = mf.id;
      mf.myAiring = null;
      File parentFile = rawFile.getParentFile();
      mf.videoDirectory = parentFile == null ? "" : parentFile.toString();
      if (!mf.initialize(rawFile, namePrefix, newFormat))
      {
        if (Sage.DBG) System.out.println("MF Failed Init:" + mf);
        return null;
      }
      mf.acquisitionTech = acquisitionTech;

      getTable(MEDIAFILE_CODE).add(mf, true);
    } finally {
      releaseWriteLock(MEDIAFILE_CODE);
    }
    if (Sage.DBG) System.out.println("Added:" + mf + " num=" + getTable(MEDIAFILE_CODE).num);
    return mf;
  }

  public MediaFile addMediaFileRecovered(Airing basedOn, File theFile)
  {
    MediaFile mf;
    try {
      acquireWriteLock(MEDIAFILE_CODE);
      mf = new MediaFile(getNextWizID());
      mf.infoAiringID = basedOn.id;
      mf.myAiring = basedOn;
      mf.videoDirectory = theFile.getParentFile().getAbsolutePath();
      mf.encodedBy = "";
      //mf.mediaType = MediaFile.MEDIATYPE_VIDEO;
      //mf.mediaSubtype = MediaFile.guessSubtypeFromFilename(theFile.getName());
      mf.setMediaMask(DBObject.MEDIA_MASK_VIDEO | DBObject.MEDIA_MASK_TV);
      mf.generalType = MediaFile.MEDIAFILE_TV;
      // The file format will be detected in initializeRecovery
      mf.initializeRecovery(theFile);
      getTable(MEDIAFILE_CODE).add(mf, true);
    } finally {
      releaseWriteLock(MEDIAFILE_CODE);
    }
    if (Sage.DBG) System.out.println("Added:" + mf);
    return mf;
  }

  MediaFile addMediaFileSpecial(byte mfGeneralType, String encodingName, String mfVideoDir, int mediaMask,
      ContainerFormat fileFormat)
  {
    MediaFile mf;
    try {
      acquireWriteLock(MEDIAFILE_CODE);
      mf = new MediaFile(getNextWizID());
      mf.infoAiringID = mf.id;
      mf.generalType = mfGeneralType;
      mf.acquisitionTech = MediaFile.ACQUISITION_SYSTEM;
      //mf.mediaType = mediatype;
      //mf.mediaSubtype = mediasubtype;
      mf.setMediaMask(mediaMask);
      mf.fileFormat = fileFormat;
      if (encodingName != null)
        mf.encodedBy = mf.name = encodingName;
      if (mfVideoDir != null)
        mf.videoDirectory = mfVideoDir;

      getTable(MEDIAFILE_CODE).add(mf, true);
    } finally {
      releaseWriteLock(MEDIAFILE_CODE);
    }
    if (Sage.DBG) System.out.println("Added:" + mf);
    return mf;
  }

  private File checkForDVDPath(File f)
  {
    if (f.getName().equalsIgnoreCase("VIDEO_TS.VOB") && f.getParentFile() != null &&
        (new File(f.getParentFile(), "VIDEO_TS.IFO").isFile() || new File(f.getParentFile(), "video_ts.ifo").isFile()))
    {
      // DVD playback; use the parent folder instead
      if (Sage.DBG) System.out.println("DVD file playback detected; use the parent DVD folder instead");
      return f.getParentFile();
    }
    return f;
  }

  // This can take a file path (uses the DB to resolve first, then local filesystem, then server's filesystem if we're a client),
  // or an smb:// URL, or a file:// URL (for remote UI local playback) or a generic URL which is resolved in the player
  public MediaFile getPlayableMediaFile(String magicPath)
  {
    if (magicPath.startsWith("file://"))
    {
      MediaFile remoteMF = createLocalMediaFile();
      remoteMF.setHostname(magicPath);
      remoteMF.setFiles(new File[] { checkForDVDPath(new File(magicPath.substring(7))) });
      return remoteMF;
    }
    else if (magicPath.startsWith("smb://"))
    {
      String localPath = FSManager.getInstance().requestLocalSMBAccess(magicPath);
      if (localPath == null) return null;
      File f = checkForDVDPath(new File(localPath));
      MediaFile smbMF = getFileForFilePath(f);
      if (smbMF != null)
        return smbMF; // it was already in the DB
      smbMF = createLocalMediaFile();
      smbMF.setHostname(magicPath);
      smbMF.setFiles(new File[] { f });
      return smbMF;
    }
    else if (magicPath.indexOf("://") != -1)
    {
      int idx = magicPath.indexOf("://");
      idx = magicPath.indexOf("/", idx + 4);
      String pathname = magicPath.substring(idx + 1);
      MediaFile remoteMF = createLocalMediaFile();
      remoteMF.setHostname(magicPath);
      remoteMF.setFiles(new File[] { new File(pathname) });
      return remoteMF;
    }
    else
    {
      File f = checkForDVDPath(new File(magicPath));
      MediaFile mf = getFileForFilePath(f);
      if (mf != null)
        return mf;
      if (f.exists())
      {
        mf = createLocalMediaFile();
        mf.setFiles(new File[] { f });
        return mf;
      }
      else if (Sage.client)
      {
        // This may be a file on the server we're trying to access so try to get that access
        if (NetworkClient.getSN().requestMediaServerAccess(f, true))
        {
          mf = createLocalMediaFile();
          mf.setHostname(Sage.preferredServer);
          mf.setFiles(new File[] { f });
          return mf;
        }
      }
    }
    return null;
  }

  private MediaFile createLocalMediaFile()
  {
    MediaFile mf = new MediaFile(0);
    mf.generalType = MediaFile.MEDIAFILE_LOCAL_PLAYBACK;
    mf.acquisitionTech = MediaFile.ACQUISITION_SYSTEM;
    return mf;
  }

  public boolean isMediaFileOK(MediaFile testMe)
  {
    return getIndex(MEDIAFILE_CODE).getSingle(testMe) != null;
  }

  void removeMediaFile(MediaFile removeMe)
  {
    if (removeMe == null) return;
    Table t = getTable(MEDIAFILE_CODE);
    t.remove(removeMe, true);
    MetaImage.clearFromCache(removeMe);
    updateLastModified(removeMe.getMediaMask());
  }

  public MediaFile[] getFiles(MediaFile[] storeHereIfBigEnough)
  {
    if (storeHereIfBigEnough == null)
      return getFiles();
    if (loading)
    {
      Arrays.fill(storeHereIfBigEnough, null);
      return storeHereIfBigEnough;
    }

    Table t = getTable(MEDIAFILE_CODE);
    int usedLength;
    try {
      t.acquireReadLock();
      usedLength = t.num;
      if (storeHereIfBigEnough.length < usedLength)
        storeHereIfBigEnough = new MediaFile[usedLength + 20];
      System.arraycopy(t.primary.data, 0, storeHereIfBigEnough, 0, usedLength);
    } finally {
      t.releaseReadLock();
    }
    Arrays.fill(storeHereIfBigEnough, usedLength, storeHereIfBigEnough.length, null);
    return storeHereIfBigEnough;
  }

  public MediaFile[] getFiles()
  {
    if (loading) return new MediaFile[0];

    Table t = getTable(MEDIAFILE_CODE);
    try {
      t.acquireReadLock();
      MediaFile[] rv = new MediaFile[t.num];
      System.arraycopy(t.primary.data, 0, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  public MediaFile[] getFiles(int mediaMask, boolean libraryOnly)
  {
    if (loading) return new MediaFile[0];

    Table t = getTable(MEDIAFILE_CODE);
    Index indy = t.primary;
    int x = 0;
    MediaFile[] rv;
    try {
      t.acquireReadLock();
      rv = new MediaFile[t.num];
      if (libraryOnly)
      {
        for (int i = 0; i < t.num; i++)
        {
          MediaFile mf = (MediaFile)t.primary.data[i];
          if (mf.archive && (mediaMask & mf.getMediaMask()) != 0)
            rv[x++] = mf;
        }
      }
      else
      {
        for (int i = 0; i < t.num; i++)
        {
          MediaFile mf = (MediaFile)t.primary.data[i];
          if ((mediaMask & mf.getMediaMask()) != 0)
            rv[x++] = mf;
        }
      }
    } finally {
      t.releaseReadLock();
    }
    if (x == rv.length)
      return rv;
    else
    {
      MediaFile[] newRV = new MediaFile[x];
      System.arraycopy(rv, 0, newRV, 0, x);
      return newRV;
    }
  }

  public MediaFile getFileForAiring(Airing air)
  {
    if (air instanceof MediaFile.FakeAiring)
    {
      return ((MediaFile.FakeAiring) air).getMediaFile();
    }
    if (air instanceof ManualRecord.FakeAiring)
    {
      air = ((ManualRecord.FakeAiring) air).getManualRecord().getContentAiring();
    }
    if (air != null) return getFileForAiring(air.id);
    else return null;
  }
  MediaFile getFileForAiring(int airingID)
  {
    if (airingID == 0) return null;
    Index indy = getIndex(MEDIAFILE_CODE, MEDIAFILE_BY_AIRING_ID_CODE);
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        MediaFile midVal = (MediaFile) indy.data[mid];
        int cmp = midVal.infoAiringID - airingID;

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          return midVal;
        }
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return null;
  }

  public MediaFile getFileForFilePath(File path)
  {
    if (path == null) return null;
    Index indy = getIndex(MEDIAFILE_CODE, MEDIAFILE_BY_FILE_PATH);
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        MediaFile midVal = (MediaFile) indy.data[mid];
        int cmp;
        if (midVal == null)
          cmp = 1;
        else
        {
          synchronized (midVal.files)
          {
            if (midVal.files.size() == 0)
              cmp = 1;
            else
            {
              cmp = ((File) midVal.files.firstElement()).compareTo(path);
            }
          }
        }

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          return midVal;
        }
      }
      // Check if it's one of the secondary files for this MF object
      if (high >= 0 && high < indy.data.length)
      {
        MediaFile testVal = (MediaFile) indy.data[high];
        if (testVal != null && testVal.hasFile(path))
          return testVal;
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return null;
  }

  public MediaFile getFileForID(int fileID)
  {
    if (fileID == 0) return null;
    Index indy = getIndex(MEDIAFILE_CODE);
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        MediaFile midVal = (MediaFile) indy.data[mid];
        int cmp = midVal.id - fileID;

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          return midVal;
        }
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return null;
  }

  Wasted addWasted(Airing basedOn, boolean manual)
  {
    Wasted w;
    try {
      acquireWriteLock(WASTED_CODE);
      w = getWastedForAiring(basedOn);
      if (w != null)
      {
        if (!w.manual && manual)
        {
          // Only update the manual flag if its turning it on, otherwise deletions remove manual wasteds
          w.manual = manual;
          logUpdate(w, WASTED_CODE);
        }
      } else {
        w = new Wasted(getNextWizID());
        w.airingID = basedOn.id;
        w.myAiring = basedOn;
        w.manual = manual;
        getTable(WASTED_CODE).add(w, true);
        if (Sage.DBG) System.out.println("Added:" + w);
      }
    } finally {
      releaseWriteLock(WASTED_CODE);
    }
    if (manual && Sage.getBoolean("apply_dont_like_at_show_level", true)) {
      Show s = basedOn.getShow();
      if (s != null) {
        s.setDontLike(true);
      }
    }
    return w;
  }

  void removeWasted(Wasted removeMe)
  {
    if (removeMe == null) return;
    Table t = getTable(WASTED_CODE);
    t.remove(removeMe, true);
    Airing a = getAiringForID(removeMe.airingID);
    if (a != null) {
      Show s = a.getShow();
      if (s != null) {
        s.setDontLike(false);
      }
    }
  }

  public Wasted[] getWasted()
  {
    if (loading) return new Wasted[0];

    Table t = getTable(WASTED_CODE);
    try {
      t.acquireReadLock();
      Wasted[] rv = new Wasted[t.num];
      System.arraycopy(t.primary.data, 0, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  public Wasted getWastedForAiring(Airing air)
  {
    if (air != null) return getWastedForAiring(air.id);
    else return null;
  }
  Wasted getWastedForAiring(int airingID)
  {
    Index indy = getIndex(WASTED_CODE);
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Wasted midVal = (Wasted) indy.data[mid];
        int cmp = midVal.airingID - airingID;

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          return midVal;
        }
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return null;
  }

  public Agent addAgent(int agentMask, String title, String category, String subCategory,
      Person person, int role, String rated, String year, String pr, String network,
      String chanName, int slotType, int[] timeslots, String keyword)
  {
    Agent bond;
    try {
      acquireWriteLock(AGENT_CODE);
      bond = new Agent(getNextWizID());
      bond.agentMask = agentMask;
      bond.title = getTitleForName(title);
      bond.category = getCategoryForName(category);
      bond.subCategory = getSubCategoryForName(subCategory);
      bond.person = person;
      bond.role = role;
      bond.rated = getRatedForName(rated);
      bond.year = getYearForName(year);
      bond.pr = getPRForName(pr);
      bond.network = getNetworkForName(network);
      bond.chanName = new String((chanName == null) ? "" : chanName);
      bond.slotType = slotType;
      bond.timeslots = timeslots == null ? null : ((int[]) timeslots.clone());
      bond.keyword = new String((keyword == null) ? "" : keyword);
      getTable(AGENT_CODE).add(bond, true);
    } finally {
      releaseWriteLock(AGENT_CODE);
    }
    if (Sage.DBG) System.out.println("Added:" + bond);
    return bond;
  }

  public void removeAgent(Agent removeMe)
  {
    if (removeMe == null) return;
    Table t = getTable(AGENT_CODE);
    t.remove(removeMe, true);
  }

  public Agent[] getAgents()
  {
    if (loading) return new Agent[0];

    Index indy = getIndex(AGENT_CODE, AGENTS_BY_CARNY_CODE);
    try {
      indy.table.acquireReadLock();
      Agent[] rv = new Agent[indy.table.num];
      System.arraycopy(indy.data, 0, rv, 0, rv.length);
      return rv;
    } finally {
      indy.table.releaseReadLock();
    }
  }

  public Agent[] getFavorites()
  {
    if (loading) return new Agent[0];

    Index indy = getIndex(AGENT_CODE, AGENTS_BY_CARNY_CODE);
    ArrayList<Agent> rv = new ArrayList<Agent>();
    try {
      indy.table.acquireReadLock();
      for (int i = 0; i < indy.table.num; i++)
        if ((((Agent) indy.data[i]).agentMask & Agent.LOVE_MASK) != 0)
          rv.add((Agent)indy.data[i]);
    } finally {
      indy.table.releaseReadLock();
    }
    return rv.toArray(new Agent[0]);
  }

  public Airing getPlayableAiring(Airing air)
  {
    if (air == null)
      return null;
    MediaFile mf = getFileForAiring(air);
    if (mf != null && mf.isCompleteRecording())
      return air;
    // OK, we've checked the options for this Airing itself, now see if the Show can reference other identical airings
    if (BigBrother.isUnique(air))
    {
      Show s = air.getShow();
      if (s != null)
      {
        Airing[] otherAirs = getAirings(s, 0);
        int len = otherAirs.length;
        for (int i = 0; i < len; i++)
        {
          Airing testAir = otherAirs[i];
          mf = getFileForAiring(testAir);
          if (mf != null && mf.isCompleteRecording())
            return otherAirs[i];
        }
      }
    }
    return null;
  }

  ManualRecord addManualRecord(Airing recAir)
  {
    return addManualRecord(recAir, 0);
  }
  ManualRecord addManualRecord(Airing recAir, long providerID)
  {
    return addManualRecord(recAir.time, recAir.duration, providerID, recAir.stationID, "", "", recAir.id, 0);
  }

  ManualRecord addManualRecord(long startTime, long duration, long providerID, int stationID, String recFilename,
      String encodingProfile, int infoAiringID, int recurrence)
  {
    ManualRecord rec;
    try {
      acquireWriteLock(MANUAL_CODE);
      // Double check here to make sure we don't already have an existing manual record on this airing object
      if (infoAiringID != 0)
      {
        rec = getManualRecord(infoAiringID);
        if (rec != null)
        {
          if (Sage.DBG) System.out.println("DUPLICATE ManualRecord add attempt...return the existing one: " + rec);
          return rec;
        }
      }
      rec = new ManualRecord(getNextWizID());
      rec.infoAiringID = infoAiringID;
      rec.duration = duration;
      rec.startTime = startTime;
      rec.providerID = providerID;
      rec.stationID = stationID;
      rec.extraInfo = new String[2];
      rec.extraInfo[ManualRecord.FILENAME] = recFilename;
      rec.extraInfo[ManualRecord.ENCODING_PROFILE] = encodingProfile;
      rec.recur = recurrence;
      getTable(MANUAL_CODE).add(rec, true);
    } finally {
      releaseWriteLock(MANUAL_CODE);
    }
    if (Sage.DBG) System.out.println("Added:" + rec);
    return rec;
  }

  ManualRecord modifyManualRecord(long modifyStartTime, long modifyEndTime, ManualRecord orgMR)
  {
    try {
      acquireWriteLock(MANUAL_CODE);
      orgMR.startTime += modifyStartTime;
      orgMR.duration += (modifyEndTime - modifyStartTime);
      logUpdate(orgMR, MANUAL_CODE);
      orgMR.getSchedulingAiring(); // updates the airings time/dur
    } finally {
      releaseWriteLock(MANUAL_CODE);
    }
    if (Sage.DBG) System.out.println("Modified:" + orgMR);
    return orgMR;
  }

  void removeManualRecord(ManualRecord removeMe)
  {
    if (removeMe == null) return;
    Table t = getTable(MANUAL_CODE);
    t.remove(removeMe, true);
  }

  public ManualRecord[] getManualRecords()
  {
    if (loading) return new ManualRecord[0];

    Table t = getTable(MANUAL_CODE);
    try {
      t.acquireReadLock();
      ManualRecord[] rv = new ManualRecord[t.num];
      System.arraycopy(t.primary.data, 0, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  ManualRecord[] getManualRecordsSortedByTime() {
    ManualRecord[] manualMustSee = getManualRecords();
    // Sorting these by time should speed up the iterative scheduling process
    Arrays.sort(manualMustSee, new Comparator<ManualRecord>() {
      public int compare(ManualRecord mr1, ManualRecord mr2) {
        if (mr1.startTime < mr2.startTime)
          return -1;
        else if (mr1.startTime > mr2.startTime)
          return 1;
        else if (mr1.duration < mr2.duration) // end time since start times are equal
          return -1;
        else if (mr1.duration > mr2.duration) // end time since start times are equal
          return 1;
        else
          return mr1.stationID - mr2.stationID; // for consistency
      }
    });
    return manualMustSee;
  }

  public ManualRecord getManualRecord(Airing recAir)
  {
    if (recAir == null) return null;
    if (recAir instanceof ManualRecord.FakeAiring)
    {
      ManualRecord rv = ((ManualRecord.FakeAiring) recAir).getManualRecord();
      if (isManualRecordOK(rv))
        return rv;
      else
        return null;
    }
    return getManualRecord(recAir.id);
  }

  ManualRecord getManualRecord(int airingID)
  {
    if (airingID == 0) return null;
    // This one'll most likely never be that big so do this linearly
    Table t = getTable(MANUAL_CODE);
    try {
      t.acquireReadLock();
      for (int i = 0; i < t.num; i++)
      {
        if (((ManualRecord) t.primary.data[i]).infoAiringID == airingID ||
            ((ManualRecord) t.primary.data[i]).id == airingID)
          return (ManualRecord) t.primary.data[i];
      }
    } finally {
      t.releaseReadLock();
    }
    return null;
  }

  boolean isManualRecordOK(ManualRecord checkMe)
  {
    Table t = getTable(MANUAL_CODE);
    try {
      t.acquireReadLock();
      for (int i = 0; i < t.num; i++)
      {
        if (t.primary.data[i] == checkMe)
          return true;
      }
    } finally {
      t.releaseReadLock();
    }
    return false;
  }

  public Show getShowForExternalID(String extID)
  {
    Index indy = getIndex(SHOW_CODE, SHOWS_BY_EXTID_CODE);
    byte[] extIDbytes;
    try
    {
      extIDbytes = extID.getBytes(Sage.I18N_CHARSET);
    }
    catch (Exception e) { extIDbytes = extID.getBytes(); }
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Show midVal = (Show) indy.data[mid];
        int cmp = byteStringCompare(midVal.externalID, extIDbytes);

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
          return midVal;
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return null;
  }

  public Show[] getShowsForExternalIDPrefix(String extID)
  {
    Index indy = getIndex(SHOW_CODE, SHOWS_BY_EXTID_CODE);
    byte[] extIDbytes;
    try
    {
      extIDbytes = extID.getBytes(Sage.I18N_CHARSET);
    }
    catch (Exception e) { extIDbytes = extID.getBytes(); }
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;
      int index = -1;
      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Show midVal = (Show) indy.data[mid];
        int cmp = byteStringPrefix(midVal.externalID, extIDbytes);

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          index = mid;
          break;
        }
      }
      if (index == -1)
      {
        // Nothing for this Show
        return new Show[0];
      }
      else
      {
        while (index > 0)
        {
          if (byteStringPrefix(((Show) indy.data[index - 1]).externalID, extIDbytes) == 0)
            index--;
          else
            break;
        }
      }
      int index2 = index;
      while (index2 < indy.table.num - 1)
      {
        if (byteStringPrefix(((Show) indy.data[index2 + 1]).externalID, extIDbytes) == 0)
          index2++;
        else
          break;
      }
      Show[] rv = new Show[index2 - index + 1];
      System.arraycopy(indy.data, index, rv, 0, rv.length);
      return rv;
    } finally {
      indy.table.releaseReadLock();
    }
  }

  public boolean doesShowHaveEPs(Show testMe)
  {
    // This needs a SHxxxxxxx EPG ID which it then will convert to an EPxxxxxx ID and then
    // try to look it up in the database and see if there's episodes that match it.
    // The last 4 digits of the ID is the episode number.
    if (testMe == null) return false;
    if (testMe.externalID.length < 3 || testMe.externalID[0] != 'S' || testMe.externalID[1] != 'H') return false;
    byte[] testBytes = new byte[testMe.externalID.length];
    System.arraycopy(testMe.externalID, 0, testBytes, 0, testBytes.length);
    testBytes[0] = 'E';
    testBytes[1] = 'P';
    Index indy = getIndex(SHOW_CODE, SHOWS_BY_EXTID_CODE);
    try {
      indy.table.acquireReadLock();
      int low = 0;
      int high = indy.table.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
      Show midVal = (Show) indy.data[mid];
      int cmp = byteStringCompare(midVal.externalID, testBytes);

      if (cmp < 0)
        low = mid + 1;
      else if (cmp > 0)
        high = mid - 1;
      else
        break;
      }
      if (low >= 0 && low < indy.table.num)
      {
        Show s = (Show) indy.data[low];
        if (s != null && s.externalID.length == testBytes.length)
        {
          boolean noMatch = false;
          for (int i = 2; i < testBytes.length - 4; i++)
          {
            if (testBytes[i] != s.externalID[i])
            {
              noMatch = true;
              break;
            }
          }
          if (!noMatch)
            return true;
        }
      }
      if (high >= 0 && high < indy.table.num)
      {
        Show s = (Show) indy.data[high];
        if (s != null && s.externalID.length == testBytes.length)
        {
          boolean noMatch = false;
          for (int i = 2; i < testBytes.length - 4; i++)
          {
            if (testBytes[i] != s.externalID[i])
            {
              noMatch = true;
              break;
            }
          }
          if (!noMatch)
            return true;
        }
      }
    } finally {
      indy.table.releaseReadLock();
    }
    return false;
  }

  public static int byteStringCompare(byte[] b1, byte[] b2)
  {
    int numComps = Math.min(b1.length, b2.length);
    for (int i = 0; i < numComps; i++)
    {
      int res = (b1[i]&0xFF) - (b2[i]&0xFF);
      if (res != 0)
        return res;
    }
    if (b2.length > b1.length)
      return -1;
    else if (b2.length == b1.length)
      return 0;
    else
      return 1;
  }

  public static int byteStringPrefix(byte[] b1, byte[] b2)
  {
    int numComps = Math.min(b1.length, b2.length);
    for (int i = 0; i < numComps; i++)
    {
      int res = (b1[i]&0xFF) - (b2[i]&0xFF);
      if (res != 0)
        return res;
    }
    return 0;
  }

  public Person[] getPeopleArray(String[] names)
  {
    if (names == null || names.length == 0)
      return Pooler.EMPTY_PERSON_ARRAY;
    Person[] rv = new Person[names.length];
    for (int i = 0; i < names.length; i++)
      rv[i] = getPersonForName(names[i]);
    return rv;
  }

  public boolean addShowPublic(String title, String primeTitle, String episodeName, String desc, long duration, String category,
      String subCategory, String[] people, byte[] roles, String rated, String[] expandedRatings,
      String year, String parentalRating, String[] bonus, String extID, String language, long originalAirDate)
  {
    return (addShow(title, episodeName, desc, duration, new String[] {category, subCategory}, getPeopleArray(people), roles, rated,
        expandedRatings, year, parentalRating, bonus, extID, language, originalAirDate, true, 0, (short)0, (short)0,
        (short)0, false, 0, 0, Pooler.EMPTY_SHORT_ARRAY, Pooler.EMPTY_2D_BYTE_ARRAY) != null);
  }

  public boolean addShowPublic2(String title, String episodeName, String desc, long duration, String[] categories,
      String[] people, byte[] roles, String rated, String[] expandedRatings,
      String year, String parentalRating, String[] bonus, String extID, String language, long originalAirDate,
      short seasonNum, short episodeNum, boolean forcedUnique)
  {
    return (addShow(title, episodeName, desc, duration, categories, getPeopleArray(people), roles, rated,
        expandedRatings, year, parentalRating, bonus, extID, language, originalAirDate, true, 0, seasonNum, episodeNum,
        (short)0, forcedUnique, 0, 0, Pooler.EMPTY_SHORT_ARRAY, Pooler.EMPTY_2D_BYTE_ARRAY) != null);
  }

  public Show addShow(String title, String primeTitle, String episodeName, String desc, long duration, String[] categories,
      String[] people, byte[] roles, String rated, String[] expandedRatings,
      String year, String parentalRating, String[] bonus, String extID, String language, long originalAirDate,
      int mediaMask, short seasonNum, short episodeNum, boolean forcedUnique, byte photoCountTall, byte photoCountWide, byte photoThumbCountTall,
      byte photoThumbCountWide, byte posterCountTall, byte posterCountWide, byte posterThumbCountTall, byte posterThumbCountWide)
  {
    return addShow(title, episodeName, desc, duration, categories, getPeopleArray(people), roles, rated, expandedRatings,
        year, parentalRating, bonus, extID, language, originalAirDate, false, mediaMask, seasonNum, episodeNum, (short)0,
        forcedUnique, 0, 0, Show.convertLegacyShowImageData(photoCountTall, photoCountWide, posterCountTall, posterCountWide),
        Pooler.EMPTY_2D_BYTE_ARRAY);
  }

  public Show addShow(String title, String episodeName, String desc, long duration, String[] categories,
                      Person[] people, byte[] roles, String rated, String[] expandedRatings,
                      String year, String parentalRating, String[] bonus, String extID, String language, long originalAirDate,
                      int mediaMask, short seasonNum, short episodeNum, boolean forcedUnique, int showcardID, byte urls[][])
  {
    return addShow(title, episodeName, desc, duration, categories, people, roles, rated, expandedRatings,
        year, parentalRating, bonus, extID, language, originalAirDate, false, mediaMask, seasonNum, episodeNum, (short)0,
        forcedUnique, showcardID, 0, Pooler.EMPTY_SHORT_ARRAY, urls);
  }

  public Show addShow(String title, String episodeName, String desc, long duration, String[] categories,
                      String[] people, byte[] roles, String rated, String[] expandedRatings,
                      String year, String parentalRating, String[] bonus, String extID, String language, long originalAirDate,
                      int mediaMask, short seasonNum, short episodeNum, boolean forcedUnique, int showcardID, byte urls[][])
  {
    return addShow(title, episodeName, desc, duration, categories, getPeopleArray(people), roles, rated, expandedRatings,
        year, parentalRating, bonus, extID, language, originalAirDate, false, mediaMask, seasonNum, episodeNum, (short)0,
        forcedUnique, showcardID, 0, Pooler.EMPTY_SHORT_ARRAY, urls);
  }

  public Show addShow(String title, String episodeName, String desc, long duration, String[] categories,
      Person[] people, byte[] roles, String rated, String[] expandedRatings,
      String year, String parentalRating, String[] bonus, String extID, String language, long originalAirDate,
      int mediaMask, short seasonNum, short episodeNum, short altEpisodeNum, boolean forcedUnique, int showcardID, int seriesID,
      short[] imageIDs)
  {
    return addShow(title, episodeName, desc, duration, categories, people, roles, rated, expandedRatings, year, parentalRating,
        bonus, extID, language, originalAirDate, false, mediaMask, seasonNum, episodeNum, altEpisodeNum, forcedUnique, showcardID,
        seriesID, imageIDs, Pooler.EMPTY_2D_BYTE_ARRAY);
  }

  private Show addShow(String title, String episodeName, String desc, long duration, String[] categories,
      Person[] people, byte[] roles, String rated, String[] expandedRatings,
      String year, String parentalRating, String[] bonus, String extID, String language, long originalAirDate, boolean fromAPlugin,
      int mediaMask, short seasonNum, short episodeNum, short altEpisodeNum, boolean forcedUnique, int showcardID, int seriesID,
      short[] imageIDs, byte[][] imageURLs)
  {
    // Double check this first because clients can't synchronize on our structure
    Show oldShow = getShowForExternalID(extID);
    Show s;
    try {
      acquireWriteLock(SHOW_CODE);
      s = (oldShow != null) ? (Show) oldShow.clone() : new Show(getNextWizID());
      s.duration = duration;
      s.title = getTitleForName(title, mediaMask);
      s.episodeNameStr = (episodeName == null) ? "" : new String(episodeName);
      s.episodeNameBytes = null;
      s.descStr = (desc == null) ? "" : new String(desc);
      s.descBytes = null;
      if (!fromAPlugin && extID.startsWith("MV") && (categories == null || categories.length == 0 || !Sage.rez("Movie").equals(categories[0])))
      {
        if (categories == null)
        {
          categories = new String[1];
          categories[0] = Sage.rez("Movie");
        }
        else
        {
          String[] temp = new String[categories.length + 1];
          temp[0] = Sage.rez("Movie");
          System.arraycopy(categories, 0, temp, 1, categories.length);
          categories = temp;
        }
      }

      categories = removeNullAndEmptyEntries(categories);
      s.categories = new Stringer[categories == null ? 0 : categories.length];
      for (int i = 0; categories != null && i < categories.length; i++)
      {
        s.categories[i] = (i == 0) ? getCategoryForName(categories[i], mediaMask) : getSubCategoryForName(categories[i], mediaMask);
      }
      if (people == null || people.length == 0)
      {
        s.people = Pooler.EMPTY_PERSON_ARRAY;
        s.roles = Pooler.EMPTY_BYTE_ARRAY;
      }
      else
      {
        s.people = new Person[people.length];
        s.roles = new byte[people.length];
        for (int i = 0; i < people.length; i++)
        {
          s.people[i] = people[i];
          s.roles[i] = roles[i];
        }
      }
      s.rated = getRatedForName(rated, mediaMask);
      expandedRatings = removeNullAndEmptyEntries(expandedRatings);
      if (expandedRatings == null || expandedRatings.length == 0) s.ers = Pooler.EMPTY_STRINGER_ARRAY;
      else
      {
        s.ers = new Stringer[expandedRatings.length];
        for (int i = 0; i < expandedRatings.length; i++)
          s.ers[i] = getERForName(expandedRatings[i], mediaMask);
      }
      s.year = getYearForName(year, mediaMask);
      s.pr = getPRForName(parentalRating, mediaMask);
      bonus = removeNullAndEmptyEntries(bonus);
      if (bonus == null || bonus.length == 0) s.bonuses = Pooler.EMPTY_STRINGER_ARRAY;
      else
      {
        s.bonuses = new Stringer[bonus.length];
        for (int i = 0; i < bonus.length; i++)
          s.bonuses[i] = getBonusForName(bonus[i], mediaMask);
      }
      try
      {
        s.externalID = extID.getBytes(Sage.I18N_CHARSET);
      }
      catch (Exception e) { s.externalID = extID.getBytes(); }
      s.language = getBonusForName(language, mediaMask);
      s.originalAirDate = originalAirDate;
      s.setMediaMask((s.getMediaMask() | mediaMask ) & DBObject.MEDIA_MASK_ALL);
      s.seasonNum = seasonNum;
      s.episodeNum = episodeNum;
      s.altEpisodeNum = altEpisodeNum;
      s.seriesID = seriesID;
      s.showcardID = showcardID;
      s.imageIDs = (imageIDs == null ? Pooler.EMPTY_SHORT_ARRAY : ((short[]) imageIDs.clone()));
      if (imageURLs == null || imageURLs.length == 0)
        s.imageURLs = Pooler.EMPTY_2D_BYTE_ARRAY;
      else
        s.imageURLs = imageURLs.clone();

      if (forcedUnique)
        s.cachedUnique = Show.FORCED_UNIQUE;

      if (oldShow != null)
      {
        if (!oldShow.isIdentical(s))
        {
          getTable(SHOW_CODE).update(oldShow, s, true);
        }
        updateLastModified(oldShow.getMediaMask());
        return oldShow;
      }
      else
      {
        getTable(SHOW_CODE).add(s, true);
      }
    } finally {
      releaseWriteLock(SHOW_CODE);
    }

    return s;
  }

  // This is only called by the external EPG plugins, so it should always be TV data
  public boolean addAiringPublic(String extID, int stationID, long startTime, long duration)
  {
    return (addAiring(extID, stationID, startTime, duration, (byte)0, (byte)0, (byte)0, DBObject.MEDIA_MASK_TV) != null);
  }

  public boolean addAiringDetailedPublic(String extID, int stationID, long startTime, long duration, int partNumber, int totalParts,
      String parentalRating, boolean hdtv, boolean stereo, boolean closedCaptioning, boolean sap, boolean subtitled, String premierFinale)
  {
    byte partsB = 0;
    if (totalParts > 0)
    {
      partsB = (byte) (totalParts & 0x0F);
      partsB = (byte)((partsB | ((partNumber  & 0x0F)<< 4)) & 0xFF);
    }
    else
    {
      partsB = (byte) (partNumber & 0xFF);
    }
    byte prB = 0;
    if (parentalRating != null && parentalRating.length() > 0)
    {
      for (int i = 0; i < Airing.PR_NAMES.length; i++)
      {
        if (Airing.PR_NAMES[i].equals(parentalRating))
        {
          prB = (byte) i;
          break;
        }
      }
    }
    byte miscB = 0;
    if (subtitled)
      miscB |= Airing.SUBTITLE_MASK;
    if (sap)
      miscB |= Airing.SAP_MASK;
    if (closedCaptioning)
      miscB |= Airing.CC_MASK;
    if (stereo)
      miscB |= Airing.STEREO_MASK;
    if (hdtv)
      miscB |= Airing.HDTV_MASK;
    if (premierFinale != null && premierFinale.length() > 0)
    {
      if (Sage.rez("Premiere").equals(premierFinale))
        miscB |= Airing.PREMIERE_MASK;
      else if (Sage.rez("Season_Premiere").equals(premierFinale))
        miscB |= Airing.SEASON_PREMIERE_MASK;
      else if (Sage.rez("Season_Finale").equals(premierFinale))
        miscB |= Airing.SEASON_FINALE_MASK;
      else if (Sage.rez("Series_Finale").equals(premierFinale))
        miscB |= Airing.SERIES_FINALE_MASK;
      else if (Sage.rez("Series_Premiere").equals(premierFinale))
        miscB |= Airing.SERIES_PREMIERE_MASK;
      else if (Sage.rez("Channel_Premiere").equals(premierFinale))
        miscB |= Airing.CHANNEL_PREMIERE_MASK;
    }
    return (addAiring(extID, stationID, startTime, duration, partsB, miscB & 0xFF, prB, DBObject.MEDIA_MASK_TV) != null);
  }

  public boolean addAiringPublic2(String extID, int stationID, long startTime, long duration, byte partsByte, int misc, String parentalRating)
  {
    byte prB = 0;
    if (parentalRating != null && parentalRating.length() > 0)
    {
      for (int i = 0; i < Airing.PR_NAMES.length; i++)
      {
        if (Airing.PR_NAMES[i].equals(parentalRating))
        {
          prB = (byte) i;
          break;
        }
      }
    }
    return (addAiring(extID, stationID, startTime, duration, partsByte, misc, prB, DBObject.MEDIA_MASK_TV) != null);
  }

  // 601 Airing addAiring(...
  public Airing addAiring(String extID, int stationID, long startTime, long duration,
      byte partsByte, int misc, byte prByte, int mediaMask)
  {
    Show oldShow = getShowForExternalID(extID);
    if (oldShow == null) return null;
    return addAiring(oldShow, stationID, startTime, duration, partsByte, misc, prByte, mediaMask);
  }
  public Airing addAiring(Show theShow, int stationID, long startTime, long duration,
      byte partsByte, int misc, byte prByte, int mediaMask)
  {
    if (duration == 0)
    {
      throw new IllegalArgumentException("Cannot add airing of zero duration, " + theShow.toString() +
          " chan=" + stationID + " time=" + startTime);
    }

    // Fix the media mask if this is from an API call and it's for TV
    if (mediaMask == 0 && stationID != 0)
      mediaMask = DBObject.MEDIA_MASK_TV;

    // Ensure that the channel exists
    Channel realChan = getChannelForStationID(stationID);
    if (realChan == null)
    {
      realChan = addChannel("", "", null, stationID, 0, null);
    }

    Table t = getTable(AIRING_CODE);
    Airing a;
    // We need to do this outside of the Airing sync block or we can deadlock on adding new media files that add a new airing
    ArrayList<Airing> overlapsToCheckForMFs = null;
    try {
      t.acquireWriteLock();
      // for any overlapping MRs that we remove
      long mrStart = Long.MAX_VALUE;
      long mrEnd = 0;
      Properties mrProps = null;
      String mrQuality = "";
      String overlapTitle = null;
      long overlapStart = Long.MAX_VALUE;
      long overlapEnd = 0;
      Airing[] overlaps = null;
      boolean epgCheck = false;
      if (stationID > 0)
      {
        // First check for overlap in time
        overlaps = getAirings(stationID, startTime, startTime + duration - 1, false);
        epgCheck = true;
      }
      if (overlaps != null && overlaps.length != 0)
      {
        // Remove these overlaps, unless it's one and a match.
        // If they're noshow Airings, then resize them to fit
        Airing singularOverlap = null;
        if (epgCheck)
        {
          if (overlaps.length == 1 && overlaps[0].showID == theShow.id &&
              overlaps[0].time == startTime && overlaps[0].duration == duration)
            singularOverlap = overlaps[0];
        }
        else
        {
          for (int i = 0; i < overlaps.length; i++)
          {
            if (overlaps[i].stationID == 0 && overlaps[i].getMediaMask() == mediaMask)
            {
              if (overlaps[i].time == startTime && overlaps[i].duration == duration)
                singularOverlap = overlaps[i];
              break;
            }
          }
        }
        if (singularOverlap != null)
        {
          if (singularOverlap.partsB == partsByte && singularOverlap.miscB == misc &&
              singularOverlap.prB == prByte && singularOverlap.getMediaMask() == mediaMask) {
            return singularOverlap;
          }
          else
          {
            Airing airClone = (Airing) singularOverlap.clone();
            airClone.partsB = partsByte;
            airClone.miscB = misc;
            airClone.prB = prByte;
            airClone.setMediaMask(mediaMask);
            t.update(singularOverlap, airClone, true);
            return singularOverlap;
          }
        }
        for (int i = 0; i < overlaps.length; i++)
        {
          if (!epgCheck)
          {
            if (overlaps[i].stationID != 0 || overlaps[i].getMediaMask() != mediaMask)
              continue;
          }
          // If any of these have ManualRecords associated with them, update the
          // ManualRecords to the new airing ID. There may be more than one MR
          // that overlaps this new air, so aggregate the total record time
          // from all the overlaps to create the new one
          ManualRecord mr = getManualRecord(overlaps[i]);
          if (mr != null && mr.infoAiringID != 0)
          {
            // this should be synchronized somehow, but there's so many
            // async cases that are weird regarding recordings
            overlapTitle = mr.getContentAiring().getTitle();
            mrStart = Math.min(mrStart, mr.getStartTime());
            mrEnd = Math.max(mrEnd, mr.getEndTime());
            mrQuality = mr.getRecordingQuality();
            if (mr.mrProps != null)
            {
              if (mrProps == null)
                mrProps = new Properties();
              mrProps.putAll(mr.mrProps);
            }
            removeManualRecord(mr);
          }
          if (overlapsToCheckForMFs == null)
            overlapsToCheckForMFs = new ArrayList<Airing>();
          overlapsToCheckForMFs.add(overlaps[i]);
          overlapStart = Math.min(overlapStart, overlaps[i].getStartTime());
          overlapEnd = Math.max(overlapEnd, overlaps[i].getEndTime());
          //System.out.println("REMOVING AIRING=" + overlaps[i]);
          t.remove(overlaps[i], true);
        }
      }
      a = new Airing(getNextWizID());
      a.showID = theShow.id;
      a.stationID = stationID;
      a.time = startTime;
      a.duration = duration;
      a.partsB = partsByte;
      a.miscB = misc;
      a.prB = prByte;
      a.setMediaMask(mediaMask);
      t.add(a, true);
      if (theShow.isDTExternalID() && overlapEnd > 0)
      {
        // Patch the holes in the airing grid immediately for these to keep the EPG clean
        // This is inefficient in terms of creating extra database transactions; but it's the only solid way
        // to ensure we don't have holes in the EPG grid, which are very bad for usability
        if (overlapStart < startTime)
        {
          // Patch the hole we created before the airing
          Airing noShowAdd = new Airing(getNextWizID());
          noShowAdd.showID = noShow.id;
          noShowAdd.stationID = stationID;
          noShowAdd.time = overlapStart;
          noShowAdd.duration = startTime - overlapStart;
          noShowAdd.setMediaMask(DBObject.MEDIA_MASK_TV);
          t.add(noShowAdd, true);
        }
        if (overlapEnd > startTime + duration)
        {
          // Patch the hole we created after the airing
          Airing noShowAdd = new Airing(getNextWizID());
          noShowAdd.showID = noShow.id;
          noShowAdd.stationID = stationID;
          noShowAdd.time = startTime + duration;
          noShowAdd.duration = overlapEnd - noShowAdd.time;
          noShowAdd.setMediaMask(DBObject.MEDIA_MASK_TV);
          t.add(noShowAdd, true);
        }
      }
      if (mrEnd != 0 && Sage.getBoolean("repair_epg_manual_record_mismatches", true))
      {
        long overlapAmount = Math.min(mrEnd, a.getEndTime()) - Math.max(mrStart, a.getStartTime());
        if (overlapAmount >= (mrEnd - mrStart)/2)
        {
          ManualRecord newMR = addManualRecord(mrStart, mrEnd - mrStart, 0, stationID, "", mrQuality, a.id, 0);
          newMR.setProperties(mrProps);
          if (Sage.DBG) System.out.println("Modified EPG data linkage for existing manual record: " + newMR + " overlapAir=" + a);
          if (overlapTitle == null || !overlapTitle.equals(a.getTitle()))
          {
            // Submit a message if the title has changed for the show
            MsgManager.postMessage(SystemMessage.createEpgLinkageChangedMsg(overlapTitle, mrStart, mrEnd - mrStart,
                realChan, a.getTitle()));
          }
        }
        else
        {
          ManualRecord newMR = addManualRecord(mrStart, mrEnd - mrStart, 0, stationID, "", mrQuality, 0, 0);
          newMR.setProperties(mrProps);
          if (Sage.DBG) System.out.println("Converted manual record to time-based recording since it no longer aligns with EPG data: " + newMR +
              " overlapAir=" + a);
          MsgManager.postMessage(SystemMessage.createEpgLinkageChangedMsg(overlapTitle, mrStart, mrEnd - mrStart,
              realChan, newMR.getContentAiring().getTitle()));
        }
      }
    } finally {
      t.releaseWriteLock();
    }
    if (overlapsToCheckForMFs != null)
    {
      boolean fileRemapped = false;
      for (int i = 0; i < overlapsToCheckForMFs.size(); i++)
      {
        // Check for any MediaFiles that use this airing, and then relink the new airing
        // to it.
        Airing tempAir = overlapsToCheckForMFs.get(i);
        MediaFile mf = getFileForAiring(tempAir);
        if (mf != null)
        {
          // NOTE: There's a logical inconsistency here because we don't allow multiple airings linked
          // to the same media file. But its an improvement on totally losing the link like we did before.
          // So now we fixed the bug if the data changes, but we're still susceptible to loss
          // if the times change.
          // UPDATE: Only ever link one MediaFile to this new Airing, because doing anything else will
          // result in an inconsistency where one file is not properly reachable in all situations.
          // NOTE: If this overlap doesn't cover the majority of the time then just create
          // a new Airing with no valid channel so it doesn't get removed; but only do this
          // if the recording is over so we don't mess anything up with the Seeker (i.e. recording a File on non-existent channel)
          if (!mf.isRecording() || fileRemapped)
          {
            long overlapAmount = Math.min(tempAir.getEndTime(), a.getEndTime()) - Math.max(tempAir.getStartTime(),
                a.getStartTime());
            if (overlapAmount >= tempAir.getDuration()/2 && !fileRemapped)
            {
              if (Sage.DBG) System.out.println("Updating metadata w/ new Airing for existing recording from: " + mf + " to be " + a);
              mf.setInfoAiring(a);
              fileRemapped = true;
            }
            else
            {
              Airing fooAir = addAiring(tempAir.getShow(), 0, tempAir.time, tempAir.duration, tempAir.partsB, tempAir.miscB,
                  tempAir.prB, tempAir.getMediaMask());
              if (Sage.DBG) System.out.println("Updating metadata by creating alternate Airing for existing recording from: " + mf + " to be " + fooAir);
              mf.setInfoAiring(fooAir);
            }
          }
          else {
            mf.setInfoAiring(a);
            fileRemapped = true;
          }
        }
        // This'll fix any Favorites in Carny's map that get flipped during this update. It *may* temporarily
        // cause invalid favorites to be added; but it'll recover swapped favorites in the significant majority of cases
        // where the EPG data has simply changed metadata (and not time)
        god.notifyAiringSwap(tempAir, a);
      }
    }

    // NOTE: Narflex: I think we want to do this....but this is new to the system, so I'm still figuring it out...
    if (!isNoShow(theShow) && !theShow.hasMediaMask(mediaMask))
    {
      theShow.addMediaMaskRecursive(mediaMask);
    }
    updateLastModified(mediaMask);
    //System.out.println("Added:" + a);
    return a;
  }

  public Airing getTimeRelativeAiring(Airing baseAir, int relativeAmount)
  {
    Table t = getTable(AIRING_CODE);
    Index indy = t.getIndex(AIRINGS_BY_CT_CODE);
    try {
      t.acquireReadLock();
      if (loading) return baseAir;
      int idx = indy.binarySearch(baseAir);
      if ((idx == -1) || (idx + relativeAmount < 0) || (idx + relativeAmount >= t.num)) return baseAir;
      if (((Airing) indy.data[idx + relativeAmount]).stationID != baseAir.stationID) return baseAir;
      return (Airing) indy.data[idx + relativeAmount];
    } finally {
      t.releaseReadLock();
    }
  }

  public Airing[] getAirings(int stationID, long startTime, long endTime,
      boolean mustStart)
  {
    Table t = getTable(AIRING_CODE);
    Index indy = t.getIndex(AIRINGS_BY_CT_CODE);
    try {
      t.acquireReadLock();
      if (loading) return Pooler.EMPTY_AIRING_ARRAY;

      int index, index2;
      int low = 0;
      int high = t.num - 1;
      index = -1;
      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Airing midVal = (Airing) indy.data[mid];
        long cmp = midVal.stationID - stationID;
        if (cmp == 0)
          cmp = sign(midVal.time - startTime);

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          index = mid; // key found
          break;
        }
      }
      if (index == -1)
      {
        index = low;
        // No exact matching time found, so we backup one if we can miss the start
        // But check to be sure something is in that spot with no time gap
        if (!mustStart && (index > 0))
        {
          Airing prevAir = (Airing) indy.data[index - 1];
          if (prevAir.stationID == stationID &&
              (prevAir.time + prevAir.duration > startTime)) index--;
        }
      }
      index2 = index;
      for (; index2 < t.num; index2++)
      {
        Airing a = (Airing) indy.data[index2];
        if ((a.time >= endTime) || a.stationID != stationID)
        {
          break;
        }
      }
      Airing[] rv = new Airing[index2 - index];
      if (rv.length > 0) System.arraycopy(indy.data, index, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  public Airing[] getAirings(Show forMe, long startingAfter)
  {
    return getAirings(forMe, startingAfter, false);
  }
  // indicatorOnly mode returns an empty Array if there are a nonzero count of these
  // and null otherwise, this is used in maintenance to avoid the overhead of returning the populated array
  Airing[] getAirings(Show forMe, long startingAfter, boolean indicatorOnly)
  {
    if (forMe == null)
      return indicatorOnly ? null : Pooler.EMPTY_AIRING_ARRAY;
    // NARFLEX - 4/20/09 - We've used 0 to indicate include all; but there's some error cases with pictures having
    // negative timestamps on them, probably due to time zone issues or something like that. This is an easy fix for
    // the problem.
    if (startingAfter == 0)
      startingAfter = Long.MIN_VALUE;
    Table t = getTable(AIRING_CODE);
    Index indy = t.getIndex(AIRINGS_BY_SHOW_CODE);
    try {
      t.acquireReadLock();
      if (loading) return Pooler.EMPTY_AIRING_ARRAY;

      int index;
      int low = 0;
      int high = t.num - 1;
      index = -1;
      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Airing midVal = (Airing) indy.data[mid];
        int cmp = midVal.showID - forMe.id;
        if ((cmp == 0) && (midVal.time < startingAfter))
          cmp = -1;

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          index = mid; // key found
          break;
        }
      }
      if (index == -1)
      {
        // Nothing for this Show
        return indicatorOnly ? null : Pooler.EMPTY_AIRING_ARRAY;
      }
      else
      {
        if (indicatorOnly)
          return Pooler.EMPTY_AIRING_ARRAY;
        while (index > 0)
        {
          if ((((Airing) indy.data[index - 1]).showID == forMe.id) &&
              (((Airing) indy.data[index - 1]).time >= startingAfter))
            index--;
          else
            break;
        }
      }
      int index2 = index;
      while (index2 < t.num - 1)
      {
        if (((Airing) indy.data[index2 + 1]).showID == forMe.id)
          index2++;
        else
          break;
      }
      Airing[] rv = new Airing[index2 - index + 1];
      System.arraycopy(indy.data, index, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  // Used to create timed recordingings in Seeker2.
  Airing getFakeAiring(long startTime, long endTime, int stationID)
  {
    return getFakeAiring(startTime, endTime, stationID, 0);
  }

  // Used to create timed recordingings in Seeker2.
  Airing getFakeAiring(long startTime, long endTime, int stationID, int showID)
  {
    Airing fakeAir = new Airing(0);
    fakeAir.time = startTime;
    fakeAir.duration = endTime - startTime;
    fakeAir.stationID = stationID;
    fakeAir.showID = showID;
    return fakeAir;
  }

  /**
   * Get the time of the last non-NoShow airing in the database
   *
   * @return -1 when database is not yet ready, or the millisecond time of the
   * last airing
   */
  public long getLatestTvAiringTime() {
    if (loading) return -1;

    // get all channels
    Channel[] channels=getChannels();

    Table t = getTable(AIRING_CODE);
    Index indy = t.getIndex(AIRINGS_BY_CT_CODE);
    long lastAiringTime=-1;

    int lastIndex=0;
    try {
      t.acquireReadLock();
      // for each channel
      for ( int chanIndex=0; chanIndex<channels.length; chanIndex++) {
        int stationID=channels[chanIndex].getStationID();

        if ( stationID > 0 ){

          int index=-1;
          int low = lastIndex; // use lastIndex as a hint of where to start looking
          int high = t.num - 1;
          int mid =-1;
          Airing air=null;

          // binary search for last airing on channel
          while ( low < high )
          {
            mid = ( low + high ) >> 1;
            air = (Airing) indy.data[mid];
            if ( mid == low )
              // cannot go deeper
              break;

            long cmp = air.stationID - stationID;
            if ( cmp == 0 ) {
              // use negative comparison to get last airing on this channel
              cmp = -1 ;
            }

            if ( cmp < 0 )
              low = mid;
            else if ( cmp > 0 )
              high = mid;
            else {
              break;
            }
          }
          // check that we did actually find an airing on the
          // station we want...
          if ( air != null && air.stationID == stationID ) {
            index=mid;
            // last airing for channel found
            // store index for next iteration of binary search loop
            lastIndex = index;

            // skip noshows by moving back
            while ( index >0  && air != null && isNoShow(air.showID) && air.stationID == stationID ) {
              index--;
              air =(Airing) indy.data[index];
            }

            if ( air != null && air.stationID == stationID && ! isNoShow(air.showID) ) {
              // got valid show on this channel
              lastAiringTime=Math.max(lastAiringTime, air.getEndTime());
            }
          }
        } // if non-zero Station ID
      } // for each channel
    } finally {
      t.releaseReadLock();
    }
    return lastAiringTime;
  }

  /**
   * Get the time of the last non-NoShow airing in the database
   *
   * @param lookupIDs An array of station ID's to look up the latest airing for.
   * @return <code>null</code> when database is not yet ready, an empty array if lookupIDs is
   *         <code>null</code> or empty or the millisecond time (or -1 if there are no airings) of
   *         the last airing for each lookupID
   */
  public long[] getLatestTvAiringTime(int lookupIDs[]) {
    if (loading) return null;
    if (lookupIDs == null || lookupIDs.length == 0) return Pooler.EMPTY_LONG_ARRAY;
    long returnValues[] = new long[lookupIDs.length];
    Arrays.fill(returnValues, -1);

    // Get all channels
    Channel[] channels=getChannels();

    Table t = getTable(AIRING_CODE);
    Index indy = t.getIndex(AIRINGS_BY_CT_CODE);

    int lastIndex=0;
    try {
      t.acquireReadLock();
      // for each channel
      for ( int chanIndex = 0; chanIndex < channels.length; chanIndex++)
      {
        int stationID = channels[chanIndex].getStationID();

        if ( stationID > 0 )
        {
          // Only check station IDs that match the channels we are looking for.
          boolean skip = true;
          for (int i = 0; i < lookupIDs.length; i++)
          {
            if (stationID == lookupIDs[i])
            {
              skip = false;
              break;
            }
          }
          if (skip) continue;

          int index=-1;
          int low = lastIndex; // use lastIndex as a hint of where to start looking
          int high = t.num - 1;
          int mid =-1;
          Airing air=null;

          // binary search for last airing on channel
          while ( low < high )
          {
            mid = ( low + high ) >> 1;
            air = (Airing) indy.data[mid];
            if ( mid == low )
              // cannot go deeper
              break;

            long cmp = air.stationID - stationID;
            if ( cmp == 0 ) {
              // use negative comparison to get last airing on this channel
              cmp = -1 ;
            }

            if ( cmp < 0 )
              low = mid;
            else if ( cmp > 0 )
              high = mid;
            else {
              break;
            }
          }
          // check that we did actually find an airing on the
          // station we want...
          if ( air != null && air.stationID == stationID )
          {
            index=mid;
            // last airing for channel found
            // store index for next iteration of binary search loop
            lastIndex = index;

            // skip noshows by moving back
            while ( index >0  && air != null && isNoShow(air.showID) && air.stationID == stationID )
            {
              index--;
              air =(Airing) indy.data[index];
            }

            if ( air != null && air.stationID == stationID && ! isNoShow(air.showID) )
            {
              // We could have saved the index earlier, but if there is more than one entry for the
              // same station ID, it would not get get updated.
              for (int i = 0; i < lookupIDs.length; i++)
              {
                if (stationID == lookupIDs[i])
                {
                  // got valid show on this channel
                  returnValues[i] = Math.max(returnValues[i], air.getEndTime());
                }
              }
            }
          }
        } // if non-zero Station ID
      } // for each channel
    } finally {
      t.releaseReadLock();
    }
    return returnValues;
  }

  Watched addWatched(Airing watchAir, long watchStart, long watchEnd,
      long realStart, long realEnd)
  {
    return addWatched(watchAir, watchStart, watchEnd, realStart, realEnd, 0);
  }
  Watched addWatched(Airing watchAir, long watchStart, long watchEnd,
      long realStart, long realEnd, int titleNum)
  {
    try {
      acquireWriteLock(WATCH_CODE);
      Watched prior = getWatch(watchAir);
      if (prior != null)
      {
        Watched ammend = (Watched) prior.clone();
        if (titleNum > 0)
        {
          ammend.watchStart = 0;
          ammend.watchEnd = watchEnd;
          if (realStart != 0)
            ammend.realStart = Math.min(realStart, prior.realStart);
          ammend.realEnd = Math.max(realEnd, prior.realEnd);
          ammend.titleNum = titleNum;
        }
        else
        {
          ammend.watchStart = Math.min(watchStart, prior.watchStart);
          ammend.watchEnd = Math.max(watchEnd, prior.watchEnd);
          if (realStart != 0)
            ammend.realStart = Math.min(realStart, prior.realStart);
          ammend.realEnd = Math.max(realEnd, prior.realEnd);
        }
        getTable(WATCH_CODE).update(prior, ammend, true);
        if (Sage.DBG) System.out.println("Updated:" + prior);
        return prior;
      }
      else
      {
        god.incWatchCount();
        Watched w = new Watched(getNextWizID());
        w.airingID = watchAir.id;
        w.watchStart = watchStart;
        w.watchEnd = watchEnd;
        w.realStart = realStart;
        w.realEnd = realEnd;
        w.time = watchAir.time;
        w.showID = watchAir.showID;
        w.titleNum = titleNum;
        w.setMediaMask(watchAir.getMediaMask());
        getTable(WATCH_CODE).add(w, true);
        if (Sage.DBG) System.out.println("Added:" + w);
        return w;
      }
    } finally {
      releaseWriteLock(WATCH_CODE);
    }
  }

  public Watched[] getLatestWatchedTV(int maxCount)
  {
    Table t = getTable(WATCH_CODE);
    Index indy = t.getIndex(WATCHED_BY_TIME_CODE);
    try {
      t.acquireReadLock();
      List<Watched> rv = new ArrayList<Watched>(maxCount);
      for (int i = t.num - 1; i >= 0; i--)
      {
        if (indy.data[i].isTV())
        {
          rv.add((Watched) indy.data[i]);
          if (rv.size() >= maxCount)
            break;
        }
      }
      return rv.toArray(new Watched[0]);
    } finally {
      t.releaseReadLock();
    }
  }

  public Watched[] getWatches(long startingAfter)
  {
    Table t = getTable(WATCH_CODE);
    Index indy = t.getIndex(WATCHED_BY_TIME_CODE);
    try {
      t.acquireReadLock();
      if (loading) return new Watched[0];

      int index;
      int low = 0;
      int high = t.num - 1;
      index = -1;
      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Watched midVal = (Watched) indy.data[mid];
        int cmp = sign((midVal.realEnd == 0 ? midVal.realStart : midVal.realEnd) - startingAfter);

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          index = mid; // key found
          break;
        }
      }
      if (index == -1)
      {
        index = low;
      }

      Watched[] rv = new Watched[t.num - index];
      System.arraycopy(indy.data, index, rv, 0, rv.length);
      return rv;
    } finally {
      t.releaseReadLock();
    }
  }

  public Watched getWatch(Airing watchAir)
  {
    if (watchAir == null) return null;
    Table t = getTable(WATCH_CODE);
    Index indy = t.primary;
    try {
      t.acquireReadLock();
      if (loading) return null;

      int index;
      int low = 0;
      int high = t.num - 1;
      index = -1;
      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Watched midVal = (Watched) indy.data[mid];

        int cmp = midVal.showID - watchAir.showID;
        if (cmp == 0)
        {
          long timeDiff = midVal.time - watchAir.time;
          cmp = (timeDiff == 0) ? 0 : ((timeDiff < 0) ? -1 : 1);
        }

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          index = mid; // key found
          break;
        }
      }
      if (index == -1)
      {
        return null;
      }
      if (((Watched) indy.data[index]).airingID == watchAir.id)
        return (Watched) indy.data[index];
      while (index > 0)
      {
        Watched currData = (Watched) indy.data[index - 1];
        if ((currData.showID == watchAir.showID) &&
            (currData.time == watchAir.getStartTime()))
        {
          index--;
          if (currData.airingID == watchAir.id)
            return currData;
        }
        else
          break;
      }
      int index2 = index;
      while (index2 < t.num - 1)
      {
        Watched currData = (Watched) indy.data[index2 + 1];
        if ((currData.showID == watchAir.showID) &&
            (currData.time == watchAir.getStartTime()))
        {
          index2++;
          if (currData.airingID == watchAir.id)
            return currData;
        }
        else
          break;
      }

      return null;
    } finally {
      t.releaseReadLock();
    }
  }

  void removeWatched(Watched removeMe)
  {
    if (removeMe == null) return;
    Table t = getTable(WATCH_CODE);
    t.remove(removeMe, true);
  }

  public Person addPerson(String name, int extID, int dob, int dod, String birthPlace, short[] yearList, String[] awardList, byte[][] headshotUrls, int mediaMask)
  {
    return addPerson(name, extID, dob, dod, birthPlace, yearList, awardList, (short)-1, headshotUrls, mediaMask);
  }
  public Person addPerson(String name, int extID, int dob, int dod, String birthPlace, short[] yearList, String[] awardList, short headshotImageID)
  {
    return addPerson(name, extID, dob, dod, birthPlace, yearList, awardList, headshotImageID, DBObject.MEDIA_MASK_TV);
  }
  public Person addPerson(String name, int extID, int dob, int dod, String birthPlace, short[] yearList, String[] awardList, short headshotImageID, int mediaMask)
  {
    return addPerson(name, extID, dob, dod, birthPlace, yearList, awardList, headshotImageID, Pooler.EMPTY_2D_BYTE_ARRAY, mediaMask);
  }
  public Person addPerson(String name, int extID, int dob, int dod, String birthPlace, short[] yearList, String[] awardList, short headshotImageID, byte[][] headshotUrls, int mediaMask)
  {
    name = name.trim();
    Person oldPerson = getPersonForNameAndExtID(name, extID, false);
    Person person;
    try {
      acquireWriteLock(PEOPLE_CODE);
      person = (oldPerson != null) ? (Person) oldPerson.clone() : new Person(getNextWizID());
      person.name = new String(name);
      person.ignoreCaseHash = name.toLowerCase().hashCode();
      person.extID = extID;
      person.dateOfBirth = dob;
      person.dateOfDeath = dod;
      person.birthPlace = (birthPlace == null || birthPlace.length() == 0) ? null : getBonusForName(birthPlace, DBObject.MEDIA_MASK_TV);
      person.yearList = (yearList == null || yearList.length == 0) ? Pooler.EMPTY_SHORT_ARRAY : ((short[]) yearList.clone());
      person.awardNames = (awardList == null || awardList.length == 0) ? Pooler.EMPTY_STRINGER_ARRAY : new Stringer[awardList.length];
      for (int i = 0; i < person.awardNames.length; i++)
        person.awardNames[i] = getBonusForName(awardList[i], DBObject.MEDIA_MASK_TV);
      person.headshotImageId = headshotImageID;
      person.headshotUrls = (headshotUrls == null || headshotUrls.length != 2) ? Pooler.EMPTY_2D_BYTE_ARRAY : headshotUrls;

      person.setMediaMask(mediaMask);

      if (oldPerson != null)
      {
        if (!person.isPersonIdentical(oldPerson))
        {
          getTable(PEOPLE_CODE).update(oldPerson, person, true);
          //if (Sage.DBG) System.out.println("Updated " + person.getFullString());
        }
        person = oldPerson;
      }
      else
      {
        getTable(PEOPLE_CODE).add(person, true);
        //if (Sage.DBG) System.out.println("Added " + person.getFullString());
      }
    } finally {
      releaseWriteLock(PEOPLE_CODE);
    }
    return person;

  }
  public Person getPersonForID(int id)
  {
    if (id == 0) return null;
    if (id < 0)
      return (Person) getIndex(PEOPLE_CODE).data[(-id) - 1];
    return (Person) getIndex(PEOPLE_CODE).getSingle(id);
  }
  public Person getPersonForName(String name)
  {
    return getPersonForName(name, 0);
  }
  // This is for looking up TMS people in the DB
  public Person getPersonForNameAndExtID(String name, int extID, boolean addIfNoExist)
  {
    if ((name == null) || (name.length() == 0)) return null;
    name = name.trim();
    Table t = getTable(PEOPLE_CODE);
    Index indy = t.getIndex(PEOPLE_BY_NAME_CODE);
    try {
      if (addIfNoExist)
        t.acquireWriteLock();
      else
        t.acquireReadLock();
      int low = 0;
      int high = t.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Person midVal = (Person) indy.data[mid];
        int cmp = midVal.name.compareTo(name);
        // If it has a 0 extID in the DB, then return it as a match so we use it
        // to update the object to the new one
        if (cmp == 0 && midVal.extID != 0)
        {
          cmp = midVal.extID - extID;
        }
        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
          return midVal; // key found
      }
      if (addIfNoExist)
      {
        // See if we already have this alias in the DB
        if (extID > 0)
        {
          Person existingAlias = getPersonForNameAndExtID(name, extID * -1, false);
          if (existingAlias != null)
            return existingAlias;
        }
        Person rv = new Person(getNextWizID());
        rv.name = new String(name);
        rv.ignoreCaseHash = name.toLowerCase().hashCode();
        rv.setMediaMask(DBObject.MEDIA_MASK_TV);
        // We use a negative external ID for a Person to indicate an alias to the real one stored in the DB
        rv.extID = -1 * extID;
        rv.orgPerson = getPersonForExtID(extID);
        if (rv.orgPerson == null)
        {
          rv.extID = extID;
          t.add(rv, !loading);
          if (Sage.DBG) System.out.println("WARNING: Did not find original person alias...putting in alias as original value:" + rv);
        }
        else
        {
          t.add(rv, !loading);
          if (Sage.DBG) System.out.println("Added Person alias of \"" + name + "\" for \"" + rv.orgPerson + "\"");
        }
        return rv;
      }
      else
        return null;
    } finally {
      if (addIfNoExist)
        t.releaseWriteLock();
      else
        t.releaseReadLock();
    }
  }
  public Person getPersonForExtID(int extID)
  {
    if (extID == 0) return null;
    Table t = getTable(PEOPLE_CODE);
    Index indy = t.getIndex(PEOPLE_BY_EXTID_CODE);
    try {
      t.acquireReadLock();
      int low = 0;
      int high = t.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Person midVal = (Person) indy.data[mid];
        int cmp = midVal.extID - extID;
        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
          return midVal; // key found
      }
      return null;
    } finally {
      t.releaseReadLock();
    }
  }
  private Person getPersonForName(String name, int createMediaMask)
  {
    return getPersonForName(name, createMediaMask, true);
  }
  public Person getPersonForName(String name, int createMediaMask, boolean addIfNotExist)
  {
    if ((name == null) || (name.length() == 0)) return null;
    name = name.trim();
    Table t = getTable(PEOPLE_CODE);
    Index indy = t.getIndex(PEOPLE_BY_NAME_CODE);
    try {
      if (addIfNotExist)
        t.acquireWriteLock();
      else
        t.acquireReadLock();
      int low = 0;
      int high = t.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Person midVal = (Person) indy.data[mid];
        int cmp = midVal.name.compareTo(name);

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
          return midVal; // key found
      }

      if (addIfNotExist)
      {
        Person rv = new Person(getNextWizID());
        rv.name = new String(name);
        rv.ignoreCaseHash = name.toLowerCase().hashCode();
        rv.setMediaMask(createMediaMask);
        rv.extID = 0;
        t.add(rv, !loading);
        return rv;
      }
      else
        return null;
    } finally {
      if (addIfNotExist)
        t.releaseWriteLock();
      else
        t.releaseReadLock();
    }
  }
  // This will not create one, it's so we can still do == comparison on name searching but also handles
  // the case where there are multiple people w/ the same name
  private Person[] getPersonsForName(String name)
  {
    if ((name == null) || (name.length() == 0)) return null;
    name = name.trim();
    Table t = getTable(PEOPLE_CODE);
    Index indy = t.getIndex(PEOPLE_BY_NAME_CODE);
    try {
      t.acquireReadLock();
      int low = 0;
      int high = t.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Person midVal = (Person) indy.data[mid];
        int cmp = midVal.name.compareTo(name);

        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          high = low = mid;
          while (low > 0)
          {
            if (((Person) indy.data[low - 1]).name.compareTo(name) == 0)
              low--;
            else
              break;
          }
          while (high < indy.table.num - 1)
          {
            if (((Person) indy.data[high + 1]).name.compareTo(name) == 0)
              high++;
            else
              break;
          }
          Person[] rv = new Person[high - low + 1];
          System.arraycopy(indy.data, low, rv, 0, rv.length);
          return rv;
        }
      }
    } finally {
      t.releaseReadLock();
    }
    return Pooler.EMPTY_PERSON_ARRAY;
  }

  public Album getCachedAlbumForMediaFile(MediaFile mf)
  {
    return getCachedAlbumForMediaFile(mf, true);
  }
  private Album getCachedAlbumForMediaFile(MediaFile mf, boolean recheckCache)
  {
    if (mf == null) return null;
    if (!mf.isMusic()) return null;
    Show s = mf.getShow();
    if (s == null) return null;
    Person artist = s.getPersonObjInRole(Show.ALBUM_ARTIST_ROLE);
    if (artist == null)
    {
      artist = s.getPersonObjInRole(Show.ARTIST_ROLE);
      if (artist == null)
        artist = s.getPersonObjInRole(Show.ALL_ROLES);
    }
    return getCachedAlbum(s.title, artist, recheckCache);
  }
  public Album getCachedAlbum(Stringer title, Person artist)
  {
    return getCachedAlbum(title, artist, true);
  }
  public Album getCachedAlbum(Stringer title, Person artist, boolean recheckCache)
  {
    if (title == null || artist == null) return null;
    if (recheckCache)
      checkAlbumCache();
    else if (titleToArtistToAlbumMap == null)
      return null;
    Object maybeMap = titleToArtistToAlbumMap.get(title);
    if (maybeMap == null)
      return null;
    // Check for it based on title only first, if that matches an Album, take it!
    if (maybeMap instanceof Album)
      return (Album) maybeMap;
    @SuppressWarnings("unchecked")
    Map<Person, Album> theMap = (Map<Person, Album>) maybeMap;
    // Check for a matching artist and take that, otherwise get the various album.
    Album theAlbum = theMap.get(artist);
    if (theAlbum != null)
      return theAlbum;
    theAlbum = theMap.get(getPersonForName(Sage.rez("Various_Artists")));
    return theAlbum;
  }

  private Map<Stringer, Object> titleToArtistToAlbumMap;
  private Album[] albumCache;
  private long albumCacheTime;
  private Object albumCacheLock = new Object();
  public Album[] getAlbums()
  {
    checkAlbumCache();
    return albumCache.clone();
  }
  // Gets an album out of the cache if it's the exact same as the passed in arguments. Used for re-using albums
  // when generating a new cache. It'll create the album if it's not in the cache.
  private Album getMatchedAlbumCheckCache(Stringer title, Person artist, Stringer category, Stringer year)
  {
    Album oldAlbum = getCachedAlbum(title, artist, false);
    if (oldAlbum != null && artist == oldAlbum.getArtistObj() &&
        category == oldAlbum.getGenreStringer() && year == oldAlbum.getYearStringer())
      return oldAlbum;
    else
      return new Album(title, artist, category, year);
  }

  private void checkAlbumCache()
  {
    if (disableDatabase) return;
    synchronized (albumCacheLock)
    {
      if (albumCache == null || albumCacheTime < lastModifiedMusic)
      {
        if (Sage.DBG) System.out.println("Generating album cache...");
        albumCacheTime = Sage.time();
        Map<Stringer, Object> newTitleToArtistToAlbumMap = new HashMap<Stringer, Object>();
        Set<Album> dirtyAlbums = new HashSet<Album>();
        List<Album> albumList = new ArrayList<Album>();
        // The dirty albums are the ones that don't have a true album artist to go by
        Person variousArtist = getPersonForName(Sage.rez("Various_Artists"));
        Index idx = getIndex(MEDIAFILE_CODE);
        try {
          idx.table.acquireReadLock();
          for (int i = 0; i < idx.table.num; i++)
          {
            MediaFile mf = (MediaFile) idx.data[i];
            if (mf != null && mf.isMusic())
            {
              Show s = mf.getShow();
              if (s != null)
              {
                Object maybeMap = newTitleToArtistToAlbumMap.get(s.title);
                boolean isCurrArtistPure = true;
                Person currArtist = s.getPersonObjInRole(Show.ALBUM_ARTIST_ROLE);
                if (currArtist == null)
                {
                  isCurrArtistPure = false;
                  currArtist = s.getPersonObjInRole(Show.ARTIST_ROLE);
                  if (currArtist == null)
                    currArtist = s.getPersonObjInRole(Show.ALL_ROLES);
                }
                if (maybeMap == null)
                {
                  // There's no files we've seen yet with this title, so create a new
                  // album for it
                  Album newAlbum = getMatchedAlbumCheckCache(s.title, currArtist, s.categories.length == 0 ? null : s.categories[0], s.year);
                  newTitleToArtistToAlbumMap.put(s.title, newAlbum);
                  if (!isCurrArtistPure)
                    dirtyAlbums.add(newAlbum);
                  albumList.add(newAlbum);
                }
                else if (maybeMap instanceof Album)
                {
                  // Check to see if we're the same album as this one we've already seen
                  Album testAlbum = (Album) maybeMap;
                  if (testAlbum.getArtistObj() == currArtist)
                  {
                    // The artists match, so we're the same album! If we're pure, then
                    // we clear the impure state if that was set
                    if (isCurrArtistPure)
                      dirtyAlbums.remove(testAlbum);
                  }
                  else if (isCurrArtistPure || !dirtyAlbums.contains(testAlbum))
                  {
                    // Different albums with different album artists, we'll need to change ourself to a map.
                    // We also create a new album for the new album artist we've found'
                    Map<Person, Album> newMap = new HashMap<Person, Album>();
                    newMap.put(testAlbum.getArtistObj(), testAlbum);
                    Album newAlbum = getMatchedAlbumCheckCache(s.title, currArtist, s.categories.length == 0 ? null : s.categories[0], s.year);
                    albumList.add(newAlbum);
                    newMap.put(currArtist, newAlbum);
                    newTitleToArtistToAlbumMap.put(s.title, newMap);
                    if (!isCurrArtistPure)
                    {
                      // The new album is dirty, so mark is as such
                      dirtyAlbums.add(newAlbum);
                    }
                  }
                  else
                  {
                    // We're dealing with different artists on two tracks that don't have album
                    // artist info, this is the 'various' artist case
                    if (testAlbum.getArtistObj() != variousArtist)
                    {
                      albumList.remove(testAlbum);
                      testAlbum = getMatchedAlbumCheckCache(testAlbum.getTitleStringer(), variousArtist,
                          testAlbum.getGenreStringer(), testAlbum.getYearStringer());
                      albumList.add(testAlbum);
                      newTitleToArtistToAlbumMap.put(testAlbum.getTitleStringer(), testAlbum);
                    }
                    dirtyAlbums.add(testAlbum);
                  }
                }
                else
                {
                  @SuppressWarnings("unchecked")
                  Map<Person, Album> definitelyMap = (Map<Person, Album>) maybeMap;
                  Album testAlbum = definitelyMap.get(currArtist);
                  if (testAlbum != null)
                  {
                    // Found an album with a matching artist, sign us up!
                    if (isCurrArtistPure)
                      dirtyAlbums.remove(testAlbum);
                  }
                  else if (isCurrArtistPure)
                  {
                    // Create a new album for us since we know our album artist
                    Album newAlbum = getMatchedAlbumCheckCache(s.title, currArtist, s.categories.length == 0 ? null : s.categories[0], s.year);
                    definitelyMap.put(currArtist, newAlbum);
                    albumList.add(newAlbum);
                  }
                  else
                  {
                    // If the various artists exists, then use it. Otherwise we'll have to look through
                    // the other artists to see if we can find another impure one to become various with.
                    testAlbum = definitelyMap.get(variousArtist);
                    if (testAlbum == null)
                    {
                      boolean albumResolved = false;
                      for (Album album : definitelyMap.values())
                      {
                        testAlbum = album;
                        if (dirtyAlbums.contains(testAlbum))
                        {
                          albumResolved = true;
                          // We changed the artist to Various for this album, so we need to also add it
                          // to the various mapping and remove it from it's current one
                          definitelyMap.remove(testAlbum.getArtistObj());
                          dirtyAlbums.remove(testAlbum);
                          albumList.remove(testAlbum);
                          testAlbum = getMatchedAlbumCheckCache(testAlbum.getTitleStringer(),
                              variousArtist, testAlbum.getGenreStringer(), testAlbum.getYearStringer());
                          definitelyMap.put(variousArtist, testAlbum);
                          dirtyAlbums.add(testAlbum);
                          albumList.add(testAlbum);
                          break;
                        }
                      }
                      if (!albumResolved)
                      {
                        testAlbum = getMatchedAlbumCheckCache(s.title, currArtist, s.categories.length == 0 ? null : s.categories[0], s.year);
                        dirtyAlbums.add(testAlbum);
                        albumList.add(testAlbum);
                        definitelyMap.put(currArtist, testAlbum);
                      }
                    }
                  }
                }
              }
            }
          }
        } finally {
          idx.table.releaseReadLock();
        }
        Album[] newAlbumCache = albumList.toArray(new Album[0]);
        Arrays.sort(newAlbumCache, new Comparator<Album>()
        {
          public int compare(Album o1, Album o2)
          {
            Stringer s1 = o1.getTitleStringer();
            Stringer s2 = o2.getTitleStringer();
            if (s1 == s2)
              return 0;
            if (s1 == null)
              return -1;
            if (s2 == null)
              return 1;
            return s1.compareTo(s2);
          }
        });
        albumCache = newAlbumCache;
        titleToArtistToAlbumMap = newTitleToArtistToAlbumMap;
        if (Sage.DBG) System.out.println("Done generating album cache.");
      }
    }
  }

  public String[] getAllArtists()
  {
    Set<String> okArtists = new HashSet<String>();
    Index idx = null;
    idx = getIndex(SHOW_CODE);
    try {
      idx.table.acquireReadLock();
      for (int i = 0; i < idx.table.num; i++)
      {
        Show s = (Show) idx.data[i];
        if (s.isMusic())
        {
          for (int j = 0; j < s.people.length; j++)
            okArtists.add(s.people[j].name);
        }
      }
    } finally {
      idx.table.releaseReadLock();
    }
    String[] rv = okArtists.toArray(Pooler.EMPTY_STRING_ARRAY);
    Arrays.sort(rv);
    return rv;
  }

  public String[] getAllGenres()
  {
    Set<String> okGenres = new HashSet<String>();
    Index idx = null;
    idx = getIndex(SHOW_CODE);
    try {
      idx.table.acquireReadLock();
      for (int i = 0; i < idx.table.num; i++)
      {
        Show s = (Show) idx.data[i];
        if (s.isMusic() && s.categories.length > 0)
          okGenres.add(s.categories[0].name);
      }
    } finally {
      idx.table.releaseReadLock();
    }
    String[] rv = okGenres.toArray(Pooler.EMPTY_STRING_ARRAY);
    Arrays.sort(rv);
    return rv;
  }

  public String[] getAllTitles(int mediaMask)
  {
    Index idx = getIndex(TITLE_CODE);
    try {
      idx.table.acquireReadLock();
      ArrayList<String> rv = new ArrayList<String>();
      for (int i = 0; i < idx.data.length; i++)
        if (idx.data[i] != null && (idx.data[i].getMediaMask() & mediaMask) != 0)
          rv.add(((Stringer) idx.data[i]).name);
      return rv.toArray(Pooler.EMPTY_STRING_ARRAY);
    } finally {
      idx.table.releaseReadLock();
    }
  }

  public String[] getAllPeople(int mediaMask)
  {
    Index idx = getIndex(PEOPLE_CODE);
    try {
      idx.table.acquireReadLock();
      ArrayList<String> rv = new ArrayList<String>();
      for (int i = 0; i < idx.data.length; i++)
        if (idx.data[i] != null && (idx.data[i].getMediaMask() & mediaMask) != 0)
          rv.add(((Person) idx.data[i]).name);
      return rv.toArray(Pooler.EMPTY_STRING_ARRAY);
    } finally {
      idx.table.releaseReadLock();
    }
  }

  public String[] getAllCategories(int mediaMask)
  {
    Set<String> rv = new HashSet<String>();
    Index idx = getIndex(CATEGORY_CODE);
    try {
      idx.table.acquireReadLock();
      for (int i = 0; i < idx.data.length; i++)
        if (idx.data[i] != null && (idx.data[i].getMediaMask() & mediaMask) != 0)
          rv.add(((Stringer) idx.data[i]).name);
    } finally {
      idx.table.releaseReadLock();
    }
    idx = getIndex(SUBCATEGORY_CODE);
    try {
      idx.table.acquireReadLock();
      for (int i = 0; i < idx.data.length; i++)
        if (idx.data[i] != null && (idx.data[i].getMediaMask() & mediaMask) != 0)
          rv.add(((Stringer) idx.data[i]).name);
    } finally {
      idx.table.releaseReadLock();
    }
    return rv.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public Vector<Airing> searchWithinFields(String str, boolean caseSensitive, boolean title, boolean episode,
      boolean description, boolean person, boolean category, boolean rated, boolean extendedRatings, boolean year,
      boolean misc, int mediaMask, boolean wholeWord)
  {
    if (str == null || str.length() == 0) return new Vector<Airing>();
    if (!caseSensitive)
      str = str.toLowerCase();
    Vector<Airing> rv = new Vector<Airing>(500);

    // show searching!
    int flag = 0;
    if(wholeWord) flag |= WHOLE_WORD_SEARCH;
    if(title) flag |= TITLE_SEARCH;
    if(rated) flag |= RATED_SEARCH;
    if(year) flag |= YEAR_SEARCH;
    if(category) flag |= CATEGORY_SEARCH;
    if(person) flag |= PERSON_SEARCH;
    if(extendedRatings) flag |= EXTENDED_RATINGS_SEARCH;
    if(misc) flag |= BONUS_SEARCH;
    if(episode) flag |= EPISODE_SEARCH;
    if(description) flag |= DESCRIPTION_SEARCH;
    long start = Sage.time();
    Show[] shows = searchShowsByKeyword(str, flag);
    for(Show show : shows) {
      if (show == null || (show.getMediaMask() & mediaMask) == 0) continue;
      Airing[] airings = getAirings(show,0);
      for(Airing currAir : airings) {
        if(currAir.hasMediaMaskAny(mediaMask)) {
              rv.add(currAir);
        }
      }
    }
    start = Sage.time() - start;
    if(Sage.DBG) System.out.println("searchWithinFields : lucene airs[" + rv.size() + "] in " + start + " ms : flags[" + flag + "]");

    if (misc)
    {
      byte matchMiscB = Airing.getMiscBMaskForSearch(str, caseSensitive, false);
      byte[] valsMiscB = Airing.getPremiereBValuesForSearch(str, caseSensitive, false);
      if (matchMiscB != 0 || valsMiscB.length > 0)
      {
        Index aidx = getIndex(AIRING_CODE);
        try {
          aidx.table.acquireReadLock();
          for (int i = 0; i < aidx.table.num; i++)
          {
            Airing currAir = (Airing) aidx.data[i];
            if (currAir.hasMediaMaskAny(mediaMask)) {
              if ((currAir.miscB & matchMiscB) != 0) {
                    rv.add(currAir);
              } else {
                for (int j = 0; j < valsMiscB.length; j++)
                {
                  if ((currAir.miscB & Airing.PREMIERES_BITMASK) == valsMiscB[j]) {
                        rv.add(currAir);
                  }
                }
              }
            }
          }
        } finally {
          aidx.table.releaseReadLock();
        }
      }
      if (Sage.getBoolean("wizard/search_media_formats", false))
      {
        Index mfIdx = getIndex(MEDIAFILE_CODE);
        try {
          mfIdx.table.acquireReadLock();
          for (int i = 0; i < mfIdx.table.num; i++)
          {
            MediaFile currMF = (MediaFile) mfIdx.data[i];
            if ((currMF.getMediaMask() & mediaMask) == 0)
              continue;
            ContainerFormat cf = currMF.getFileFormat();
            if (cf != null)
            {
              String formatInfo = cf.getPrettyDesc();
              if ((caseSensitive && sage.StringMatchUtils.wordMatches(
                  formatInfo,str)) ||
                  (!caseSensitive && sage.StringMatchUtils.wordMatchesLowerCase(
                      formatInfo,str)))
                rv.add(currMF.getContentAiring());
            }
          }
        } finally {
          mfIdx.table.releaseReadLock();
        }
      }
    }
    return rv;
  }

  public Vector<Airing> searchForMatchingFields(String str, boolean caseSensitive, boolean title, boolean episode,
      boolean description, boolean person, boolean category, boolean rated, boolean extendedRatings, boolean year,
      boolean misc, int mediaMask)
  {
    if (str == null || str.length() == 0) return new Vector<Airing>();
    if (!caseSensitive)
      str = str.toLowerCase();
    Index idx = null;
    Set<Stringer> okTitleStringers = null;
    Set<Person> okPersonStringers = null;
    Set<Stringer> okCategoryStringers = null;
    Set<Stringer> okRatedStringers = null;
    Set<Stringer> okERStringers = null;
    Set<Stringer> okYearStringers = null;
    Set<Stringer> okMiscStringers = null;
    if (title)
    {
      okTitleStringers = new HashSet<Stringer>();
      idx = getIndex(TITLE_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if ((caseSensitive && ((Stringer) idx.data[i]).name.equals(str)) ||
              (!caseSensitive && ((Stringer) idx.data[i]).name.equalsIgnoreCase(str)))
            okTitleStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (person)
    {
      okPersonStringers = new HashSet<Person>();
      idx = getIndex(PEOPLE_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if ((caseSensitive && ((Person) idx.data[i]).name.equals(str)) ||
              (!caseSensitive && ((Person) idx.data[i]).name.equalsIgnoreCase(str)))
            okPersonStringers.add((Person) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (category)
    {
      okCategoryStringers = new HashSet<Stringer>();
      idx = getIndex(CATEGORY_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if ((caseSensitive && ((Stringer) idx.data[i]).name.equals(str)) ||
              (!caseSensitive && ((Stringer) idx.data[i]).name.equalsIgnoreCase(str)))
            okCategoryStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
      idx = getIndex(SUBCATEGORY_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if ((caseSensitive && ((Stringer) idx.data[i]).name.equals(str)) ||
              (!caseSensitive && ((Stringer) idx.data[i]).name.equalsIgnoreCase(str)))
            okCategoryStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (rated)
    {
      okRatedStringers = new HashSet<Stringer>();
      idx = getIndex(RATED_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if ((caseSensitive && ((Stringer) idx.data[i]).name.equals(str)) ||
              (!caseSensitive && ((Stringer) idx.data[i]).name.equalsIgnoreCase(str)))
            okRatedStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (extendedRatings)
    {
      okERStringers = new HashSet<Stringer>();
      idx = getIndex(ER_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if ((caseSensitive && ((Stringer) idx.data[i]).name.equals(str)) ||
              (!caseSensitive && ((Stringer) idx.data[i]).name.equalsIgnoreCase(str)))
            okERStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (year)
    {
      okYearStringers = new HashSet<Stringer>();
      idx = getIndex(YEAR_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if ((caseSensitive && ((Stringer) idx.data[i]).name.equals(str)) ||
              (!caseSensitive && ((Stringer) idx.data[i]).name.equalsIgnoreCase(str)))
            okYearStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (misc)
    {
      okMiscStringers = new HashSet<Stringer>();
      idx = getIndex(BONUS_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if ((caseSensitive && ((Stringer) idx.data[i]).name.equals(str)) ||
              (!caseSensitive && ((Stringer) idx.data[i]).name.equalsIgnoreCase(str)))
            okMiscStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    Index sidx = getIndex(SHOW_CODE);
    Vector<Airing> rv = new Vector<Airing>(500);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    try {
      sidx.table.acquireReadLock();
      show_loop:
        for (int i = 0; i < sidx.table.num; i++)
        {
          Show currShow = (Show) sidx.data[i];
          if ((currShow.getMediaMask() & mediaMask) == 0)
            continue;
          if ((title && okTitleStringers.contains(currShow.title)) ||
              (rated && okRatedStringers.contains(currShow.rated)) ||
              (year && okYearStringers.contains(currShow.year)))

          {
            addAllAiringsForShow(rv, currShow, mediaMask);
            if (rv.size() >= MAX_SEARCH_RESULTS)
              break;
            continue show_loop;
          }
          if (category)
          {
            for (int j = 0; j < currShow.categories.length; j++)
              if (okCategoryStringers.contains(currShow.categories[j]))
              {
                addAllAiringsForShow(rv, currShow, mediaMask);
                if (rv.size() >= MAX_SEARCH_RESULTS)
                  break;
                continue show_loop;
              }
          }
          if (person)
          {
            for (int j = 0; j < currShow.people.length; j++)
              if (okPersonStringers.contains(currShow.people[j]))
              {
                addAllAiringsForShow(rv, currShow, mediaMask);
                if (rv.size() >= MAX_SEARCH_RESULTS)
                  break;
                continue show_loop;
              }
          }
          if (extendedRatings)
          {
            for (int j = 0; j < currShow.ers.length; j++)
              if (okERStringers.contains(currShow.ers[j]))
              {
                addAllAiringsForShow(rv, currShow, mediaMask);
                if (rv.size() >= MAX_SEARCH_RESULTS)
                  break;
                continue show_loop;
              }
          }
          if (misc)
          {
            for (int j = 0; j < currShow.bonuses.length; j++)
              if (okMiscStringers.contains(currShow.bonuses[j]))
              {
                addAllAiringsForShow(rv, currShow, mediaMask);
                if (rv.size() >= MAX_SEARCH_RESULTS)
                  break;
                continue show_loop;
              }
          }
          if (episode)
          {
            if ((caseSensitive && currShow.getEpisodeName().equals(str)) ||
                (!caseSensitive && currShow.getEpisodeName().equalsIgnoreCase(str)))
            {
              addAllAiringsForShow(rv, currShow, mediaMask);
              if (rv.size() >= MAX_SEARCH_RESULTS)
                break;
              continue show_loop;
            }
          }
          if (description)
          {
            if ((caseSensitive && currShow.getDesc().equals(str)) ||
                (!caseSensitive && currShow.getDesc().equalsIgnoreCase(str)))
            {
              addAllAiringsForShow(rv, currShow, mediaMask);
              if (rv.size() >= MAX_SEARCH_RESULTS)
                break;
              continue show_loop;
            }
          }
        }
    } finally {
      sidx.table.releaseReadLock();
    }
    if (misc)
    {
      byte matchMiscB = Airing.getMiscBMaskForSearch(str, caseSensitive, true);
      byte[] valsMiscB = Airing.getPremiereBValuesForSearch(str, caseSensitive, true);
      if (matchMiscB != 0 || valsMiscB.length > 0)
      {
        Index aidx = getIndex(AIRING_CODE);
        try {
          aidx.table.acquireReadLock();
          for (int i = 0; i < aidx.table.num; i++)
          {
            Airing currAir = (Airing) aidx.data[i];
            if ((currAir.getMediaMask() & mediaMask) == 0)
              continue;
            if ((currAir.miscB & matchMiscB) != 0){
              rv.add(currAir);
              if (rv.size() >= MAX_SEARCH_RESULTS)
                break;
            } else {
              for (int j = 0; j < valsMiscB.length; j++)
              {
                if ((currAir.miscB & Airing.PREMIERES_BITMASK) == valsMiscB[j]) {
                  rv.add(currAir);
                  if (rv.size() >= MAX_SEARCH_RESULTS)
                    break;
                }
              }
            }
          }
        } finally {
          aidx.table.releaseReadLock();
        }
      }
    }
    return rv;
  }

  public Vector<Airing> searchFields(Pattern pat, boolean title, boolean episode,
      boolean description, boolean person, boolean category, boolean rated, boolean extendedRatings, boolean year,
      boolean misc, int mediaMask)
  {
    if (pat == null || pat.pattern().length() == 0) return new Vector<Airing>();
    Index idx = null;
    Set<Stringer> okTitleStringers = null;
    Set<Person> okPersonStringers = null;
    Set<Stringer> okCategoryStringers = null;
    Set<Stringer> okRatedStringers = null;
    Set<Stringer> okERStringers = null;
    Set<Stringer> okYearStringers = null;
    Set<Stringer> okMiscStringers = null;
    if (title)
    {
      okTitleStringers = new HashSet<Stringer>();
      idx = getIndex(TITLE_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if (pat.matcher(((Stringer) idx.data[i]).name).matches())
            okTitleStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (person)
    {
      okPersonStringers = new HashSet<Person>();
      idx = getIndex(PEOPLE_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if (pat.matcher(((Person) idx.data[i]).name).matches())
            okPersonStringers.add((Person) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (category)
    {
      okCategoryStringers = new HashSet<Stringer>();
      idx = getIndex(CATEGORY_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if (pat.matcher(((Stringer) idx.data[i]).name).matches())
            okCategoryStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
      idx = getIndex(SUBCATEGORY_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if (pat.matcher(((Stringer) idx.data[i]).name).matches())
            okCategoryStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (rated)
    {
      okRatedStringers = new HashSet<Stringer>();
      idx = getIndex(RATED_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if (pat.matcher(((Stringer) idx.data[i]).name).matches())
            okRatedStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (extendedRatings)
    {
      okERStringers = new HashSet<Stringer>();
      idx = getIndex(ER_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if (pat.matcher(((Stringer) idx.data[i]).name).matches())
            okERStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (year)
    {
      okYearStringers = new HashSet<Stringer>();
      idx = getIndex(YEAR_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if (pat.matcher(((Stringer) idx.data[i]).name).matches())
            okYearStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    if (misc)
    {
      okMiscStringers = new HashSet<Stringer>();
      idx = getIndex(BONUS_CODE);
      try {
        idx.table.acquireReadLock();
        for (int i = 0; i < idx.table.num; i++)
        {
          if ((idx.data[i].getMediaMask() & mediaMask) == 0)
            continue;
          if (pat.matcher(((Stringer) idx.data[i]).name).matches())
            okMiscStringers.add((Stringer) idx.data[i]);
        }
      } finally {
        idx.table.releaseReadLock();
      }
    }
    Index sidx = getIndex(SHOW_CODE);
    Vector<Airing> rv = new Vector<Airing>(500);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    try {
      sidx.table.acquireReadLock();
      show_loop:
        for (int i = 0; i < sidx.table.num; i++)
        {
          Show currShow = (Show) sidx.data[i];
          if ((currShow.getMediaMask() & mediaMask) == 0)
            continue;

          if ((title && okTitleStringers.contains(currShow.title)) ||
              (rated && okRatedStringers.contains(currShow.rated)) ||
              (year && okYearStringers.contains(currShow.year)))

          {
            addAllAiringsForShow(rv, currShow, mediaMask);
            if (rv.size() >= MAX_SEARCH_RESULTS)
              break;
            continue show_loop;
          }
          if (category)
          {
            for (int j = 0; j < currShow.categories.length; j++)
              if (okCategoryStringers.contains(currShow.categories[j]))
              {
                addAllAiringsForShow(rv, currShow, mediaMask);
                if (rv.size() >= MAX_SEARCH_RESULTS)
                  break;
                continue show_loop;
              }
          }
          if (person)
          {
            for (int j = 0; j < currShow.people.length; j++)
              if (okPersonStringers.contains(currShow.people[j]))
              {
                addAllAiringsForShow(rv, currShow, mediaMask);
                if (rv.size() >= MAX_SEARCH_RESULTS)
                  break;
                continue show_loop;
              }
          }
          if (extendedRatings)
          {
            for (int j = 0; j < currShow.ers.length; j++)
              if (okERStringers.contains(currShow.ers[j]))
              {
                addAllAiringsForShow(rv, currShow, mediaMask);
                if (rv.size() >= MAX_SEARCH_RESULTS)
                  break;
                continue show_loop;
              }
          }
          if (misc)
          {
            for (int j = 0; j < currShow.bonuses.length; j++)
              if (okMiscStringers.contains(currShow.bonuses[j]))
              {
                addAllAiringsForShow(rv, currShow, mediaMask);
                if (rv.size() >= MAX_SEARCH_RESULTS)
                  break;
                continue show_loop;
              }
          }
          if (episode)
          {
            if (pat.matcher(currShow.getEpisodeName()).matches())
            {
              addAllAiringsForShow(rv, currShow, mediaMask);
              if (rv.size() >= MAX_SEARCH_RESULTS)
                break;
              continue show_loop;
            }
          }
          if (description)
          {
            if (pat.matcher(currShow.getDesc()).matches())
            {
              addAllAiringsForShow(rv, currShow, mediaMask);
              if (rv.size() >= MAX_SEARCH_RESULTS)
                break;
              continue show_loop;
            }
          }
        }
    } finally {
      sidx.table.releaseReadLock();
    }
    if (misc)
    {
      byte matchMiscB = Airing.getMiscBMaskForSearch(pat);
      byte[] valsMiscB = Airing.getPremiereBValuesForSearch(pat);
      if (matchMiscB != 0 || valsMiscB.length > 0)
      {
        Index aidx = getIndex(AIRING_CODE);
        try {
          aidx.table.acquireReadLock();
          for (int i = 0; i < aidx.table.num; i++)
          {
            Airing currAir = (Airing) aidx.data[i];
            if (currAir.hasMediaMaskAny(mediaMask)) {
              if ((currAir.miscB & matchMiscB) != 0) {
                rv.add(currAir);
                if (rv.size() >= MAX_SEARCH_RESULTS)
                  break;
              } else {
                for (int j = 0; j < valsMiscB.length; j++)
                {
                  if ((currAir.miscB & Airing.PREMIERES_BITMASK) == valsMiscB[j]) {
                    rv.add(currAir);
                    if (rv.size() >= MAX_SEARCH_RESULTS)
                      break;
                  }
                }
              }
            }
          }
        } finally {
          aidx.table.releaseReadLock();
        }
      }
      if (Sage.getBoolean("wizard/search_media_formats", false))
      {
        Index mfIdx = getIndex(MEDIAFILE_CODE);
        try {
          mfIdx.table.acquireReadLock();
          for (int i = 0; i < mfIdx.table.num; i++)
          {
            MediaFile currMF = (MediaFile) mfIdx.data[i];
            if ((currMF.getMediaMask() & mediaMask) == 0)
              continue;
            ContainerFormat cf = currMF.getFileFormat();
            if (cf != null && pat.matcher(cf.getPrettyDesc()).matches()) {
              rv.add(currMF.getContentAiring());
              if (rv.size() >= MAX_SEARCH_RESULTS)
                break;
            }
          }
        } finally {
          mfIdx.table.releaseReadLock();
        }
      }
    }
    return rv;
  }

  public String[] searchForTitles(Pattern pattern, int mediaMask)
  {
    Set<String> okTitles = new HashSet<String>();
    Index idx = getIndex(TITLE_CODE);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    try {
      idx.table.acquireReadLock();
      for (int i = 0; i < idx.table.num; i++)
      {
        if ((idx.data[i].getMediaMask() & mediaMask) == 0)
          continue;
        if (pattern.matcher(((Stringer) idx.data[i]).name).matches())
        {
          okTitles.add(((Stringer) idx.data[i]).name);
          if (okTitles.size() >= MAX_SEARCH_RESULTS)
            break;
        }
      }
    } finally {
      idx.table.releaseReadLock();
    }
    String[] rv = okTitles.toArray(Pooler.EMPTY_STRING_ARRAY);
    Arrays.sort(rv);
    return rv;
  }

  public String[] searchForTitles(String str, int mediaMask)
  {
    str = str.toLowerCase();
    Set<String> okTitles = new HashSet<String>();
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    Show[] shows = searchShowsByKeyword(str, TITLE_SEARCH);
    for(Show show : shows) {
      if (show == null || (show.getMediaMask() & mediaMask) == 0)
        continue;
      okTitles.add(show.title.name);
      if (okTitles.size() >= MAX_SEARCH_RESULTS)
        break;
    }
    String[] rv = okTitles.toArray(Pooler.EMPTY_STRING_ARRAY);
    Arrays.sort(rv);
    return rv;
  }

  public Airing[] searchByExactTitle(String st, int mediaMask)
  {
    Stringer str = getTitleForName(st);
    ArrayList<Airing> rv = new ArrayList<Airing>();
    Index sidx = getIndex(SHOW_CODE);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    try {
      sidx.table.acquireReadLock();
      for (int i = 0; i < sidx.table.num; i++)
      {
        Show currShow = (Show) sidx.data[i];
        if ((currShow.getMediaMask() & mediaMask) == 0)
          continue;
        if (currShow.title == str)
        {
          addAllAiringsForShow(rv, currShow, mediaMask);
          if (rv.size() >= MAX_SEARCH_RESULTS)
            break;
        }
      }
    } finally {
      sidx.table.releaseReadLock();
    }
    return rv.toArray(Pooler.EMPTY_AIRING_ARRAY);
  }

  public Airing[] searchByExactAlbum(Album al)
  {
    ArrayList<Airing> rv = new ArrayList<Airing>();
    Index sidx = getIndex(MEDIAFILE_CODE);
    Stringer str = al.getTitleStringer();
    if (str == null) return Pooler.EMPTY_AIRING_ARRAY;
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    try {
      sidx.table.acquireReadLock();
      for (int i = 0; i < sidx.table.num; i++)
      {
        MediaFile mf = (MediaFile) sidx.data[i];
        Show s;
        if (mf.isMusic() && ((s = mf.getShow()) != null) &&
            (s.title == str))
        {
          // The title matches, so also do our lookup to match the album artist correctly
          // DO NOT recheck the album cache at this point since we have the MediaFile lock
          // because that can result in a deadlock with anything else going into the album cache.
          Album cachedAl = getCachedAlbumForMediaFile(mf, false);
          if (cachedAl != null && cachedAl.getArtistObj() == al.getArtistObj())
          {
            rv.add(mf.getContentAiring());
            if (rv.size() >= MAX_SEARCH_RESULTS)
              break;
          }
        }
      }
    } finally {
      sidx.table.releaseReadLock();
    }
    return rv.toArray(Pooler.EMPTY_AIRING_ARRAY);
  }

  public Person[] searchForPeople(Pattern pat, int mediaMask)
  {
    ArrayList<Person> okPeople = new ArrayList<Person>();
    Index idx = getIndex(PEOPLE_CODE);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    try {
      idx.table.acquireReadLock();
      for (int i = 0; i < idx.table.num; i++)
      {
        Person strgr = (Person) idx.data[i];
        if ((strgr.getMediaMask() & mediaMask) == 0)
          continue;
        if (pat.matcher(strgr.name).matches())
        {
          okPeople.add(strgr);
          if (okPeople.size() >= MAX_SEARCH_RESULTS)
            break;
        }
      }
    } finally {
      idx.table.releaseReadLock();
    }
    Person[] rv = okPeople.toArray(Pooler.EMPTY_PERSON_ARRAY);
    Arrays.sort(rv, Person.NAME_COMPARATOR);
    return rv;
  }

  public Person[] searchForPeople(String str, int mediaMask)
  {
    ArrayList<Person> okPeople = new ArrayList<Person>();
    Person[] peeps = searchPeople(str);
    for(Person peep : peeps) {
      if (peep == null || (peep.getMediaMask() & mediaMask) == 0)
        continue;
      okPeople.add(peep);
    }
    Person[] rv = okPeople.toArray(Pooler.EMPTY_PERSON_ARRAY);
    Arrays.sort(rv, Person.NAME_COMPARATOR);
    return rv;
  }

  public Airing[] searchByExactPerson(Person p)
  {
    return searchByExactPerson(p, DBObject.MEDIA_MASK_ALL);
  }

  public Airing[] searchByExactPerson(Person p, int mediaMask)
  {
    Person[] peeps = getAliasesForPerson(p);
    if (peeps.length == 0)
      return Pooler.EMPTY_AIRING_ARRAY;
    ArrayList<Airing> rv = new ArrayList<Airing>();
    for (int i = 0; i < peeps.length; i++)
      searchByExactPerson(peeps[i], rv, mediaMask);
    return rv.toArray(Pooler.EMPTY_AIRING_ARRAY);
  }

  public Person[] getAliasesForPerson(Person p)
  {
    if (p == null)
      return Pooler.EMPTY_PERSON_ARRAY;
    if (p.extID == 0)
      return new Person[] { p };
    Person originalPerson = (p.extID < 0) ? p.getOriginalAlias() : p;
    if (originalPerson == null)
      originalPerson = p;

    int targetExtID = -1 * originalPerson.extID;
    // Now we get all the aliases for this person
    Table t = getTable(PEOPLE_CODE);
    Index indy = t.getIndex(PEOPLE_BY_EXTID_CODE);
    try {
      t.acquireReadLock();
      int low = 0;
      int high = t.num - 1;

      while (low <= high)
      {
        int mid = (low + high) >> 1;
        Person midVal = (Person) indy.data[mid];
        int cmp = midVal.extID - targetExtID;
        if (cmp < 0)
          low = mid + 1;
        else if (cmp > 0)
          high = mid - 1;
        else
        {
          // Now we need to walk forward and backwards to find the bounds
          // of the aliases in this index
          low = high = mid;
          while (low > 0)
          {
            Person lowbie = (Person) indy.data[low - 1];
            if (lowbie != null && lowbie.extID == targetExtID)
              low--;
            else
              break;
          }
          while (high < t.num - 1)
          {
            Person higher = (Person) indy.data[high + 1];
            if (higher != null && higher.extID == targetExtID)
              high++;
            else
              break;
          }
          Person[] rv = new Person[2 + high - low];
          rv[0] = originalPerson;
          System.arraycopy(indy.data, low, rv, 1, rv.length - 1);
          return rv;
        }
      }
      return new Person[] { originalPerson };
    } finally {
      t.releaseReadLock();
    }
  }

  public Airing[] searchByExactArtist(Person p)
  {
    return searchByExactPerson(p, DBObject.MEDIA_MASK_MUSIC);
  }

  private void searchByExactPerson(Person st, ArrayList<Airing> rv, int mediaMask)
  {
    Index sidx = getIndex(SHOW_CODE);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    try {
      sidx.table.acquireReadLock();
      for (int i = 0; i < sidx.table.num; i++)
      {
        Show currShow = (Show) sidx.data[i];
        if ((currShow.getMediaMask() & mediaMask) == 0)
          continue;
        for (int j = 0; j < currShow.people.length; j++)
          if (currShow.people[j] == st)
          {
            addAllAiringsForShow(rv, currShow, mediaMask);
            if (rv.size() >= MAX_SEARCH_RESULTS)
              return;
            break;
          }
      }
    } finally {
      sidx.table.releaseReadLock();
    }
  }

  public Airing[] searchByExactGenre(String st)
  {
    ArrayList<Airing> rv = new ArrayList<Airing>();
    Stringer str = getCategoryForName(st);
    Index sidx = getIndex(SHOW_CODE);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    try {
      sidx.table.acquireReadLock();
      for (int i = 0; i < sidx.table.num; i++)
      {
        Show currShow = (Show) sidx.data[i];
        if (currShow.isMusic() && currShow.categories.length > 0 && currShow.categories[0] == str)
        {
          rv.addAll(Arrays.asList(getAirings(currShow, 0)));
          if (rv.size() >= MAX_SEARCH_RESULTS)
            break;
        }
      }
    } finally {
      sidx.table.releaseReadLock();
    }
    return rv.toArray(Pooler.EMPTY_AIRING_ARRAY);
  }

  // Searches descriptions and episodeNames
  public Airing[] searchByText(String str, int mediaMask)
  {
    str = str.toLowerCase();
    Show[] shows = searchShowsByKeyword(str, DESCRIPTION_SEARCH|EPISODE_SEARCH);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    ArrayList<Airing> rv = new ArrayList<Airing>();
    for(Show show : shows) {
      if (show == null || (show.getMediaMask() & mediaMask) == 0)
        continue;
      addAllAiringsForShow(rv, show, mediaMask);
      if (rv.size() >= MAX_SEARCH_RESULTS)
        break;
    }
    return rv.toArray(Pooler.EMPTY_AIRING_ARRAY);
  }

  /**
   * Add all the airings matching the mediaMask for the requested show to the passed list
   *
   * @param list list of airings to append to
   * @param show the show to get the airings for
   * @param mediaMask the mediaMask the caller is interested in
   */
  private void addAllAiringsForShow(List<Airing> list, Show show, int mediaMask) {
    // add all airings for show
    Airing[] airs=getAirings(show, 0);
    for (Airing airing : airs) {
      if ( airing.hasMediaMaskAny(mediaMask)) {
        list.add(airing);
      }
    }
  }

  /**
   * Search the database for all Channel objects matching the searchSstring in their name, number,
   * callsign or network
   *
   * @param searchString String to search for
   * @param includeDisabled whether to include non-viewable channels
   * @return Array of matching Channel objects, ordered by StationID
   */
  public Channel[] searchForChannel(String searchString, boolean includeDisabled) {
    searchString = searchString.toLowerCase();

    // Only try matching by logical channel number when the
    // searchString is numeric.
    HashSet<Integer> matchingStationIds=new HashSet<Integer>();
    try {
      Integer.parseInt(searchString);
      // yes, searchstring is an int...
      // build list of matching channels on all providers by stationID...
      EPG epg = EPG.getInstance();
      for(long providerID : epg.getAllProviderIDs() ){
        @SuppressWarnings("unchecked")
        // map of station ID to array of logical channel numbers
        Map<Integer, String[]> logicalLineup = epg.getLineup(providerID);
        for (Entry<Integer, String[]> entry : logicalLineup.entrySet()) {
          for (String logicalId : entry.getValue()) {
            if (logicalId.startsWith(searchString))
              matchingStationIds.add(entry.getKey());
          }
        }
      }
    } catch(NumberFormatException e) { /* ignore */ }

    ArrayList<Channel> okChannels = new ArrayList<Channel>();
    Index idx = getIndex(CHANNEL_CODE);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    try {
      idx.table.acquireReadLock();
      for (int i = 0; i < idx.table.num; i++) {
        Channel chan = (Channel) idx.data[i];
        if (includeDisabled || chan.isViewable()) {
          // use StringMatchUtils to perform faster substring matching
          // on start-of-word where multiple words may match
          if ((chan.getName() != null
              && chan.getName().toLowerCase().startsWith(searchString))
              || (matchingStationIds.contains(chan.getStationID()))
              || (chan.getNetwork() != null
              && StringMatchUtils.wordMatchesLowerCase(chan.getNetwork(), searchString))
              || (chan.getLongName() != null
              && StringMatchUtils.wordMatchesLowerCase(chan.getLongName(), searchString))) {
            okChannels.add(chan);
            if (okChannels.size() >= MAX_SEARCH_RESULTS) break;
          }
        }
      }
    } finally {
      idx.table.releaseReadLock();
    }
    return okChannels.toArray(Pooler.EMPTY_CHANNEL_ARRAY);
  }

  static int sign(long x)
  {
    return (x == 0) ? 0 : ((x < 0) ? -1 : 1);
  }

  public boolean ok(Airing x)
  {
    return (x instanceof MediaFile.FakeAiring) || ((x instanceof ManualRecord.FakeAiring) && getManualRecord(x) != null) ||
        (getAiringForID(x.id) != null);
  }

  /*
   * File Operations
   */
  private boolean restoreBackup()
  {
    if (dbBackupFile == null || dbFile == null) return false;
    // Restore a backup copy
    int x = 0;
    File corruptWizFile = new File(dbFile.getParentFile(), dbFile.getName() + ".corrupt" + (x++));
    while (corruptWizFile.isFile())
      corruptWizFile = new File(dbFile.getParentFile(), dbFile.getName() + ".corrupt" + (x++));
    if (!dbFile.renameTo(corruptWizFile))
      return false;
    if (!dbBackupFile.renameTo(dbFile))
    {
      try
      {
        IOUtils.copyFile(dbBackupFile, dbFile);
        dbBackupFile.delete();
      }
      catch (IOException e)
      {
        if (Sage.DBG) System.out.println("Error restoring backup of:" + e);
        return false;
      }
    }
    return true;
  }

  private DBObject loadDBObject(byte code, DataInput in, byte ver, Map<Integer, Integer> idMap, int baseID)
      throws IOException {

    switch (code)
    {
      case CHANNEL_CODE:
        return new Channel(in, ver, idMap);
      case SHOW_CODE:
        return new Show(in, ver, idMap);
      case AIRING_CODE:
        return new Airing(in, ver, idMap);
      case WATCH_CODE:
        return new Watched(in, ver, idMap);
      case AGENT_CODE:
        return new Agent(in, ver, idMap);
      case MEDIAFILE_CODE:
        return new MediaFile(in, ver, idMap);
      case MANUAL_CODE:
        return new ManualRecord(in, ver, idMap);
      case WASTED_CODE:
        return new Wasted(in, ver, idMap);
      case WIDGET_CODE:
        // 601
        //return new WidgetImp(in, ver, idMap, baseID);
        throw (new tv.sage.SageRuntimeException("Wizard no longer supports Widgets", tv.sage.SageExceptable.INTEGRITY));
      case PLAYLIST_CODE:
        return new Playlist(in, ver, idMap);
      case TVEDITORIAL_CODE:
        return new TVEditorial(in, ver, idMap);
      case SERIESINFO_CODE:
        return new SeriesInfo(in, ver, idMap);
      case USERRECORD_CODE:
        return new UserRecord(in, ver, idMap);
      case PEOPLE_CODE:
        return new Person(in, ver, idMap);
      default:
        return new Stringer(in, ver, idMap);
    }
  }

  private Table lastTable;

  private byte processXctFromStream(DataInput in, byte ver, long cmdLength,
      Map<Integer, Integer> idMap, int baseID) throws IOException
  {
    if (cmdLength == -1)
    {
      cmdLength = in.readInt();
    }
    byte opcode = in.readByte();
    if (opcode == XCTS_DONE)
      return opcode;
    byte typecode = in.readByte();
    if (opcode == SIZE)
    {
      lastTable = getTable(typecode);
      if (lastTable == null)
        return opcode;
      int theSize = in.readInt();
      if (Sage.DBG) System.out.println("Wizard allocating table for " + getNameForCode(typecode) + " of size " + theSize);
      try {
        lastTable.acquireWriteLock();
        lastTable.num = theSize;
        lastTable.primary.data = new DBObject[theSize + INC_SIZE];
        for (int i = 0; i < lastTable.others.length; i++)
          lastTable.others[i].data = new DBObject[theSize + INC_SIZE];
      } finally {
        lastTable.releaseWriteLock();
      }
    }
    else if (opcode == FULL_DATA)
    {
      if (lastTable == null)
        return opcode;
      long loadStart = Sage.eventTime();
      if (Sage.DBG) System.out.println("Wizard loading main index for " + getNameForCode(lastTable.tableCode) + " bytes=" + cmdLength);
      if (GENERATE_MEDIA_MASK)
      {
        // NOTE: This will take a LONG time to load in this case because we'll be extracting file format
        // information for every media file in the DB, so update the splash so the user realizes this.
        Sage.setSplashText(Sage.rez("MediaFile_Format_Load_Wait"));
      }
      try {
        lastTable.acquireWriteLock();
        Index indy = lastTable.primary;
        LuceneIndex index = null;
        boolean indexInitialized = false;
        if(lastTable.tableCode == SHOW_CODE) {
          index = getShowIndex();
        } else if (lastTable.tableCode == PEOPLE_CODE) {
          index = getPersonIndex();
        }
        if(index != null) {
          if(index.getWriter().numDocs() != indy.table.num) {
            if (Sage.DBG) {
              System.out.println("Lucene index(" + index.name
                  + ") does not have same item count as wizard (idx:" + index.getWriter().numDocs()
                  + ", wiz:" + indy.table.num + ") - RESETING");
            }
            index.resetIndex();
          } else {
            if(Sage.DBG) System.out.println("Lucene index(" + index.name
                + ") counts(" + index.getWriter().numDocs()
                + ", " + indy.table.num + ") - Loaded from storage; Wizard not impeded");
            indexInitialized = true;
          }
        }
        for (int i = 0; i < indy.table.num; i++)
        {
          DBObject newObj = loadDBObject(lastTable.tableCode, in, ver, idMap, baseID);
          if(newObj != null) {
            // This object is being added to the table out-of-band, see if we must updated
            if(index != null && !indexInitialized) {
              if(lastTable.tableCode == SHOW_CODE) {
                addShowToLucene((Show)newObj);
              } else if (lastTable.tableCode == PEOPLE_CODE) {
                addPersonToLucene((Person)newObj);
              }
            }
          }
          indy.data[i] = newObj;
          // Widgets/Shows use a lot of memory on load because of the property conversions
          if ((i % 1000) == 0)
            Sage.gcPause();
        }
        long totalTime = Sage.eventTime() - loadStart;
        if (Sage.DBG) System.out.println("Load time for " + getNameForCode(lastTable.tableCode) + " " + totalTime +
            " msec " + (((float)totalTime)/indy.table.num) + " msec/object");
        loadStart = Sage.eventTime();
        indy.check();
        if (Sage.DBG) System.out.println("Index check time for " + getNameForCode(lastTable.tableCode) + " " + (Sage.eventTime() - loadStart) + " msec");
        if (ver < 0x53)
        {
          for (int k = 0; k < lastTable.others.length; k++)
          {
            loadStart = Sage.eventTime();
            if (Sage.DBG) System.out.println("Wizard building alt. index " + k + " for " + getNameForCode(lastTable.tableCode));
            System.arraycopy(lastTable.primary.data, 0, lastTable.others[k].data, 0, lastTable.num);
            lastTable.others[k].check();
            if (Sage.DBG) System.out.println("Alt. index " + k + " for " + getNameForCode(lastTable.tableCode) + " load time " + (Sage.eventTime() - loadStart) + " msec");
          }
        }

        // Mark un-initialzied lucene indexs as live.
        if (index != null) {
          if (!indexInitialized) {
            synchronized (index.getTransactionLock()) {
              if (Sage.DBG)
                System.out.println("Lucene index(" + index.name + ") built from Wizard");
              index.indexTransactions.add(null);
              index.getTransactionLock().notifyAll();
            }
          } else {
            if (Sage.DBG)
              System.out.println("Lucene index(" + index.name + ") initialized from disk");
          }
        }
      } finally {
        lastTable.releaseWriteLock();
      }
      Sage.gcPause();
    }
    else if (opcode == INDEX_DATA)
    {
      if (lastTable == null)
        return opcode;
      long loadStart = Sage.eventTime();
      if (Sage.DBG) System.out.println("Wizard loading alt index " + typecode + " for " + getNameForCode(lastTable.tableCode) + " bytes=" + cmdLength);
      try {
        lastTable.acquireWriteLock();
        Index indy = lastTable.getIndex(typecode);
        Index primaryIndex = lastTable.primary;
        boolean loadFailed = false;
        for (int i = 0; i < indy.table.num; i++)
        {
          int idx = in.readInt();
          if (idx < 0 || idx >= indy.table.num) {
            loadFailed = true;
            if (Sage.DBG) System.out.println("ERROR in DB file index table, invalid index of:" + idx + " size=" + indy.table.num);
          } else {
            indy.data[i] = primaryIndex.data[idx];
          }
        }
        if (loadFailed) {
          if (Sage.DBG) System.out.println("Repairing index due to error on load...");
          // We call check() below which will fix the ordering...normally it would be presorted so check()
          // would execute very quickly, but this will have to reorder things so it will execute more slowly but
          // that's better than completely failing to load
          System.arraycopy(primaryIndex.data, 0, indy.data, 0, indy.table.num);
        }
        long totalTime = Sage.eventTime() - loadStart;
        if (Sage.DBG) System.out.println("Load time for alt index " + typecode+ " for " + getNameForCode(lastTable.tableCode) + " " + totalTime +
            " msec");
        loadStart = Sage.eventTime();
        indy.check();
        if (Sage.DBG) System.out.println("Index check time for alt index " + typecode + " for " +
            getNameForCode(lastTable.tableCode) + " " + (Sage.eventTime() - loadStart) + " msec");
      } finally {
        lastTable.releaseWriteLock();
      }
    }
    else
    {
      boolean remove = (opcode == REMOVE);
      boolean update = (opcode == UPDATE);
      if (!remove && !update && opcode != ADD)
      {
        if (dbErrorsLogged < MAX_DB_ERRORS_TO_LOG)
        {
          dbErrorsLogged++;
          if (Sage.DBG) System.out.println("INVALID OPCODE in DB of " + opcode + " skipping record.");
        }
        return opcode;
      }
      try {
        acquireWriteLock(typecode);
        DBObject newObj = loadDBObject(typecode, in, ver, idMap, baseID);
        if (getTable(typecode) != null)
        {
          if (remove)
            getTable(typecode).remove(newObj, false);
          else if (update)
          {
            DBObject oldObj = getTable(typecode).primary.getSingle(newObj);
            if (oldObj != null)
              getTable(typecode).update(oldObj, newObj, false);
          }
          else
            getTable(typecode).add(newObj, false);
        }
        if (Sage.client)
        {
          // We update the modification time here because there's no other way on the client to recognize the changes
          updateLastModified(newObj.getMediaMask());
        }
      } finally {
        releaseWriteLock(typecode);
      }
    }
    return opcode;
      }

  private boolean loadDBFile() throws Throwable//IOException
  {
    boolean saveItNow = false;
    if (Sage.client)
    {
      return false;
    }
    else if (dbFile.isFile() && dbFile.length() > 0)
    {
      SageDataFile in = null;
      loading = true;
      boolean finishedAll = false;
      Map<Integer, Integer> idTranslation = null;
      if (nextID > SERVER_INIT_ID_BOUNDARY)
      {
        File safeBackupFile = null;
        // Make a backup of the Wiz file before we do this, be sure not to overwrite anything
        safeBackupFile = new File(dbFile.getParentFile(), "Wiz.bin.PRECOMPRESSED_IDS");
        int fooNum = 1;
        while (safeBackupFile.isFile())
        {
          safeBackupFile =  new File(safeBackupFile.getParentFile(), "Wiz.bin.PRECOMPRESSED_IDS" + fooNum);
          fooNum++;
        }
        try
        {
          IOUtils.copyFile(dbFile, safeBackupFile);
          System.out.println("DATABASE ID ROLLOVER POTENTIAL - REBUILDING IDS OF ALL OBJECTS");
          nextID = 1;
          idTranslation = new HashMap<Integer, Integer>();
          saveItNow = true;
        }
        catch (IOException e)
        {
          if (Sage.DBG) System.out.println("Error doing backup for ID compression of:" + e);
        }
      }
      try
      {
        long fileLength = dbFile.length();
        in = new SageDataFile(new EncryptedSageFile(new BufferedSageFile(
            new LocalSageFile(dbFile, true),
            BufferedSageFile.READ_BUFFER_SIZE)),
            Sage.I18N_CHARSET);

        // Testing shows the DB loads 5% faster if this is false...not much of an optimization, but it helps
        //in.setOptimizeReadFully(false);
        byte b1 = in.readUnencryptedByte();
        byte b2 = in.readUnencryptedByte();
        byte b3 = in.readUnencryptedByte();
        if ((b1 != 'W') || (b2 != 'I') || (b3 != 'Z'))
        {
          throw new IOException("Invalid DB file format!");
        }

        version = in.readUnencryptedByte();
        if (version == BAD_VERSION)
          throw new IOException("Invalid DB file, only partially saved.");
        if (Sage.DBG) System.out.println("Reading DB file:"+dbFile+" with version " + version);
        if (version != VERSION) saveItNow = true;
        if (version < 0x09)
          throw new IllegalArgumentException("Wizard does not support DBs before version 9!");
        if (version < 0x2F || version >= 0x54)
        {
          // unencrypted DB file
          //Remove encryption filter.
          in = new SageDataFile(in.getUnencryptedSource(), (version == 0x54) ? Sage.BYTE_CHARSET : Sage.I18N_CHARSET);
          // We are already at the right position since we didn't re-open the file.
          /*in.readUnencryptedByte();
          in.readUnencryptedByte();
          in.readUnencryptedByte();
          in.readUnencryptedByte();*/
        }
        if (version < 0x35)
        {
          // Switch to byte chars
          in.setCharset(Sage.BYTE_CHARSET);
        }
        if (version < 0x38)
        {
          // DB is from before we had the media mask, so we need to generate it for all DBObjects on load
          GENERATE_MEDIA_MASK = true;
        }
        Table t = null;
        HashMap<Byte, Set<DBObject>> killMap = new HashMap<Byte, Set<DBObject>>();
        if (Sage.DBG) System.out.println("DBFile at version " + version + " FileSize=" + fileLength);
        long fp = in.position();
        int cmdLength = 0;
        byte opcode = 0;
        byte typecode = 0;
        int numTransactionRecords = 0;
        while (fileLength > in.position())
        {
          long newFp = in.position();
          if (fp + cmdLength != newFp)
          {
            if (!finishedAll) {
              throw new IOException("ERROR DB RECORD LENGTH VIOLATION was " + (newFp - fp) + " should be " + cmdLength +
                  " fp=" + fp + " length=" + fileLength + " opcode=" + opcode + " typecode=" + typecode);
            }
            else if (dbErrorsLogged < MAX_DB_ERRORS_TO_LOG)
            {
              dbErrorsLogged++;
              if (Sage.DBG) System.out.println("ERROR DB RECORD LENGTH VIOLATION was " + (newFp - fp) + " should be " + cmdLength +
                  " fp=" + fp + " length=" + fileLength + " opcode=" + opcode + " typecode=" + typecode);
            }
            in.seek(fp + cmdLength);
            saveItNow = true;
          }
          fp += cmdLength;
          cmdLength = in.readInt();
          cmdLength = Math.max(1, cmdLength);
          if (cmdLength + fp > fileLength)
          {
            if (!finishedAll)
              throw new IOException("BOGUS command length, ending file load now. req=" + (cmdLength + fp) +
                  " actual=" + fileLength);
            else
            {
              // If we terminate this way because of a corrupt record; we still want to run the validation on the objects
              // so don't throw an exception that'll make us skip that part! Just break out of the loop.
              if (Sage.DBG) System.out.println("BOGUS-2 command length, ending file load now. req=" + (cmdLength + fp) +
                  " actual=" + fileLength);
              saveItNow = true;
              break;
            }
          }
          cmdLength = Math.max(cmdLength, 4);

          try
          {
            opcode = processXctFromStream(in, version, cmdLength, idTranslation, 0);
            if (opcode != SIZE && opcode != FULL_DATA)
            {
              if (!finishedAll && (opcode == ADD || opcode == REMOVE || opcode == UPDATE))
              {
                finishedAll = true;
                if (Sage.DBG) System.out.println("Wizard processing transactional records...");
              }
              if (numTransactionRecords % 100 == 0)
              {
                Sage.setSplashText(Sage.rez("Module_Init_Progress", new Object[] { Sage.rez("Object_Database"), new Double((((double)fp)/fileLength)) }));
              }
              if (numTransactionRecords++ > NUM_TRANSACTIONS_TO_COMPRESS_ON_LOAD)
                saveItNow = true;
            }
            else
              Sage.setSplashText(Sage.rez("Module_Init_Progress", new Object[] { Sage.rez("Object_Database"), new Double((((double)fp)/fileLength)) }));
          }
          catch (IOException e)
          {
            if (Sage.DBG) System.out.println("ERROR Processing DB record, skipping it and continuing. Error:" + e);
            if (Sage.DBG) Sage.printStackTrace(e);
            saveItNow = true;
          }
        }

        // Setup the noShowID before we do validation because getShow() is used quite
        // often in performing validation and it may resolve to the noShow
        refreshNoShow();

        Sage.gcPause();
        // Walk through all of the DBObjects and validate them
        if (validateObjs)
        {
          if (Sage.DBG) System.out.println("Wizard performing validation on database objects...");
          for (int tableNum = 0; tableNum < tables.length; tableNum++)
          {
            t = tables[tableNum];
            if (t == null) continue;
            Index indy = t.primary;
            for (int i = 0; i < indy.table.num; i++)
            {
              DBObject newObj = indy.data[i];
              if (!newObj.validate())
              {
                Set<DBObject> tempSet = killMap.get(t.tableCode);
                if (tempSet == null)
                {
                  tempSet = new HashSet<DBObject>();
                  killMap.put(t.tableCode, tempSet);
                }
                tempSet.add(newObj);
                String errString = "";
                try{errString = newObj.toString();}catch(Throwable thr){}
                if (Sage.DBG) System.out.println("ERROR DBObject failed validation " +
                    newObj.getClass() + " id=" + newObj.id + " str=" + errString);
              }
            }
          }
        }

        if (!killMap.isEmpty()) saveItNow = true;
        for (Byte nextB : killMap.keySet())
        {
          for (DBObject obj : killMap.get(nextB))
          {
            getTable(nextB).remove(obj, false);
          }
        }

        if (GENERATE_MEDIA_MASK)
        {
          if (Sage.DBG) System.out.println("Applying media mask update throughout the airing table...");
          // Now we have to find all of the Airings that are on linked StationIDs and mark them all as
          // being TV files and also transfer it all down recursively
          // NOTE: We have to be careful here as we don't want to call methods in the EPG
          // which rely on the initialization of the capture system or anything else since the DB
          // is loaded first and this is called at the end of DB load.
          EPGDataSource[] epgdss = EPG.getInstance().getDataSources();
          Set<Integer> usedStations = new HashSet<Integer>();
          for (int i = 0; i < epgdss.length; i++)
          {
            int[] allStats = EPG.getInstance().getAllStations(epgdss[i].getProviderID());
            for (int j = 0; j < allStats.length; j++)
            {
              usedStations.add(allStats[j]);
            }
          }
          Index airIndex = getIndex(AIRING_CODE, AIRINGS_BY_CT_CODE);
          for (int i = 0; i < airIndex.table.num; i++)
          {
            Airing iAir = (Airing) airIndex.data[i];
            if (iAir != null && usedStations.contains(iAir.stationID))
            {
              iAir.addMediaMask(DBObject.MEDIA_MASK_TV);
              Show s = iAir.getShow();
              if (s != null && !isNoShow(s))
                s.addMediaMaskRecursive(DBObject.MEDIA_MASK_TV);
            }
          }
        }

        if (REPAIR_CATEGORIES)
        {
          if (Sage.DBG) System.out.println("Performing database repair on Show category information now...");
          saveItNow = true;
          int numRepairs = 0;
          Index showIndex = getIndex(SHOW_CODE);
          for (int i = 0; i < showIndex.table.num; i++)
          {
            Show s = (Show) showIndex.data[i];
            if (s != null && s.categories.length > 0)
            {
              if (getCategoryForID(s.categories[0].id, false) == null)
              {
                numRepairs++;
                s.categories[0] = getCategoryForName(s.categories[0].name);
              }
            }
          }
          if (Sage.DBG) System.out.println("Done with category repair...fixed " + numRepairs + " entries.");
          REPAIR_CATEGORIES = false;
        }
      }
      catch (Throwable e)
      {
        if (Sage.DBG) Sage.printStackTrace(e);
        if (!finishedAll) throw e;
        if (Sage.DBG) System.out.println("DBFile PREMATURELY ended, but still using it. Error:" + e);
        if (Sage.DBG) Sage.printStackTrace(e);
        return false;
      }
      finally
      {
        loading = false;
        if (in != null)
        {
          in.close();
          in = null;
        }
      }
    }
    else return false;
    Sage.gcPause();
    return !saveItNow;
  }

  Vector diffWidgetFile(STVEditor myStudio, File diffFile) throws Throwable
  {
    if (UIManager.ENABLE_STUDIO)
    {
      if (Sage.DBG) System.out.println("Performing diff against widget file:" + diffFile);

      Properties fakePrefs = new Properties();
      fakePrefs.put("STV", diffFile.toString());

      tv.sage.ModuleGroup moduleGroup = tv.sage.ModuleManager.loadModuleGroup(fakePrefs);

      // Now we have both sets of Widgets loaded into the DB and we can analyze them for
      // differences
      Widget[] currWidgs = myStudio.getUIMgr().getModuleGroup().getWidgets();
      Widget[] diffWidgs = moduleGroup.getWidgets();
      Vector rv = myStudio.generateDiffOps(currWidgs, currWidgs.length, diffWidgs, diffWidgs.length);
      moduleGroup.dispose();
      return rv;
    }
    else
      return null;
  }

  Vector diffWidgetFileUID(STVEditor myStudio, File diffFile) throws Throwable
  {
    if (UIManager.ENABLE_STUDIO)
    {
      if (Sage.DBG) System.out.println("Performing UID diff against widget file:" + diffFile);

      Properties fakePrefs = new Properties();
      fakePrefs.put("STV", diffFile.toString());

      tv.sage.ModuleGroup moduleGroup = tv.sage.ModuleManager.loadModuleGroup(fakePrefs);

      // Now we have both sets of Widgets loaded into the DB and we can analyze them for
      // differences
      Widget[] currWidgs = myStudio.getUIMgr().getModuleGroup().getWidgets();
      Widget[] diffWidgs = moduleGroup.getWidgets();
      Vector rv = myStudio.generateDiffOpsUID(currWidgs, currWidgs.length, diffWidgs, diffWidgs.length);
      moduleGroup.dispose();
      return rv;
    }
    else
      return null;
  }

  Vector diffWidgetFilesUID(STVEditor myStudio, File diffFileOld, File diffFileNew) throws Throwable
  {
    if (UIManager.ENABLE_STUDIO)
    {
      if (Sage.DBG) System.out.println("Performing UID diff between widget files old=" + diffFileOld + " and new=" + diffFileNew);

      Properties fakePrefs = new Properties();
      fakePrefs.put("STV", diffFileOld.toString());
      tv.sage.ModuleGroup moduleGroup1 = tv.sage.ModuleManager.loadModuleGroup(fakePrefs);
      Widget[] oldWidgs = moduleGroup1.getWidgets();
      fakePrefs.put("STV", diffFileNew.toString());
      tv.sage.ModuleGroup moduleGroup2 = tv.sage.ModuleManager.loadModuleGroup(fakePrefs);
      Widget[] newWidgs = moduleGroup2.getWidgets();
      Vector rv = myStudio.generateDiffOpsUID(newWidgs, newWidgs.length, oldWidgs, oldWidgs.length);
      moduleGroup1.dispose();
      moduleGroup2.dispose();
      return rv;
    }
    else
      return null;
  }

  protected void finalize() throws IOException
  {
    if (dbout != null) dbout.close();
  }

  void goodbye()
  {
    if (dbout != null)
    {
      try
      {
        dbout.close();
      }
      catch (Exception e){}
    }
  }

  private void saveDBFile() throws IOException
  {
    if (Sage.client || disableDatabase) return;
    synchronized (pendingWriteXcts)
    {
      // If writes are suspended already due to sending a DB to a client...then hold off until that's done before we proceed.
      while (suspendWrite)
      {
        try { pendingWriteXcts.wait(1000);} catch (Exception e){}
      }
      suspendWrite = true;
    }
    synchronized (outLock)
    {
      boolean backupFailed = false;
      if (dbout != null)
      {
        dbout.close();
        dbout = null;
      }
      if (Sage.DBG) System.out.println("Wizard backing up database file...");

      LuceneIndex index = getShowIndex();
      if(index != null) {
        synchronized (index.getTransactionLock()) {
          index.indexTransactions.add(null);
          index.getTransactionLock().notifyAll();
        }
      }
      index = getPersonIndex();
      if(index != null) {
        synchronized (index.getTransactionLock()) {
          index.indexTransactions.add(null);
          index.getTransactionLock().notifyAll();
        }
      }
      if (dbFile.isFile())
      {
        // Save a backup copy
        dbBackupFile.delete();
        if (!dbFile.renameTo(dbBackupFile))
        {
          if (Sage.DBG) System.out.println("Renaming of DB Backup file failed...do a copy instead.");
          try {
            IOUtils.copyFile(dbFile, dbBackupFile);
            dbFile.delete();
          } catch (Exception e) {
            System.out.println("Copying DB Backup file failed: " + e + ".  Will only write transactionals.");
            backupFailed = true;
          }
        }
      }
      // Looks cleaner and creates one less string for !Sage.EMBEDDED.
      String fileMode = "rwd";
      if(!backupFailed) {
        int dbWriteFlags = 0;

        long startSaveTime = Sage.eventTime();
        File realDBFile = dbFile;
        // Use a temp file here so that we can be 100% sure the valid version byte is present and the
        // file is fully synced before it becomes the Wiz.bin file.
        dbFile = new File(dbFile.getAbsolutePath() + ".tmp");
        dbFile.createNewFile();
        if (Sage.DBG) System.out.println("Wizard compressing new file with version "+VERSION+"...");
        // 128k was chosen from observing most random writes are typically less than this distance
        // behind the actual write buffer. The original default of 64k would often miss even with
        // the optimizer trying to compensate for the last miss since the misses were more than 64k
        // behind the latest write.
        dbout = new SageDataFile(new BufferedSageFile(
            new LocalSageFile(dbFile, fileMode),
            BufferedSageFile.READ_BUFFER_SIZE, 131072),
            Sage.I18N_CHARSET);
        dbout.writeUnencryptedByte((byte) 'W');
        dbout.writeUnencryptedByte((byte) 'I');
        dbout.writeUnencryptedByte((byte) 'Z');
        dbout.flush();

        // The BAD_VERSION marker is to signify incompletely saved DB files.
        long verPos = dbout.position();
        dbout.writeUnencryptedByte(BAD_VERSION);
        dbout.sync();

        for (int i = 0; i < WRITE_ORDER.length; i++)
        {
          if (Sage.DBG) System.out.println("Wizard writing out table info for " +
              getNameForCode(WRITE_ORDER[i]));
          Table currTable = getTable(WRITE_ORDER[i]);
          // 7/14/2016 JS: Setting the variable here doesn't make any sense; it never gets used.
          long fp;// = dbout.position();
          dbout.writeInt(10);
          dbout.writeByte(SIZE);
          try {
            currTable.acquireReadLock();
            dbout.writeByte(currTable.tableCode);
            dbout.writeInt(currTable.num);
            if (currTable.num > 0)
            {
              fp = dbout.position();
              dbout.writeInt(Integer.MAX_VALUE);
              dbout.writeByte(FULL_DATA);
              dbout.writeByte(currTable.primary.indexCode);

              DBObject[] currData = currTable.primary.data;
              for (int j = 0; j < currTable.num; j++)
              {
                DBObject dbobj = currData[j];
                dbobj.write(dbout, dbWriteFlags);
                dbobj.lookupIdx = -(j + 1);
              }

              logCmdLength(dbout, fp);

              for (int k = 0; k < currTable.others.length; k++)
              {
                Index currIdx = currTable.others[k];
                currData = currIdx.data;
                dbout.writeInt((4 * currTable.num) + 6);
                dbout.writeByte(INDEX_DATA);
                dbout.writeByte(currIdx.indexCode);
                for (int m = 0; m < currTable.num; m++)
                {
                  dbout.writeInt(-1*(currData[m].lookupIdx + 1));
                }
              }
              // We don't need to log the command length because we knew it when we started writing it out
            }
            // Remove any transasctions that are for the full table we just wrote out,
            // updates that occur after this need to be written
            synchronized (pendingWriteXcts)
            {
              for (int j = 0; j < pendingWriteXcts.size(); j++)
              {
                XctObject currXct = pendingWriteXcts.get(j);
                if (currXct.objectType == currTable.tableCode)
                  pendingWriteXcts.removeElementAt(j--);
              }
            }
          } finally {
            currTable.releaseReadLock();
          }
        }
        long endSaveTime = Sage.eventTime();
        if (Sage.DBG) System.out.println("DB saveTime=" + ((endSaveTime - startSaveTime)/1000.0) + " sec");
        numUncompXcts = 0;
        long fp = dbout.position();
        dbout.setLength(fp);
        dbout.sync();
        dbout.seek(verPos);
        dbout.writeUnencryptedByte(VERSION);
        dbout.close();
        dbFile.renameTo(realDBFile);
        dbFile = realDBFile;
        // 128k was chosen from observing most random writes are typically less than this distance
        // behind the actual write buffer. The original default of 64k would often miss even with
        // the optimizer trying to compensate for the last miss since the misses were more than 64k
        // behind the latest write.
        dbout = new SageDataFile(new BufferedSageFile(
            new LocalSageFile(dbFile, fileMode),
            BufferedSageFile.READ_BUFFER_SIZE, 131072),
            Sage.I18N_CHARSET);
        dbout.seek(fp);
      } else {
        // backup failed to update; make sure we've got dbout set.
        dbout = new SageDataFile(new BufferedSageFile(
            new LocalSageFile(dbFile, fileMode),
            BufferedSageFile.READ_BUFFER_SIZE, 131072),
            Sage.I18N_CHARSET);
        dbout.seek(dbout.length());
      }
      while (true)
      {
        XctObject nextXct = null;
        synchronized (pendingWriteXcts)
        {
          if (pendingWriteXcts.isEmpty())
          {
            suspendWrite = false;
            break;
          }
          nextXct = pendingWriteXcts.remove(0);
        }

        long fp = dbout.position();
        dbout.writeInt(Integer.MAX_VALUE);
        dbout.writeByte(nextXct.xctType);
        dbout.writeByte(nextXct.objectType);
        try {
          acquireReadLock(nextXct.objectType);
          nextXct.obj.write(dbout, 0);
        } finally {
          releaseReadLock(nextXct.objectType);
        }
        logCmdLength(dbout, fp);
        numUncompXcts++;
      }
    }
    Sage.gcPause();
  }

  /*
   * ADD/REMOVE Object Methods
   * UPDATE Object Methods. For these if logTX is true, then it logs an update on the arg
   * and doesn't use the arg as a template for locating the one that it changed.
   */
  void logUpdate(DBObject updateMe, byte code)
  {
    if (disableDatabase) return;
    if (Sage.client) return;
    getTable(code).incModCount();
    synchronized (pendingWriteXcts)
    {
      if (suspendWrite)
      {
        pendingWriteXcts.add(new XctObject(UPDATE, code, updateMe));
        distributeOp(UPDATE, code, updateMe);
        return;
      }
      numUncompXcts++;
      synchronized (outLock)
      {
        SageDataFile frf = dbout;
        if (frf == null) return;
        long fp = frf.position();
        try
        {
          if (listeners.isEmpty())
          {
            frf.writeInt(Integer.MAX_VALUE);
            frf.writeByte(UPDATE);
            frf.writeByte(code);
            updateMe.write(frf, 0);
            logCmdLength(frf, fp);
          }
          else
          {
            byte[] xctData = distributeOp(UPDATE, code, updateMe);
            frf.writeInt(xctData.length + 4);
            frf.write(xctData);
          }
        }
        catch (Exception e)
        {
          if (Sage.DBG) System.out.println("Error updating DB file:" + e);
          if (Sage.DBG) e.printStackTrace();
          try
          {
            frf.flush();
            frf.seek(fp);
          }
          catch (IOException ioe)
          {
            System.out.println("IO Error updating DB file: " + ioe);
            ioe.printStackTrace();
          }
        }
      }
    }
  }

  // NOTE: Narflex - 05/10/2012 - We used to use the distributeUpdate call for sending out mediaMask changes during maintenance,
  // but then realized there's an inconsistency there, so we stopped using it for that. Now we're going to use it and add some other
  // distributeOP calls which handle distributing a xct during the save operation to connected clients since we can't write them
  // to the DB file at that time since the outLock is being held by the saveDBFile method.

  private byte[] distributeOp(byte opCode, byte tableCode, DBObject updateMe)
  {
    if (disableDatabase) return null;
    if (Sage.client || !hasListeners()) return null;
    synchronized (pendingWriteXcts)
    {
      if (cachedDOS == null || cachedBAOS == null)
      {
        cachedBAOS = new ByteArrayOutputStream(512);
        cachedDOS = new SageTVConnection.MyDataOutput(cachedBAOS);
      }
      else
        cachedBAOS.reset();
      try
      {
        cachedDOS.writeByte(opCode);
        cachedDOS.writeByte(tableCode);
        updateMe.write(cachedDOS, 0);
        byte[] xctData = cachedBAOS.toByteArray();
        distributeXct(xctData);
        return xctData;
      }
      catch (Exception e)
      {
        if (Sage.DBG) System.out.println("Error distributing DB operation:" + e);
      }
      return null;
    }
  }

  void logRemove(DBObject removeMe, byte code)
  {
    if (disableDatabase) return;
    if (Sage.client) return;
    getTable(code).incModCount();
    synchronized (pendingWriteXcts)
    {
      if (suspendWrite)
      {
        pendingWriteXcts.add(new XctObject(REMOVE, code, removeMe));
        distributeOp(REMOVE, code, removeMe);
        return;
      }
      numUncompXcts++;
      synchronized (outLock)
      {
        SageDataFile frf = dbout;
        if (frf == null) return;
        long fp = frf.position();
        try
        {
          if (listeners.isEmpty())
          {
            frf.writeInt(Integer.MAX_VALUE);
            frf.writeByte(REMOVE);
            frf.writeByte(code);
            removeMe.write(frf, 0);
            logCmdLength(frf, fp);
          }
          else
          {
            byte[] xctData = distributeOp(REMOVE, code, removeMe);
            frf.writeInt(xctData.length + 4);
            frf.write(xctData);
          }
        }
        catch (Exception e)
        {
          if (Sage.DBG) System.out.println("Error updating DB file:" + e);
          if (Sage.DBG) e.printStackTrace();
          try
          {
            frf.flush();
            frf.seek(fp);
          }
          catch (IOException ioe)
          {
            System.out.println("IO Error updating DB file: " + ioe);
            ioe.printStackTrace(System.out);
          }
        }
      }
    }
  }

  void logAdd(DBObject addMe, byte code)
  {
    if (disableDatabase) return;
    if (Sage.client) return;
    getTable(code).incModCount();
    synchronized (pendingWriteXcts)
    {
      if (suspendWrite)
      {
        pendingWriteXcts.add(new XctObject(ADD, code, addMe));
        distributeOp(ADD, code, addMe);
        return;
      }
      numUncompXcts++;
      synchronized (outLock)
      {
        SageDataFile frf = dbout;
        if (frf == null) return;
        long fp = frf.position();
        try
        {
          if (listeners.isEmpty())
          {
            frf.writeInt(Integer.MAX_VALUE);
            frf.writeByte(ADD);
            frf.writeByte(code);
            addMe.write(frf, 0);
            logCmdLength(frf, fp);
          }
          else
          {
            byte[] xctData = distributeOp(ADD, code, addMe);
            frf.writeInt(xctData.length + 4);
            frf.write(xctData);
          }
        }
        catch (Exception e)
        {
          if (Sage.DBG) System.out.println("Error updating DB file:" + e);
          if (Sage.DBG) e.printStackTrace(System.out);
          try
          {
            frf.flush();
            frf.seek(fp);
          }
          catch (IOException ioe)
          {
            System.out.println("IO Error updating DB file: " + ioe);
            ioe.printStackTrace(System.out);
          }
        }
      }
    }
  }

  public static void logCmdLength(SageDataFile frf, long fp) throws IOException
  {
    frf.writeIntAtOffset(fp, (int)(frf.position() - fp));
  }

  /*
   * Linear search methods
   */
  private Stringer getLinearStringerForName(String name, byte code)
  {
    return getLinearStringerForName(name, code, 0);
  }

  private Stringer getLinearStringerForName(String name, byte code, int createMediaMask)
  {
    if ((name == null) || (name.length() == 0)) return null;
    Table t = getTable(code);
    Stringer rv;
    try {
      t.acquireReadLock();
      rv = getLinearStringerForNameWhileLocked(t, name);
      if (rv != null && rv.hasMediaMask(createMediaMask))
        return rv;
    } finally {
      t.releaseReadLock();
    }
    // We must re-acquire the lock so that we can do the add; we cannot acquire
    // the write lock while we hold the read lock.
    try {
      t.acquireWriteLock();
      rv = getLinearStringerForNameWhileLocked(t, name);
      if (rv != null && rv.hasMediaMask(createMediaMask))
        return rv;
      if ( rv == null ) {
        rv = new Stringer(getNextWizID());
        rv.name = name;
        rv.ignoreCaseHash = name.toLowerCase().hashCode();
        rv.setMediaMask(createMediaMask);
        t.add(rv, true);
      } else {
        rv.addMediaMask(createMediaMask);
        logUpdate(rv, code);
      }
      return rv;
    } finally {
      t.releaseWriteLock();
    }
  }

  private Stringer getLinearStringerForNameWhileLocked(Table t, String name)
  {
    for (int i = 0; (i < t.num) && (t.primary.data[i] != null); i++)
    {
      if (((Stringer) t.primary.data[i]).name.equals(name))
        return (Stringer) t.primary.data[i];
    }
    return null;
  }

  Stringer getCategoryForName(String name)
  {
    return getLinearStringerForName(name, CATEGORY_CODE);
  }

  Stringer getSubCategoryForName(String name)
  {
    return getLinearStringerForName(name, SUBCATEGORY_CODE);
  }

  Stringer getNetworkForName(String name)
  {
    return getLinearStringerForName(name, NETWORK_CODE);
  }

  Stringer getRatedForName(String name)
  {
    return getLinearStringerForName(name, RATED_CODE);
  }

  Stringer getERForName(String name)
  {
    return getLinearStringerForName(name, ER_CODE);
  }

  Stringer getPRForName(String name)
  {
    return getLinearStringerForName(name, PR_CODE);
  }

  Stringer getYearForName(String name)
  {
    return getLinearStringerForName(name, YEAR_CODE);
  }

  Stringer getBonusForName(String name)
  {
    return getLinearStringerForName(name, BONUS_CODE);
  }

  Stringer getCategoryForName(String name, int createMediaMask)
  {
    return getLinearStringerForName(name, CATEGORY_CODE, createMediaMask);
  }

  Stringer getSubCategoryForName(String name, int createMediaMask)
  {
    return getLinearStringerForName(name, SUBCATEGORY_CODE, createMediaMask);
  }

  Stringer getNetworkForName(String name, int createMediaMask)
  {
    return getLinearStringerForName(name, NETWORK_CODE, createMediaMask);
  }

  Stringer getRatedForName(String name, int createMediaMask)
  {
    return getLinearStringerForName(name, RATED_CODE, createMediaMask);
  }

  Stringer getERForName(String name, int createMediaMask)
  {
    return getLinearStringerForName(name, ER_CODE, createMediaMask);
  }

  Stringer getPRForName(String name, int createMediaMask)
  {
    return getLinearStringerForName(name, PR_CODE, createMediaMask);
  }

  Stringer getYearForName(String name, int createMediaMask)
  {
    return getLinearStringerForName(name, YEAR_CODE, createMediaMask);
  }

  Stringer getBonusForName(String name, int createMediaMask)
  {
    return getLinearStringerForName(name, BONUS_CODE, createMediaMask);
  }

  /*
   * Fast search methods
   */
  private Stringer getStringerForName(Table t, Index indy, String name)
  {
    return getStringerForName(t, indy, name, 0);
  }

  private Stringer getStringerForName(Table t, Index indy, String name, int createMediaMask)
  {
    if ((name == null) || (name.length() == 0)) return null;
    Stringer rv;
    try {
      t.acquireReadLock();
      rv = getStringerForNameWhileLocked(t, indy, name);
      if (rv != null)
        return rv;
    } finally {
      t.releaseReadLock();
    }
    // We must re-acquire the lock so that we can do the add; we cannot acquire
    // the write lock while we hold the read lock.
    try {
      t.acquireWriteLock();
      rv = getStringerForNameWhileLocked(t, indy, name);
      if (rv != null)
        return rv;
      rv = new Stringer(getNextWizID());
      rv.name = new String(name);
      rv.ignoreCaseHash = name.toLowerCase().hashCode();
      rv.setMediaMask(createMediaMask);
      t.add(rv, !loading);
      return rv;
    } finally {
      t.releaseWriteLock();
    }
  }

  private Stringer getStringerForNameWhileLocked(Table t, Index indy, String name)
  {
    int low = 0;
    int high = t.num - 1;

    while (low <= high)
    {
      int mid = (low + high) >> 1;
      Stringer midVal = (Stringer) indy.data[mid];
      int cmp = midVal.name.compareTo(name);

      if (cmp < 0)
        low = mid + 1;
      else if (cmp > 0)
        high = mid - 1;
      else
        return midVal; // key found
    }
    return null;
  }

  public Channel getChannelForStationID(int stationID)
  {
    Channel myLookupChan = lookupChan.get();
    synchronized (myLookupChan)
    {
      myLookupChan.stationID = stationID;
      return (Channel) getIndex(CHANNEL_CODE).getSingle(myLookupChan);
    }
  }

  public Show getShowForID(int showID)
  {
    return (Show) getIndex(SHOW_CODE).getSingle(showID);
  }

  public Agent getAgentForID(int agentID)
  {
    return (Agent) getIndex(AGENT_CODE).getSingle(agentID);
  }

  Channel getChannelForID(int channelID)
  {
    Index idx = getIndex(CHANNEL_CODE);
    try {
      idx.table.acquireReadLock();
      for (int i = 0; i < idx.table.num; i++)
        if (idx.data[i].id == channelID)
          return (Channel) idx.data[i];
    } finally {
      idx.table.releaseReadLock();
    }
    return null;
  }

  public Playlist getPlaylistForID(int playlistID)
  {
    return (Playlist) getIndex(PLAYLIST_CODE).getSingle(playlistID);
  }

  public Airing getAiringForID(int airingID)
  {
    return getAiringForID(airingID, true, true);
  }

  Airing getAiringForID(int airingID, boolean checkFiles, boolean checkManuals)
  {
    if (airingID == 0) return null;
    Airing rv = (Airing) getIndex(AIRING_CODE).getSingle(airingID);
    if (rv != null) return rv;
    if (checkFiles)
    {
      MediaFile mf = getFileForAiring(airingID);
      if (mf != null)
      {
        return mf.getContentAiring();
      }
    }
    if (checkManuals)
    {
      ManualRecord mr = getManualRecord(airingID);
      if (mr != null)
      {
        return mr.getSchedulingAiring();
      }
    }
    return null;
  }

  Stringer getCategoryForID(int id)
  {
    return getCategoryForID(id, true);
  }

  private Stringer getCategoryForID(int id, boolean allowSubCat)
  {
    if (id == 0) return null;
    if (id < 0)
      return (Stringer) getIndex(CATEGORY_CODE).data[(-id) - 1];
    Stringer rv = (Stringer) getIndex(CATEGORY_CODE).getSingle(id);
    if (rv == null && allowSubCat)
    {
      rv = getSubCategoryForID(id);
      if (!REPAIR_CATEGORIES)
      {
        REPAIR_CATEGORIES = true;
        if (Sage.DBG) System.out.println("ERROR WITH DATABASE - FOUND CATEGORY ITEM LINKED TO SUBCATEGORY INDEX...REPAIR THIS AFTER LOAD IS FINISHED...THIS IS A KNOWN LEGACY BUG");
      }
    }
    return rv;
  }

  Stringer getSubCategoryForID(int id)
  {
    if (id == 0) return null;
    if (id < 0)
      return (Stringer) getIndex(SUBCATEGORY_CODE).data[(-id) - 1];
    Stringer rv = (Stringer) getIndex(SUBCATEGORY_CODE).getSingle(id);
    return rv;
  }

  Stringer getTitleForID(int id)
  {
    if (id == 0) return null;
    if (id < 0)
      return (Stringer) getIndex(TITLE_CODE).data[(-id) - 1];
    return (Stringer) getIndex(TITLE_CODE).getSingle(id);
  }

  Stringer getTitleForName(String name)
  {
    return getStringerForName(getTable(TITLE_CODE), getIndex(TITLE_CODE, TITLES_BY_NAME_CODE), name);
  }

  Stringer getTitleForName(String name, int createMediaMask)
  {
    return getStringerForName(getTable(TITLE_CODE), getIndex(TITLE_CODE, TITLES_BY_NAME_CODE), name, createMediaMask);
  }

  Stringer getPrimeTitleForID(int id)
  {
    if (id == 0) return null;
    return (Stringer) getIndex(PRIME_TITLE_CODE).getSingle(id);
  }

  Stringer getNetworkForID(int id)
  {
    if (id == 0) return null;
    if (id < 0)
      return (Stringer) getIndex(NETWORK_CODE).data[(-id) - 1];
    return (Stringer) getIndex(NETWORK_CODE).getSingle(id);
  }

  Stringer getRatedForID(int id)
  {
    if (id == 0) return null;
    if (id < 0)
      return (Stringer) getIndex(RATED_CODE).data[(-id) - 1];
    return (Stringer) getIndex(RATED_CODE).getSingle(id);
  }

  Stringer getPRForID(int id)
  {
    if (id == 0) return null;
    if (id < 0)
      return (Stringer) getIndex(PR_CODE).data[(-id) - 1];
    return (Stringer) getIndex(PR_CODE).getSingle(id);
  }

  Stringer getERForID(int id)
  {
    if (id == 0) return null;
    if (id < 0)
      return (Stringer) getIndex(ER_CODE).data[(-id) - 1];
    return (Stringer) getIndex(ER_CODE).getSingle(id);
  }

  Stringer getYearForID(int id)
  {
    if (id == 0) return null;
    if (id < 0)
      return (Stringer) getIndex(YEAR_CODE).data[(-id) - 1];
    return (Stringer) getIndex(YEAR_CODE).getSingle(id);
  }

  Stringer getBonusForID(int id)
  {
    if (id == 0) return null;
    if (id < 0)
      return (Stringer) getIndex(BONUS_CODE).data[(-id) - 1];
    return (Stringer) getIndex(BONUS_CODE).getSingle(id);
  }

  boolean isStandalone() { return standalone; }

  /**
   * Is an airing a "No Data" recording?
   *
   * @param testMe The {@link Airing} to check.
   * @return <code>true</code> if the airing is a "No Data" recording.
   */
  public boolean isNoShow(Airing testMe)
  {
    return testMe != null && testMe.showID == noShowID;
  }

  boolean isNoShow(Show s) { return noShow == s; }

  boolean isNoShow(int showID) { return noShow.id == showID; }

  long getLastMaintenance() { return lastMaintenance; }

  void clearMaintenanceTime()
  {
    Sage.putLong(prefsRoot + LAST_MAINTENANCE, lastMaintenance = 0);
    maintenanceNeeded = true;
  }

  private void addXctListener(XctSyncClient addMe)
  {
    listeners.addElement(addMe);
  }

  void removeXctListener(XctSyncClient removeMe)
  {
    listeners.remove(removeMe);
  }

  boolean hasListeners() { return !listeners.isEmpty(); }

  public int getMaxPendingClientXcts()
  {
    int max = 0;
    synchronized (listeners)
    {
      for (int i = 0; i < listeners.size(); i++)
      {
        max = Math.max(0, listeners.get(i).getPendingXctCount());
      }
    }
    return max;
  }

  private void distributeXct(byte[] xctData)
  {
    synchronized (listeners)
    {
      if (listeners.isEmpty()) return;
      // In case listeners get removed along the way
      XctSyncClient[] listData = listeners.toArray(new XctSyncClient[0]);
      SageTV.incrementQuanta();
      for (int i = 0; i < listData.length; i++)
      {
        try
        {
          listData[i].xctOut(xctData);
        }
        catch (Exception e)
        {
          System.out.println("Error communicating with client-2:" + e);
          e.printStackTrace();
        }
      }
    }
  }

  void xctIn(DataInput in, byte ver, Map<Integer, Integer> idMap)
  {
    try
    {
      byte lastOpcode = 0;
      do
      {
        lastOpcode = processXctFromStream(in, ver, -1, idMap, 0);
      } while (lastOpcode != XCTS_DONE);
    }
    catch (IOException e)
    {
      if (Sage.DBG)
      {
        System.out.println("WIZARD TRANSACTION ERROR:" + e);
        e.printStackTrace(System.out);
      }
    }
  }

  void sendDBThroughStream(Socket sake, OutputStream outStream, SageTVConnection addMe) throws IOException
  {
    // Suspend database writing while we send our current DB to clients so we don't block server operations
    // during this time period.
    clientIsSyncing = true;
    boolean didSuspend = false;
    synchronized (pendingWriteXcts)
    {
      if (!suspendWrite && dbout != null)
      {
        if (Sage.DBG) System.out.println("Suspending DB writes while we send the DB to the client...");
        suspendWrite = true;
        didSuspend = true;
      }
    }
    synchronized (outLock)
    {
      try
      {
        if (dbout == null)
        {
          TimeoutHandler.registerTimeout(15000, sake);
          outStream.write(0); // 32-bit int for 5
          outStream.write(0);
          outStream.write(0);
          outStream.write(5);
          outStream.write(XCTS_DONE);
          outStream.flush();
          TimeoutHandler.clearTimeout(sake);
          addXctListener(addMe);
          return;
        }
        long startFP = dbout.position();
        try
        {
          dbout.seek(4); // skip version # & WIZ.
          byte[] netBuf = new byte[65536];
          long dbLeft = dbout.length() - 4;
          if (Sage.DBG) System.out.println("Sending DB to client of size:" + dbLeft);
          // Since we are just transferring the whole file like this it is faster to have this optimization on
          // at this point.
          // 7-12-2016 JS: The newer implementation works out if a read could fit completely within
          // the buffer and based on that will either do a partial buffer + direct read, no buffer +
          // direct read or a fully buffered read. Since the default read buffer size is 32k and the
          // read length here is mostly 256k, it will trigger the same kind of optimization that
          // this enabled in the old class.
          //dbout.setOptimizeReadFully(true);
          while (dbLeft > 0)
          {
            int currRead = Math.min(netBuf.length, (int)dbLeft);
            dbout.readFully(netBuf, 0, currRead);
            TimeoutHandler.registerTimeout(60000, sake);
            outStream.write(netBuf, 0, currRead);
            TimeoutHandler.clearTimeout(sake);
            dbLeft -= currRead;
          }
        }
        finally
        {
          dbout.seek(startFP);
        }
      }
      finally
      {
        // Now send the transactions that occurred during the transmit of the DB to the client to just this client
        // since all other clients will have already received these. Also write them to the DB File as well.
        if (Sage.DBG && didSuspend) System.out.println("Logging " + pendingWriteXcts.size() + " DB xcts to file and sending them to the new client...");
        boolean clientFailed = false;
        int q = 0;
        ByteArrayOutputStream xctBuffer = new ByteArrayOutputStream(512);
        SageTVConnection.MyDataOutput doBuffer = new SageTVConnection.MyDataOutput(xctBuffer);
        while (didSuspend)
        {
          XctObject nextXct = null;
          synchronized (pendingWriteXcts)
          {
            if (pendingWriteXcts.isEmpty())
            {
              suspendWrite = false;
              pendingWriteXcts.notifyAll();
              break;
            }
            nextXct = pendingWriteXcts.remove(0);
          }
          if (Sage.DBG && didSuspend && ((q++ % 100) == 0)) System.out.println("Logging " + pendingWriteXcts.size() + " DB xcts to file and sending them to the new client...");

          xctBuffer.reset();
          doBuffer.writeByte(nextXct.xctType);
          doBuffer.writeByte(nextXct.objectType);
          try {
            acquireReadLock(nextXct.objectType);
            nextXct.obj.write(doBuffer, 0);
          } finally {
            releaseReadLock(nextXct.objectType);
          }
          dbout.writeInt(xctBuffer.size() + 4);
          byte[] xctData = xctBuffer.toByteArray();
          dbout.write(xctData);
          if (!clientFailed)
          {
            try
            {
              TimeoutHandler.registerTimeout(60000, sake);
              int len = xctData.length + 4;
              outStream.write((byte)((len >>> 24) & 0xFF));
              outStream.write((byte)((len >>> 16) & 0xFF));
              outStream.write((byte)((len >>> 8) & 0xFF));
              outStream.write((byte)(len & 0xFF));
              outStream.write(xctData);
              TimeoutHandler.clearTimeout(sake);
            }
            catch (Throwable t)
            {
              // We MUST proceed all the way through cleaning up the pending transactions and ignore network
              // errors sending this to the client
              if (Sage.DBG) System.out.println("ERROR sending pending DB xct to new client...but ignoring because we must process all of these...:" + t);
              t.printStackTrace();
              clientFailed = true;
            }
          }
          numUncompXcts++;
        }
        clientIsSyncing = false;
        synchronized (clientIsSyncingLock)
        {
          clientIsSyncingLock.notifyAll();
        }
      }
      TimeoutHandler.registerTimeout(60000, sake);
      outStream.write(0); // 32-bit int for 5
      outStream.write(0);
      outStream.write(0);
      outStream.write(5);
      outStream.write(XCTS_DONE);
      outStream.flush();
      TimeoutHandler.clearTimeout(sake);
      // Construct the listener queue for the connection before we add them as a xct listener
      // or otherwise socket delays on that connection can hang up the entire server
      // system until the replies come back from that client since we would then
      // try to serially send transactions to it.
      addMe.constructListenerQueue();
      addXctListener(addMe);
      if (Sage.DBG) System.out.println("DONE sending DB to the client and client is now added as a DB sync listener");
    }
  }

  public void setLineup(long providerID, Map lineupMap)
  {
    EPG.getInstance().setLineup(providerID, lineupMap);
  }

  public void setPhysicalLineup(long providerID, Map lineupMap)
  {
    EPG.getInstance().setPhysicalLineup(providerID, lineupMap);
  }

  public void setPhysicalOverride(long providerID, int stationID, String physChan)
  {
    EPG.getInstance().setPhysicalOverride(providerID, stationID, physChan);
  }

  public File getWidgetDBFile() { return widgetDBFile; }

  Show getNoShow() { return noShow; }

  private void updateLastModified(int updateMask)
  {
    if ((updateMask & DBObject.MEDIA_MASK_DVD) == DBObject.MEDIA_MASK_DVD)
      lastModifiedDVD = Sage.time();
    if ((updateMask & DBObject.MEDIA_MASK_BLURAY) == DBObject.MEDIA_MASK_BLURAY)
      lastModifiedBluRay = Sage.time();
    if ((updateMask & DBObject.MEDIA_MASK_MUSIC) == DBObject.MEDIA_MASK_MUSIC)
      lastModifiedMusic = Sage.time();
    if ((updateMask & DBObject.MEDIA_MASK_PICTURE) == DBObject.MEDIA_MASK_PICTURE)
      lastModifiedPicture = Sage.time();
    if ((updateMask & DBObject.MEDIA_MASK_TV) == DBObject.MEDIA_MASK_TV)
      lastModifiedTV = Sage.time();
    if ((updateMask & DBObject.MEDIA_MASK_VIDEO) == DBObject.MEDIA_MASK_VIDEO)
      lastModifiedVideo = Sage.time();
  }

  public void freeAllDBMemory()
  {
    while (performingMaintenance)
    {
      try{Thread.sleep(250);}catch(Exception e){}
      if (Sage.DBG) System.out.println("Waiting for DB maintenance to finish before unloading the database...");
    }
    if (Sage.DBG) System.out.println("Freeing all database tables...");
    disableDatabase = true;
    // Now empty the tables
    for (int i = 0; i < tables.length; i++)
    {
      Table t = tables[i];
      if (t == null) continue;
      t.num = 0;
      t.primary.data = new DBObject[0];
      for (int j = 0; j < t.others.length; j++)
        t.others[j].data = new DBObject[0];
    }
    if (Sage.DBG) System.out.println("Done freeing the database!");
  }

  private String[] removeNullAndEmptyEntries(String[] strarr)
  {
    if (strarr == null || strarr.length == 0)
      return strarr;
    int numValid = 0;
    for (int i = 0; i < strarr.length; i++)
    {
      if (strarr[i] != null && strarr[i].length() > 0)
        numValid++;
    }
    if (numValid == strarr.length) return strarr;
    String[] rv = new String[numValid];
    numValid = 0;
    for (int i = 0; i < strarr.length; i++)
    {
      if (strarr[i] != null && strarr[i].length() > 0)
      {
        rv[numValid++] = strarr[i];
      }
    }
    return rv;
  }

  public void compressDBIfNeeded()
  {
  }

  public void waitUntilDBClientSyncsComplete()
  {
    // We use this method during EPG updates or maintenance to stall those processes (outside of any locks)
    // while a client is connecting and synchronizing its DB with the server. If we don't do this then there will
    // be a pile of transactions we need to sync when the client is finishing up its connection and that may end
    // up taking an excessive amount of time to do.
    boolean didWait = false;
    while (clientIsSyncing)
    {
      synchronized (clientIsSyncingLock)
      {
        if (clientIsSyncing)
        {
          if (!didWait)
          {
            if (Sage.DBG) System.out.println("Server is waiting until client finishes loading the DB before DB updates continue...");
            didWait = true;
          }
          try{clientIsSyncingLock.wait(15000);}catch(InterruptedException e){}
        }
      }
    }
    if (didWait && Sage.DBG) System.out.println("DONE waiting for client to finish DB sync");
  }

  public boolean isDBLoading() {
    return loading;
  }

  public void setDBLoadState(boolean x) {
    if (!Sage.client) throw new IllegalArgumentException("Cannot call setDBLoadState on the server!");
    loading = x;
  }

  private byte version;
  private String prefsRoot;
  private SageDataFile dbout;
  private Carny god;
  private final Object outLock = new Object();
  private File dbFile;
  private File dbBackupFile;
  private File widgetDBFile;
  private int nextID;
  private boolean loading = false;
  private long lastModifiedMusic;
  private long lastModifiedVideo;
  private long lastModifiedPicture;
  private long lastModifiedTV;
  private long lastModifiedDVD;
  private long lastModifiedBluRay;
  private Table[] tables;
  private boolean standalone;
  private int noShowID;
  private Show noShow;
  private long lastMaintenance;
  private long noDataMaxLen;
  private long noDataMaxRuleDur;
  private boolean flashOverflow;
  private boolean maintenanceNeeded;
  private int numUncompXcts;

  private SageTVConnection.MyDataOutput cachedDOS;
  private ByteArrayOutputStream cachedBAOS;

  private boolean disableDatabase;
  private boolean performingMaintenance;
  private int dbErrorsLogged;

  private ThreadLocal<Channel> lookupChan = new ThreadLocal<Channel>() {
    protected synchronized Channel initialValue() {
      return new Channel(-1);
    }
  };

  private Vector<XctSyncClient> listeners;

  private final Vector<XctObject> pendingWriteXcts;
  private boolean suspendWrite;

  private boolean clientIsSyncing;
  private final Object clientIsSyncingLock = new Object();

  private boolean primed;

  public static interface XctSyncClient
  {
    public void xctOut(byte[] xctData) throws IOException;
    public int getPendingXctCount();
  }

  private static class XctObject
  {
    public XctObject(byte xctType, byte objectType, DBObject obj)
    {
      this.xctType = xctType;
      this.objectType = objectType;
      this.obj = obj;
    }
    byte xctType;
    byte objectType;
    DBObject obj;
  }

  public Vector<Airing> searchFieldsNTE(String nteString, boolean title, boolean episode,
      boolean description, boolean person, boolean category, boolean rated, boolean extendedRatings, boolean year,
      boolean misc, int mediaMask, boolean wholeWord) {
    if(Sage.DBG) System.out.println("searchFieldsNTE -> searchWithinFields");
    StringMatchUtils.updateNteCharsFromProperties();
    return searchWithinFields(nteString, false, title, episode, description, person, category,
        rated, extendedRatings, year, misc, mediaMask, wholeWord);
  }

  public String[] searchForTitlesNTE(String nteString, int mediaMask) {
    nteString = nteString.toLowerCase();
    StringMatchUtils.updateNteCharsFromProperties();

    Set<String> okTitles = new HashSet<String>();
    Index idx = getIndex(TITLE_CODE);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);
    try {
      idx.table.acquireReadLock();
      for (int i = 0; i < idx.table.num; i++)
      {
        if ((idx.data[i].getMediaMask() & mediaMask) == 0)
          continue;
        if (StringMatchUtils.wordMatchesNte(
            ((Stringer) idx.data[i]).name,nteString))
        {
          okTitles.add(((Stringer) idx.data[i]).name);
          if (okTitles.size() >= MAX_SEARCH_RESULTS)
            break;
        }
      }
    } finally {
      idx.table.releaseReadLock();
    }
    String[] rv = okTitles.toArray(Pooler.EMPTY_STRING_ARRAY);
    Arrays.sort(rv);
    return rv;
  }

  public Person[] searchForPeopleNTE(String str, int mediaMask) {
    if(Sage.DBG) System.out.println("searchForPeopleNTE -> searchForPeople");
    StringMatchUtils.updateNteCharsFromProperties();
    return searchForPeople(str, mediaMask);
  }

  //
  // Lucene index for Sage
  //
  // 07/12/2012 Codefu: I should really look at moving this to its own class and subclassing
  // new indexes as they are needed.
  // 07/13/2012 Codefu: Add flag to turn off lucene, even on fat
  static boolean disableLucene = Sage.client ? true : (Sage.getBoolean("wizard/disable_lucene", false) || Sage.getBoolean("db_perf_analysis", false));
  static class LuceneIndex {
    static final Boolean diskIndex = Sage.getBoolean("wizard/lucene_disk", false); // set to TRUE for disk testing on a desktop
    static final Boolean diskIndexRunning = diskIndex && Sage.getBoolean("wizard/lucene_running_disk", false);
    static final String INDEX_BASE_PATH = "/rw/";
    static final String INDEX_RUNNING_PATH = "lucene-run/";
    static final String INDEX_SAVE_PATH = "lucene/";
    static final String INDEX_WRITE_COMPLETED = "SageFinishedMarker3";
    static final int SHOW_INDEX = 0x1;
    static final int PERSON_INDEX = 0x2;
    static final double INDEX_RAM_BUFFER_SIZE = 10;
    String rootSnapshotPath;
    String rootRunningPath;
    private final Object lock = new Object();
    public Object getLock() {
      return lock;
    }

    private Directory index;
    private IndexReader reader;
    private IndexSearcher searcher;
    private IndexWriter writer;
    private String name;
    private final byte type;
    private File directorySnapshot;
    private File directoryRunning;
    boolean initializedIndex;

    // Allows the index to queue up additions and deletions.
    // Additions are lucene Documents, deletions are DB id's
    private final Object indexTransactionsLock = new Object();
    private List<Object> indexTransactions = new ArrayList<Object>();
    public Object getTransactionLock() {
      return indexTransactionsLock;
    }

    public int insertions;
    public int insertionTime;

    static int indexCount = 0;

    boolean needsReset = false;

    int lastCommittedWrite = 0;

    //Analyzer analyzer = new StandardAnalyzer(org.apache.lucene.util.Version.LUCENE_36, Collections.emptySet());
    Analyzer analyzer = new LuceneWhitespaceInsensitiveAnalyzer(org.apache.lucene.util.Version.LUCENE_36);
    //Analyzer analyzer = new WhitespaceAnalyzer(org.apache.lucene.util.Version.LUCENE_36);

    public LuceneIndex(byte type, String name, String indexRootPath) {
      this.type = type;
      if(disableLucene) { return; }
      int count = indexCount++;
      if(name == null || name.trim().equals("")) {
        this.name = "noname-" + count + "-" + type;
      } else {
        this.name = name;
      }
      if(indexRootPath == null) {
        rootSnapshotPath = Sage.getPath("cache");
      } else {
        rootSnapshotPath = indexRootPath;
      }
      if(!rootSnapshotPath.endsWith(System.getProperty("file.separator"))) {
        rootSnapshotPath += System.getProperty("file.separator");
      }
      rootRunningPath = rootSnapshotPath + INDEX_RUNNING_PATH;
      rootSnapshotPath += INDEX_SAVE_PATH;
      if (Sage.DBG) System.out.println("Lucene index(" + this.name + "): rooted at "
          + rootSnapshotPath + " running at " + rootRunningPath + " DiskIndex:" + diskIndex
          + " RunningDiskIndex:" + diskIndexRunning);
      try {
        // First, try to pre-load the index with data from the disk!
        directorySnapshot =  new File(rootSnapshotPath + this.name + "/");
        directoryRunning = new File(rootRunningPath + this.name + "/");
        Directory diskDir = null;
        if(diskIndex) {
          if(!directorySnapshot.exists() && !directorySnapshot.mkdirs()) {
            disableLucene = true;
            System.out.println("Error creating directory or its parents: " + directorySnapshot.getPath());
            return;
          }
          if(diskIndexRunning && !directoryRunning.exists() && !directoryRunning.mkdirs()) {
            disableLucene = true;
            System.out.println("Error creating directory or its parents: " + directoryRunning.getPath());
            return;
          }
          diskDir = new MMapDirectory(directorySnapshot, NoLockFactory.getNoLockFactory());
          if(diskDir != null && diskDir.fileExists(INDEX_WRITE_COMPLETED)) {
            // Clear out the last running index as it is probably inconsistent and reload from our
            // last saved snapshot
            if(diskIndexRunning) {
              IOUtils.deleteDirectory(directoryRunning);
              index = MMapDirectory.open(directoryRunning, NoLockFactory.getNoLockFactory());
            } else {
              index = new RAMDirectory(diskDir);
            }
            IndexWriterConfig iwc = new IndexWriterConfig(
                org.apache.lucene.util.Version.LUCENE_36,
                analyzer).setOpenMode(OpenMode.CREATE);
            // If the runnign index has a backer; copy over the snapshot index.
            if(diskIndexRunning) {
              // note; allow for merging here since we want to do some optimizations at boot.
              writer = new IndexWriter(index, iwc);
              long start = Sage.eventTime();
              writer.addIndexes(new Directory[] {diskDir});
              start = Sage.eventTime() - start;
              System.out.println("Lucene index(" + name + ") read from saved: " + start);
              writer.commit();
              writer.close();
            }
          } else {
            if(Sage.DBG) System.out.println("Lucene index(" + this.name + "): missing write marker, assuming corrupt index" + rootSnapshotPath);
            if(diskIndexRunning) {
              IOUtils.deleteDirectory(directoryRunning);
              index = MMapDirectory.open(directoryRunning, NoLockFactory.getNoLockFactory());
            } else {
              index = new RAMDirectory();
            }
          }
        } else {
          // Desktop situation; only run a ram index with no backing.
          index = new RAMDirectory();
        }
        if(diskDir != null) {
          diskDir.close();
        }

        IndexWriterConfig iwc = new IndexWriterConfig(
            org.apache.lucene.util.Version.LUCENE_36, analyzer).setOpenMode(
                OpenMode.CREATE_OR_APPEND);
        if(diskIndexRunning) {
          iwc.setRAMBufferSizeMB(INDEX_RAM_BUFFER_SIZE)
          .setMergePolicy(NoMergePolicy.NO_COMPOUND_FILES) // TESTING
          .setMergeScheduler(NoMergeScheduler.INSTANCE); // TETING
        }
        writer = new IndexWriter(index, iwc);
        initializedIndex = (writer.numDocs() > 0) ? true : false;
        long start = Sage.time();
        reader = IndexReader.open(getWriter(), true); /* true: apply all deletes (perfhit) */
        start = Sage.time() - start;
        if(Sage.DBG) System.out.println("Lucene index(" + name + ") reader opened in " + start);
        searcher = new IndexSearcher(reader);

        if(Sage.DBG) System.out.println("Lucene index(" + this.name + ") docs: " + writer.numDocs());
      } catch (CorruptIndexException e) {
        // TODO(codefu): RECOVER RECOVER RECOVER! ;)
        System.out.println("Corrupt index for " + this.name);
        needsReset = true;
        e.printStackTrace();
      } catch (LockObtainFailedException e) {
        needsReset = true;
        e.printStackTrace();
      } catch (IOException e) {
        needsReset = true;
        System.out.println("Openning disk index failed! " + e);
        e.printStackTrace();
      }
    }

    private void reloadIndex() {
      Directory diskDir;
      try {
        synchronized (getLock()) {
          initializedIndex = false;
          if(!diskIndex) {
            resetIndex();
            return;
          }
          index = new MMapDirectory(directorySnapshot, NoLockFactory.getNoLockFactory());
          IndexWriterConfig iwc = new IndexWriterConfig(org.apache.lucene.util.Version.LUCENE_36,
              analyzer).setOpenMode(OpenMode.CREATE_OR_APPEND);
          if (diskIndexRunning) {
            iwc.setRAMBufferSizeMB(INDEX_RAM_BUFFER_SIZE)
            .setMergePolicy(NoMergePolicy.NO_COMPOUND_FILES) // TESTING
            .setMergeScheduler(NoMergeScheduler.INSTANCE); // TETING
          }
          writer = new IndexWriter(index, iwc);
          initializedIndex = (writer.numDocs() > 0) ? true : false;
          reader = IndexReader.open(getWriter(), true); /* true: apply all deletes (perfhit) */
          searcher = new IndexSearcher(reader);
          needsReset = false;
        }
      } catch (IOException e) {
        System.out.println("Execption reloading index from disk; assuming corrupt. e = " + e);
        e.printStackTrace();
        resetIndex();
      }
    }

    /**
     * BFN: This should only be called when loading the wizard database!
     */
    public void resetIndex() {
      // reset this to empty.
      try {
        synchronized (getLock()) {
          try {
            if (writer != null) {
              writer.rollback();
              writer.close();
            }
          } catch (Throwable ignore) {
          }
          try {
            if (index != null) index.close();
          } catch (Throwable ignore) {
          }
          if(diskIndexRunning) {
            IOUtils.deleteDirectory(directoryRunning);
            index = MMapDirectory.open(directoryRunning, NoLockFactory.getNoLockFactory());
          } else {
            index = new RAMDirectory();
          }
          IndexWriterConfig iwc = new IndexWriterConfig(
              org.apache.lucene.util.Version.LUCENE_36,
              analyzer).setOpenMode(OpenMode.CREATE);
          if (diskIndexRunning) {
            iwc.setRAMBufferSizeMB(INDEX_RAM_BUFFER_SIZE)
            .setMergePolicy(NoMergePolicy.NO_COMPOUND_FILES) // TESTING
            .setMergeScheduler(NoMergeScheduler.INSTANCE); // TETING
          }
          writer = new IndexWriter(index, iwc);
          reader = IndexReader.open(getWriter(), true); // true: apply all deletes (perfhit)
          initializedIndex = false;
          searcher = new IndexSearcher(reader);
          insertions = 0;
        }
        if(Sage.DBG) System.out.println("Index(" + this.name + ") reset");
      } catch (CorruptIndexException e) {
        disableLucene = true;
        // Should never happen...
        System.out.println("BUG: Execption restting index e = " + e);
        e.printStackTrace();
      } catch (IOException e) {
        disableLucene = true;
        // Should never happen...
        System.out.println("BUG: Execption restting index e = " + e);
        e.printStackTrace();
      }
      needsReset = false;
    }

    public Directory getDirectory() {
      return index;
    }

    public IndexReader getReader() {
      return reader;
    }

    public IndexSearcher getSearcher() {
      return searcher;
    }

    SnapshotDeletionPolicy sdp;
    IndexCommit commits;
    int snapCount = 0;

    public void reopenReaderIfNeeded() {
      synchronized (getLock()) {
        IndexReader newReader;
        try {
          newReader = IndexReader.openIfChanged(reader);
          if (newReader != null) {
            reader.close();
            reader = newReader;
            searcher.close();
            searcher = new IndexSearcher(reader);
          }
          lastCommittedWrite = insertions;
        } catch (IOException e) {
          System.out.println("BUG: committing a ram index; should never happen: " + e);
          e.printStackTrace();
        }
      }
    }

    /**
     * Flush any pending transactions and re-open the reader.
     */
    public void flush() {
      try {
        synchronized (getLock()) {
          if ( writer != null) {
            writer.commit();
          }
          if ( reader != null ){
            reopenReaderIfNeeded();
          }
        }
      } catch (CorruptIndexException e) {
        // really? Throws from Open, which should have been a empty database.
        System.out.println("Index for " + name + " corrupt at flush..." + e);
        needsReset = true;
        e.printStackTrace();
      } catch (IOException e) {
        // really? Disk going bad.  Doesn't mater what we do from here on out. Keep running with the
        // current ram index though.
        System.out.println("Index for " + name + " ioexception at flush..." + e);
        e.printStackTrace();
      }
    }

    /**
     * Save an atomic copy of the index to match the Wizard DB to be loaded next boot.
     * Note; if the box is reset before this completes, the index will be lost and require
     * a rebuild.
     */
    public void snapshot() {
      int count = snapCount++;
      if(Sage.DBG) System.out.println("Snapshot index " + name + " snap-count: " + count);
      try {
        synchronized (getLock()) {
          flush();
          // Desktop machines are 100x faster at handling the index, so we're ram only.
          if(!diskIndex) return;

          // Remove the old index and re-open it to copy over the running index.
          IOUtils.deleteDirectory(directorySnapshot);
          Directory tmp = FSDirectory.open(directorySnapshot, NoLockFactory.getNoLockFactory());
          // Note: Spend a little time optimizing the index by leaving in the default merger.
          IndexWriterConfig tmpIwc = new IndexWriterConfig(
              org.apache.lucene.util.Version.LUCENE_36,
              analyzer).setOpenMode(OpenMode.CREATE);
          // If we later wish to push this off till boot time, make sure to uncomment these lines
          // if (!diskIndexRunning)
          // tmpIwc.setMergePolicy(NoMergePolicy.NO_COMPOUND_FILES).setMergeScheduler(NoMergeScheduler.INSTANCE);

          IndexWriter tmpWriter = new IndexWriter(tmp, tmpIwc);
          tmpWriter.addIndexes(new Directory[] {index});
          // Write the tombstone marker signaling the write was successfull
          tmp.createOutput(INDEX_WRITE_COMPLETED);
          tmpWriter.close();
          long start = Sage.time();
          tmp.close();
          start = Sage.time() - start;
          if(Sage.DBG) System.out.println("Snapshot-index-close-time: " + start);
          if(index instanceof RAMDirectory) {
            // Finally reset all the writers / readers / etc.
            // TODO(codefu): Figure out what I was thinking about closing and re-openning stuff.
            // 2013-03-14(codefu): Probably because writing out the index with merges might optimize.
            //     but this is a ram index, so who cares?
            // reloadIndex();
          }
        }
        if(Sage.DBG) System.out.println("Snapshot index " + name + " done");
      } catch (CorruptIndexException e) {
        // really? Throws from Open, which should have been a empty database.
        System.out.println("Index for " + name + " corrupt at snapshot..." + e);
        needsReset = true;
        e.printStackTrace();
      } catch (IOException e) {
        // really? Disk going bad.  Doesn't mater what we do from here on out. Keep running with the
        // current ram index though.
        System.out.println("Index for " + name + " ioexception at snapshot..." + e);
        e.printStackTrace();
      }
    }

    public IndexWriter getWriter() {
      return writer;
    }

    List<Object> swapIndexWork(List<Object> empty) {
      List<Object> work;
      synchronized (getTransactionLock()) {
        work = indexTransactions;
        indexTransactions = empty;
      }
      return work;
    }

    /**
     * In a bad situation, we need to rebuild the indexes on the fly.
     * Side-effect: swap the work queues
     * @param work queue to load up.
     * @param tableCode for the wizard table to load
     */
    private List<Object> reloadLuceneWorkQueue(List<Object> work) {
      // Elsewhere when a show is inserted, the wizard lock is grabbed first; do that here
      // to prevent deadlock.
      List<Object> empty;
      work.clear();
      try {
        Wizard.getInstance().acquireReadLock(this.type);
        synchronized (getTransactionLock()) {
          resetIndex();
          Table table = Wizard.getInstance().getTable(this.type);
          Index showIndex = table.getIndex((byte) 0);
          for(Object o : showIndex.data) {
            if(o != null) {
              work.add(o);
            }
          }
          // Free all current work (we should have it) from the DB and then issue a snapshot
          indexTransactions.clear();
          empty = indexTransactions;
          indexTransactions = work;
          if(work.size() > 0) {
            work.add(0);
          }
        }
        return empty;
      } finally {
        Wizard.getInstance().releaseReadLock(this.type);
      }
    }
  }

  LuceneIndex showIndex;
  LuceneIndex personIndex;

  boolean indexShowTransactionRunning = false;
  boolean indexPersonTransactionRunning = false;
  static final int LUCENE_PERSON_INSERTION_THROTTLE_TIME = 10; // 10x slow down
  static final int LUCENE_SHOW_INSERTION_THROTTLE_TIME = 25; // 2x slowdown

  Runnable indexShowTransactionTask = new Runnable() {
    public void run() {
      // NOTE(codefu): Keep these instances around (we can re-use them). No need to perform 200k
      // re-allocations.
      Document doc = new Document();
      final String emptyString = "";
      Field id = new Field("id", emptyString, Field.Store.YES, Field.Index.ANALYZED);
      Field title = new Field("title", emptyString, Field.Store.NO, Field.Index.ANALYZED);
      Field desc = new Field("desc", emptyString, Field.Store.NO, Field.Index.ANALYZED);
      Field ep = new Field("ep", emptyString, Field.Store.NO, Field.Index.ANALYZED);
      Field year = new Field("year", emptyString, Field.Store.NO, Field.Index.ANALYZED);
      Field rated = new Field("rated", emptyString, Field.Store.NO, Field.Index.ANALYZED);
      Field peeps = new Field("peep", emptyString, Field.Store.NO, Field.Index.ANALYZED);
      Field categories = new Field("cat", emptyString, Field.Store.NO, Field.Index.ANALYZED);
      Field ers = new Field("ers", emptyString, Field.Store.NO, Field.Index.ANALYZED);
      Field bonuses = new Field("bonus", emptyString, Field.Store.NO, Field.Index.ANALYZED);
      doc.add(id);
      doc.add(title);
      doc.add(desc);
      doc.add(ep);
      doc.add(year);
      doc.add(rated);
      doc.add(peeps);
      doc.add(categories);
      doc.add(ers);
      doc.add(bonuses);

      List<Object> work = new ArrayList<Object>();

      while (true) {
        LuceneIndex index = getShowIndex();
        work = index.swapIndexWork(work);
        try {
          synchronized (index.getTransactionLock()) {
            if (work.size() == 0) {
              index.getTransactionLock().wait();
              continue;
            }
          }
          // Now perform what actions are requested of us.
          int transactionCount = work.size();
          int deletions = 0;
          synchronized (index.getLock()) { // allows others to get lock between loops
            IndexWriter writer = index.getWriter();
            int count = 0;
            for (Object trans : work) {
              if (trans instanceof Integer) {
                // DELETE DOCUMENTS MATCHING THIS ID
                Integer delId = (Integer) trans;
                TermQuery q = new TermQuery(new Term("id", delId.toString()));
                try {
                  // TODO(codefu): investigate faster ways to store, index, and delete shows by ID.
                  // I started off with NumericFields, but this so far hasn't proven faster.
                  // NumericRangeQuery<Integer> newIntRange = NumericRangeQuery.newIntRange("id",
                  // id, id, true, true);
                  // Query q = newIntRange.rewrite(reader);
                  writer.deleteDocuments(q);
                  // TODO(codefu): possibly set a countdown timer and commit the writer for
                  // performance.
                  deletions++;
                } catch (Exception e) {
                  if (Sage.DBG) System.out.println(
                      "Exception while trying to delete show[" + delId + "] from index; " + e);
                  e.printStackTrace();
                }
              } else if (trans instanceof Show) {
                // INSERT THIS SHOW
                Show s = (Show) trans;
                long start = Sage.time();

                id.setValue(Integer.toString(s.getID()));
                title.setValue(s.getTitle());
                desc.setValue(s.getDesc());
                ep.setValue(s.getEpisodeName());
                year.setValue(s.getYear());
                rated.setValue(s.getRated());

                StringBuffer buff = new StringBuffer();
                if (s.people.length > 0) {
                  for (Person peep : s.people) {
                    if (peep == null) continue;
                    buff.append(peep.name);
                    buff.append(" ");
                  }
                }
                peeps.setValue((buff.length() > 0) ? buff.toString() : emptyString);

                buff.setLength(0);
                if (s.categories.length > 0) {
                  for (Stringer cat : s.categories) {
                    if (cat == null) continue;
                    buff.append(cat.name);
                    buff.append(" ");
                  }
                }
                categories.setValue((buff.length() > 0) ? buff.toString() : emptyString);

                buff.setLength(0);
                if (s.ers.length > 0) {
                  for (Stringer er : s.ers) {
                    if (er == null) continue;
                    buff.append(er.name);
                    buff.append(" ");
                  }
                }
                ers.setValue((buff.length() > 0) ? buff.toString() : emptyString);

                buff.setLength(0);
                if (s.bonuses.length > 0) {
                  for (Stringer bonus : s.ers) {
                    if (bonus == null) continue;
                    buff.append(bonus.name);
                    buff.append(" ");
                  }
                }
                bonuses.setValue((buff.length() > 0) ? buff.toString() : emptyString);

                try {
                  writer.addDocument(doc);
                } catch (CorruptIndexException e) {
                  String sId = doc.get("id");
                  String sTitle = doc.get("title");
                  if (Sage.DBG) {
                    System.out.println("CorruptIndexException while trying to insert show[" + sId
                        + ", " + sTitle + "] into index; " + e);
                    e.printStackTrace();
                  }
                } catch (IOException e) {
                  String sId = doc.get("id");
                  String sTitle = doc.get("title");
                  System.out.println("IOException while trying to insert show[" + sId + ", "
                      + sTitle + "] into index; " + e);
                  e.printStackTrace();
                }
                start = Sage.time() - start;
                index.insertions++;
                index.insertionTime += start;
              } else if (trans == null) {
                // SNAPSHOT THIS INDEX
                index.snapshot();
                deletions = 0;
                index.initializedIndex = true;
              }
              if(Sage.DBG && ++count % 1000 == 0 && count > 0) {
                System.out.println(String.format(
                    "%3.2f%% / %d work queue, %d inserted @ %.2f ms/show avg [tot:%dms]",
                    ((float) (count * 100) / transactionCount), transactionCount, index.insertions,
                    (float) index.insertionTime / index.insertions, index.insertionTime));
              }
            }
            if (deletions > 0) {
              index.reopenReaderIfNeeded();
            }
          }
          if (Sage.DBG && transactionCount > 1000) {
            System.out.println(String.format(
                "100%% work queue, %d insertions @ %.2f ms/show [tot:%dms]", index.insertions,
                (float) index.insertionTime / index.insertions, index.insertionTime));
          }
          // Clear and get ready to page out for more work
          work.clear();
        } catch (InterruptedException ignore) {
        } catch (Throwable e) {
          if(Sage.DBG) {
            System.out.println("BUG: Lucene transacation task experienced exception: " + e + ".  This (more than likely) requires a full rebuild.");
            e.printStackTrace();
          }
          // Clear all current (junk) work, add all shows in the DB and start over.
          work = index.reloadLuceneWorkQueue(work);
        }
      }
    }
  };



  Runnable indexPersonTransactionTask = new Runnable() {
    public void run() {
      // NOTE(codefu): Keep these instances around (we can re-use them). No need to perform 200k
      // re-allocations.
      Document doc = new Document();
      final String emptyString = "";
      Field id = new Field("id", emptyString, Field.Store.YES, Field.Index.ANALYZED);
      Field name = new Field("name", emptyString, Field.Store.NO, Field.Index.ANALYZED);
      doc.add(id);
      doc.add(name);

      List<Object> work = new ArrayList<Object>();

      while (true) {
        LuceneIndex index = getPersonIndex();
        work = index.swapIndexWork(work);
        try {
          synchronized (index.getTransactionLock()) {
            if (work.size() == 0) {
              index.getTransactionLock().wait();
              continue;
            }
          }
          // Now perform what actions are requested of us.
          int transactionCount = work.size();
          int deletions = 0;
          synchronized (index.getLock()) {
            IndexWriter writer = index.getWriter();
            int count = 0;
            for (Object trans : work) {
              if (trans instanceof Integer) {
                // DELETE DOCUMENTS MATCHING THIS ID
                Integer delId = (Integer) trans;
                TermQuery q = new TermQuery(new Term("id", delId.toString()));
                deletions++;
                try {
                  writer.deleteDocuments(q);
                } catch (Exception e) {
                  if (Sage.DBG) System.out.println(
                      "Exception while trying to delete person[" + delId + "] from index; " + e);
                  e.printStackTrace();
                }
              } else if (trans instanceof Person) {
                Person perp = (Person) trans;
                long start = Sage.time();
                id.setValue(Integer.toString(perp.id));
                name.setValue(perp.getName());
                try {
                  writer.addDocument(doc);
                } catch (Exception e) {
                  if (Sage.DBG) {
                    String pId = doc.get("id");
                    String pName = doc.get("name");
                    System.out.println("Exception while trying to insert person[" + pId + ", "
                        + pName + "] into index; " + e);
                    e.printStackTrace();
                  }
                }
                start = Sage.time() - start;
                index.insertions++;
                index.insertionTime += start;
              } else if (trans == null) {
                index.snapshot();
                deletions = 0;
                index.initializedIndex = true;
              }

              if(Sage.DBG && ++count % 1000 == 0 && count > 0) {
                System.out.println(String.format(
                    "%3.2f%% / %d work queue, %d inserted @ %.2f ms/peep avg [tot:%dms]",
                    ((float) (count * 100) / transactionCount), transactionCount, index.insertions,
                    (float) index.insertionTime / index.insertions, index.insertionTime));
              }
            }
            if (deletions > 0) {
              index.reopenReaderIfNeeded();
            }
          }
          if(Sage.DBG && transactionCount > 1000) {
            System.out.println(String.format(
                "100%% work queue, %d insertions @ %.2f ms/peep [tot:%dms]", index.insertions,
                (float) index.insertionTime / index.insertions, index.insertionTime));
          }
          // Clear and get ready to page out for more work
          work.clear();
        } catch (InterruptedException ignore) {
        } catch (Throwable e) {
          if(Sage.DBG) {
            System.out.println("BUG: Lucene transacation task experienced exception: " + e + ".  This (more than likely) requires a full rebuild.");
            e.printStackTrace();
          }
          // Clear all current (junk) work, add all shows in the DB and start over.
          work = index.reloadLuceneWorkQueue(work);
        }
      }
    }
  };

  public LuceneIndex getShowIndex() {
    if(disableLucene) { return null; }
    if(showIndex == null || !indexShowTransactionRunning) {
      synchronized (this) {
        if(showIndex == null) {
          showIndex = new LuceneIndex(SHOW_CODE, "show", LuceneIndex.INDEX_BASE_PATH);
          if(showIndex.needsReset) {
            showIndex.reloadIndex();
          }
        }
        if(!indexShowTransactionRunning) {
          indexShowTransactionRunning = true;
          Pooler.execute(indexShowTransactionTask, "LuceneShowTransactionTask", Thread.MIN_PRIORITY);
        }
      }
    }
    return showIndex;
  }

  public LuceneIndex getPersonIndex() {
    if(disableLucene) { return null; }
    if(personIndex == null || !indexPersonTransactionRunning) {
      synchronized (this) {
        if(personIndex == null) {
          personIndex = new LuceneIndex(PEOPLE_CODE, "person", LuceneIndex.INDEX_BASE_PATH);
          if(personIndex.needsReset) {
            personIndex.reloadIndex();
          }
        }
        if(!indexPersonTransactionRunning) {
          indexPersonTransactionRunning = true;
          Pooler.execute(indexPersonTransactionTask, "LucenePersonTransactionTask", Thread.MIN_PRIORITY);
        }
      }
    }
    return personIndex;
  }

  void addShowToLucene(Show s) {
    if(disableLucene) { return; }
    LuceneIndex index = getShowIndex();
    synchronized (index.getTransactionLock()) {
      index.indexTransactions.add(s);
      index.getTransactionLock().notifyAll();
    }
  }

  void addPersonToLucene(Person perp) {
    if(disableLucene) { return; }
    LuceneIndex index = getPersonIndex();
    synchronized (index.getTransactionLock()) {
      index.indexTransactions.add(perp);
      index.getTransactionLock().notifyAll();
    }
  }

  void deleteShowFromLucene(Show s) {
    if(disableLucene) { return; }
    LuceneIndex index = getShowIndex();
    synchronized (index.getTransactionLock()) {
      index.indexTransactions.add(s.getID());
      index.getTransactionLock().notifyAll();
    }
  }

  void deletePersonFromLucene(Person p) {
    if(disableLucene) { return; }
    LuceneIndex index = getPersonIndex();
    synchronized (index.getTransactionLock()) {
      index.indexTransactions.add(p.getID());
      index.getTransactionLock().notifyAll();
    }
  }

  static class SearchTerm {
    final int flag;
    final String field;
    SearchTerm(int flag, String field) {
      this.flag = flag;
      this.field = field;
    }
  }

  static final int TITLE_SEARCH = 0x1;
  static final int PERSON_SEARCH = 0x2;
  static final int EPISODE_SEARCH = 0x4;
  static final int DESCRIPTION_SEARCH = 0x8;
  static final int CATEGORY_SEARCH = 0x10;
  static final int BONUS_SEARCH = 0x20;
  static final int YEAR_SEARCH = 0x40;
  static final int RATED_SEARCH = 0x80;
  static final int EXTENDED_RATINGS_SEARCH = 0x100;
  static final int NAME_SEARCH = 0x200;
  static final int WHOLE_WORD_SEARCH = 0x400;

  static final int SIMPLE_ALL_SEARCH = TITLE_SEARCH | PERSON_SEARCH | EPISODE_SEARCH
      | DESCRIPTION_SEARCH | CATEGORY_SEARCH | BONUS_SEARCH | YEAR_SEARCH | RATED_SEARCH
      | EXTENDED_RATINGS_SEARCH;

  static final private SearchTerm[] terms = new SearchTerm[] {
    new SearchTerm(TITLE_SEARCH, "title"),
    new SearchTerm(PERSON_SEARCH, "peep"),
    new SearchTerm(EPISODE_SEARCH, "ep"),
    new SearchTerm(DESCRIPTION_SEARCH, "desc"),
    new SearchTerm(CATEGORY_SEARCH, "cat"),
    new SearchTerm(BONUS_SEARCH, "bonus"),
    new SearchTerm(YEAR_SEARCH, "year"),
    new SearchTerm(RATED_SEARCH, "rated"),
    new SearchTerm(EXTENDED_RATINGS_SEARCH, "ers"),
    new SearchTerm(NAME_SEARCH, "name"),
  };

  Show[] searchShowsByKeyword(String keyword) {
    boolean titleOnly = keyword.startsWith("TITLE:");
    String currKeyword = titleOnly ? keyword.substring("TITLE:".length()).trim() : keyword;
    return searchShowsByKeyword(currKeyword, (titleOnly ? TITLE_SEARCH : SIMPLE_ALL_SEARCH) | WHOLE_WORD_SEARCH);
  }

  BooleanQuery generateQueryTerms(String keyword, List<String> fields, int searchFlags) {
    int index = 0;
    int end;
    keyword = keyword.trim().toLowerCase();
    boolean wholeWordMatch = (searchFlags & WHOLE_WORD_SEARCH) == WHOLE_WORD_SEARCH;

    List<BooleanQuery> keywordQueries = new ArrayList<BooleanQuery>(4 /*hard coded*/);
    final String[] words = keyword.split("\\s+");
    for(String word : words) {
      BooleanQuery thisQuery = new BooleanQuery();
      keywordQueries.add(thisQuery);
      for(String field : fields) {
        if((searchFlags & WHOLE_WORD_SEARCH) == WHOLE_WORD_SEARCH) {
          // Match the whole term and nothing but the term.
          thisQuery.add(new TermQuery(new Term(field, word)), Occur.SHOULD);
        } else {
          // Match Term*
          thisQuery.add(new PrefixQuery(new Term(field, word)), Occur.SHOULD);
        }
      }
    }

    BooleanQuery bq = new  BooleanQuery();
    for(BooleanQuery field : keywordQueries) {
      bq.add(field, Occur.MUST);
    }
    return bq;
  }

  static final String wordMatchAll = "* ";
  Show[] searchShowsByKeyword(String keyword, int searchFlags) {
    if (disableLucene || keyword.length() < 1) {
      return Pooler.EMPTY_SHOW_ARRAY;
    }

    List<String> fields = new ArrayList<String>();
    for(SearchTerm term : terms) {
      if((term.flag & searchFlags) == term.flag) {
        fields.add(term.field);
      }
    }
    Query query = generateQueryTerms(keyword, fields, searchFlags);
    System.out.println("Keyword Terms: " + query);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);

    try {
      LuceneIndex searchIndex = getShowIndex();
      IndexReader reader = searchIndex.getReader();
      IndexSearcher searcher = searchIndex.getSearcher();

      TopDocs search = searcher.search(query, MAX_SEARCH_RESULTS);
      Show[] shows = new Show[search.scoreDocs.length];
      for (int i = 0; i < search.scoreDocs.length; i++) {
        Document doc = searcher.doc(search.scoreDocs[i].doc);
        int showID = Integer.parseInt(doc.getFieldable("id").stringValue());
        shows[i] = getShowForID(showID);
      }
      return shows;
    } catch( Exception e) {
      if (Sage.DBG) System.out.println("Bad things while searching: " + e);
      e.printStackTrace();
    }
    return Pooler.EMPTY_SHOW_ARRAY;
  }

  static final List<String> nameFieldList = Arrays.asList(new String[] {"name"});
  Person[] searchPeople(String keyword) {
    if (disableLucene || keyword.length() < 1) {
      return Pooler.EMPTY_PERSON_ARRAY;
    }

    BooleanQuery query = generateQueryTerms(keyword, nameFieldList, 0);
    System.out.println("People Terms: " + query);
    int MAX_SEARCH_RESULTS = Sage.getInt("wizard/max_search_results", 1000);

    try {
      LuceneIndex searchIndex = getPersonIndex();
      IndexReader reader = searchIndex.getReader();
      IndexSearcher searcher = searchIndex.getSearcher();

      TopDocs search = searcher.search(query, MAX_SEARCH_RESULTS);
      Person[] peeps = new Person[search.scoreDocs.length];
      for (int i = 0; i < search.scoreDocs.length; i++) {
        Document doc = searcher.doc(search.scoreDocs[i].doc);
        int id = Integer.parseInt(doc.getFieldable("id").stringValue());
        peeps[i] = getPersonForID(id);
      }
      return peeps;
    } catch( Exception e) {
      if (Sage.DBG) System.out.println("Bad things while searching: " + e);
      e.printStackTrace();
    }
    return Pooler.EMPTY_PERSON_ARRAY;
  }
}
