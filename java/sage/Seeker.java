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

import jcifs.smb.SmbFile;

import sage.plugin.PluginEventManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

/*
 * External usage of Seeker:
 * VideoFrame for getCurrRecord which
 * also is synchronized on, Sage for initialization, ChannelSetupMenu for video control,
 * MainMenu for recently watched, Wizard for file Airings to preserve,
 * ChronicMenu for getting chronic info, CommandInterface for drawAiringRect call,
 * VideoSetupMenu for modifying video directory, EventRouter for determining if Power
 * button turns on or off
 */
public class Seeker implements Hunter
{
  private static final String VIDEO_STORAGE = "video_storage";
  private static final String ARCHIVE_DIRECTORY = "archive_directory";
  private static final String MMC_PRESENT = "mmc_present";
  private static final String LAST_STATION_ID = "last_channel";
  private static final String FAST_MUX_SWITCH = "fast_mux_switch";
  public static final String CHANNEL_CHANGE_ASK_ADVANCE = "channel_change_ask_advance";
  private static final String DEFAULT_RECORDING_QUALITY = "default_recording_quality";
  private static final String DISABLE_PROFILER_RECORDING = "disable_profiler_recording";
  private static final String USE_DTV_MAJOR_MINOR_CHANNELS = "use_dtv_major_minor_channels";
  private static final String PICTURE_LIBRARY_IMPORT_FILENAME_EXTENSIONS = "picture_library_import_filename_extensions";
  private static final String MUSIC_LIBRARY_IMPORT_FILENAME_EXTENSIONS = "music_library_import_filename_extensions";
  private static final String PLAYLIST_IMPORT_FILENAME_EXTENSIONS = "playlist_import_filename_extensions";
  private static final String VIDEO_LIBRARY_IMPORT_FILENAME_EXTENSIONS = "video_library_import_filename_extensions";
  private static final String THOROUGH_LIB_SCAN_PERIOD = "thorough_lib_scan_period";
  private static final String AUTO_IMPORT_IGNORE_PATHS = "autoimport_ignore_paths";
  private static final String AUTO_IMPORT_ADDED_NETWORK_PATHS = "autoimport_added_net_paths";
  static final String SEEKER_KEY = "seeker";

  private static final String VALID_ROOT_IMPORT_PATHS = "seeker/valid_root_import_paths";

  private static final long MILLIS_PER_HOUR = 60*60000L;

  private static final long MINUTE = 60000L;

  public static final String DVD_VOLUME_SECRET = "video_ts";
  public static final String BLURAY_VOLUME_SECRET = "bdmv";

  private static final boolean EXPONENTIAL_RED = true;

  public static final String LOCAL_PROCESS_CLIENT = "SAGETV_PROCESS_LOCAL_UI";

  public static final int VIDEO_DISKSPACE_USE_ONLY = 1;
  public static final int VIDEO_DISKSPACE_LEAVE_FREE = 2;
  public static final int VIDEO_DISKSPACE_USE_ALL = 3;

  private static final long MAX_SLEEP_TIME = 5*MINUTE;
  private static final long DISK_BONUS_TIME = 15*MINUTE;

  private static final String[] SUPPORTED_MUSIC_EXTENSIONS = { ".mp3", ".wma", ".ogg", ".wav", ".aac", ".m4a", ".flac", ".ac3", ".dts", ".mka" };
  private static final String[] SUPPORTED_VIDEO_EXTENSIONS =
      { ".mpg", ".mpeg", ".mp2", ".mpeg2", ".mpe", ".avi", ".divx", ".mpg1", ".ts",
    ".wmv", ".asf", ".wm", ".tivo", ".m2t", ".vob", ".flv", ".mp4", ".mov", ".trp", ".m4v", ".ogm", ".3gp", ".mkv", ".dvr-ms",
    ".m2ts", ".mts", ".iso", ".tp" };
  private static final String[] SUPPORTED_PICTURE_EXTENSIONS = { ".jpg", ".gif", ".jpeg", ".jpe", ".png", ".tif", ".tiff" };

  private static final long FAT_FILE_SIZE_LIMIT = 3500000000L;

  public static final int VIDEO_DIR_MASK = 0x1;
  public static final int MUSIC_DIR_MASK = 0x2;
  public static final int PICTURE_DIR_MASK = 0x4;
  public static final int ALL_DIR_MASK = 0x7;

  private static final long EXTERNAL_PROCESS_CHECK_PERIOD = 10000;

  private static Seeker chosenOne;
  private static final Object instanceLock = new Object();

  private static class SeekerHolder
  {
    public static final Seeker instance = new Seeker();
  }

  public static Seeker prime()
  {
    Seeker instance = getInstance();
    synchronized (instanceLock)
    {
      instance.init();
    }
    return instance;
  }

  public static Seeker getInstance()
  {
    return SeekerHolder.instance;
  }

  private Seeker()
  {
    sched = Scheduler.getInstance();
    mmc = MMC.getInstance();
    wiz = Wizard.getInstance();
    epg = EPG.getInstance();
    god = Carny.getInstance();
    deniedMustSees = new Vector<Airing>();
    completeChanChangeAsks = new Vector<Airing>();
    videoStore = new Vector<VideoStorage>();
    encoderStateMap = new HashMap<CaptureDevice, EncoderState>();
    ignoreFiles = Collections.synchronizedSet(new HashSet<String>());
    currRecords = Collections.synchronizedSet(new HashSet<Airing>());
    currRecordFiles = Collections.synchronizedSet(new HashSet<MediaFile>());
    clientWatchFileMap = Collections.synchronizedMap(new HashMap<MediaFile, Set<UIClient>>());
    exportPlugins = new Vector<FileExportPlugin>();
    nfsMountMap = new LinkedHashMap<String, String>(); // to retain the order so the properties file doesn't get overly updated
    smbMountMap = new LinkedHashMap<String, String>(); // to retain the order so the properties file doesn't get overly updated
    failedMounts = new HashSet<String>();

    prefs = SEEKER_KEY + '/';
    autoImportIgnorePaths = Sage.parseCommaDelimSet(Sage.get(prefs + AUTO_IMPORT_IGNORE_PATHS, ""));
    autoImportAddedNetPaths = Sage.parseCommaDelimSet(Sage.get(prefs + AUTO_IMPORT_ADDED_NETWORK_PATHS, ""));
    delayForNextLibScan = Sage.getLong("seeker/delay_before_library_scan", 0);
    // Dir monitor has been added for embedded, so we can put this back at a huge number since they should never be needed. But just to
    // cover any corner cases we may somehow miss...we'll keep it at every 24 hours so any problems would be fixed within a day.
    libScanPeriod = Sage.getLong("seeker/library_scan_period", (2 * Sage.MILLIS_PER_HR));
    //leadTime = Sage.getLong(prefs + LEAD_TIME, 0/*2*MINUTE*/);
    // 8/12/03 Lead times != 0 screw up multituner forced watch control because recordings get started
    // before the force watch can take control of them like it should
    leadTime = 0;//5000;
    // PTS rolls over every 26 hours
    StringTokenizer toker = new StringTokenizer(Sage.get(prefs + VIDEO_STORAGE, ""), ";");
    while (toker.hasMoreTokens())
    {
      StringTokenizer toker2 = new StringTokenizer(toker.nextToken(), ",");
      if (toker2.countTokens() == 3)
      {
        try
        {
          videoStore.add(new VideoStorage(toker2.nextToken(), Long.parseLong(toker2.nextToken()),
              Integer.parseInt(toker2.nextToken())));
        }
        catch (Exception e)
        {}
      }
    }
    if (videoStore.isEmpty())
    {
      if (Sage.WINDOWS_OS && !Sage.isHeadless())
        videoStore.add(new VideoStorage(Sage.VISTA_OS ? new File(Sage.readStringValue(Sage.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders", "My Video"),
            "SageTV").getAbsolutePath() : new File(System.getProperty("user.home"),
                "My Documents\\My Videos\\SageTV").getAbsolutePath(),
                2000000000L, VIDEO_DISKSPACE_LEAVE_FREE));
      else if (Sage.WINDOWS_OS)
        videoStore.add(new VideoStorage(new File(System.getProperty("user.dir"), "Video").getAbsolutePath(),
            20000000000L, VIDEO_DISKSPACE_USE_ALL));
      else if (Sage.MAC_OS_X)
      {
        File f = new File("/Users/Shared/SageTV/TV");
        f.mkdirs();
        videoStore.add(new VideoStorage(f.getAbsolutePath(), 2000000000L, VIDEO_DISKSPACE_LEAVE_FREE));
      }
      else
      {
        new File(SageTV.LINUX_ROOT_MEDIA_PATH + "/tv").mkdirs();
        videoStore.add(new VideoStorage(SageTV.LINUX_ROOT_MEDIA_PATH + "/tv", 25000000000L, VIDEO_DISKSPACE_LEAVE_FREE));
      }
    }

    chanWaitAskAdv = Sage.getLong(prefs + CHANNEL_CHANGE_ASK_ADVANCE, 5*Sage.MILLIS_PER_MIN);
    disableProfilerRecs = Sage.getBoolean(prefs + DISABLE_PROFILER_RECORDING, true);
    dtvMajorMinorChans = Sage.getBoolean(prefs + USE_DTV_MAJOR_MINOR_CHANNELS, false);
    toker = new StringTokenizer(Sage.get(prefs + PICTURE_LIBRARY_IMPORT_FILENAME_EXTENSIONS,
        ".jpg,.gif,.jpeg,.jpe,.png"), ",");
    picLibFileExts = new HashSet<String>();
    while (toker.hasMoreTokens())
    {
      picLibFileExts.add(toker.nextToken().toLowerCase());
    }
    for (int i = 0; i < SUPPORTED_PICTURE_EXTENSIONS.length; i++)
    {
      if (!picLibFileExts.contains(SUPPORTED_PICTURE_EXTENSIONS[i]))
      {
        picLibFileExts.add(SUPPORTED_PICTURE_EXTENSIONS[i]);
        Sage.put(prefs + PICTURE_LIBRARY_IMPORT_FILENAME_EXTENSIONS,
            Sage.get(prefs + PICTURE_LIBRARY_IMPORT_FILENAME_EXTENSIONS, "") + "," + SUPPORTED_PICTURE_EXTENSIONS[i]);
      }
    }
    if (Sage.getBoolean("seeker/disable_bmp_picture_imports", true))
      picLibFileExts.remove(".bmp");
    toker = new StringTokenizer(Sage.get(prefs + MUSIC_LIBRARY_IMPORT_FILENAME_EXTENSIONS,
        ".mp3,.wav,.wma,.aac,.m4a,.flac"), ",");
    musicLibFileExts = new HashSet<String>();
    while (toker.hasMoreTokens())
    {
      musicLibFileExts.add(toker.nextToken().toLowerCase());
    }
    for (int i = 0; i < SUPPORTED_MUSIC_EXTENSIONS.length; i++)
    {
      if (!musicLibFileExts.contains(SUPPORTED_MUSIC_EXTENSIONS[i]))
      {
        musicLibFileExts.add(SUPPORTED_MUSIC_EXTENSIONS[i]);
        Sage.put(prefs + MUSIC_LIBRARY_IMPORT_FILENAME_EXTENSIONS,
            Sage.get(prefs + MUSIC_LIBRARY_IMPORT_FILENAME_EXTENSIONS, "") + "," + SUPPORTED_MUSIC_EXTENSIONS[i]);
      }
    }
    toker = new StringTokenizer(Sage.get(prefs + PLAYLIST_IMPORT_FILENAME_EXTENSIONS,
        ".m3u,.asx,.wax,.wvx,.wpl"), ",");
    playlistFileExts = new HashSet<String>();
    while (toker.hasMoreTokens())
    {
      playlistFileExts.add(toker.nextToken().toLowerCase());
    }
    toker = new StringTokenizer(Sage.get(prefs + VIDEO_LIBRARY_IMPORT_FILENAME_EXTENSIONS,
        ".mpg,.mpeg,.mp2,.mpeg2,.mpe,.avi,.divx,.mpg1,.ts,.wmv,.asf,.wm,.tivo,.m2t,.vob,.flv,.mp4,.mov"), ",");
    vidLibFileExts = new HashSet<String>();
    while (toker.hasMoreTokens())
    {
      vidLibFileExts.add(toker.nextToken().toLowerCase());
    }
    for (int i = 0; i < SUPPORTED_VIDEO_EXTENSIONS.length; i++)
    {
      if (!vidLibFileExts.contains(SUPPORTED_VIDEO_EXTENSIONS[i]))
      {
        vidLibFileExts.add(SUPPORTED_VIDEO_EXTENSIONS[i]);
        Sage.put(prefs + VIDEO_LIBRARY_IMPORT_FILENAME_EXTENSIONS,
            Sage.get(prefs + VIDEO_LIBRARY_IMPORT_FILENAME_EXTENSIONS, "") +
                "," + SUPPORTED_VIDEO_EXTENSIONS[i]);
      }
    }
    String archiveDirStrs = "";
    if (Sage.WINDOWS_OS && !Sage.isHeadless())
    {
      String defaultArchiveDirs;
      if (Sage.VISTA_OS)
      {
        String s = Sage.readStringValue(Sage.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders", "My Video");
        defaultArchiveDirs = ((s != null) ? new File(s).getAbsolutePath() :
          new File(System.getProperty("user.home"),
              "My Documents\\My Videos").getAbsolutePath()) + "," + VIDEO_DIR_MASK + ";";
        s = Sage.readStringValue(Sage.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders", "My Pictures");
        defaultArchiveDirs += ((s != null) ? new File(s).getAbsolutePath() :
          new File(System.getProperty("user.home"),
              "My Documents\\My Pictures").getAbsolutePath()) + "," + PICTURE_DIR_MASK + ";";
        s = Sage.readStringValue(Sage.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders", "My Music");
        defaultArchiveDirs += ((s != null) ? new File(s).getAbsolutePath() :
          new File(System.getProperty("user.home"),
              "My Documents\\My Music").getAbsolutePath()) + "," + MUSIC_DIR_MASK + ";";
      }
      else
      {
        defaultArchiveDirs = new File(System.getProperty("user.home"),
            "My Documents\\My Videos").getAbsolutePath() + "," + VIDEO_DIR_MASK + ";";
        defaultArchiveDirs += new File(System.getProperty("user.home"),
            "My Documents\\My Pictures").getAbsolutePath() + "," + PICTURE_DIR_MASK + ";";
        defaultArchiveDirs += new File(System.getProperty("user.home"),
            "My Documents\\My Music").getAbsolutePath() + "," + MUSIC_DIR_MASK + ";";
      }
      String s = Sage.readStringValue(Sage.HKEY_LOCAL_MACHINE,
          "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders", "CommonVideo");
      if (s != null && s.length() > 0)
        defaultArchiveDirs += new File(s).getAbsolutePath() + "," + VIDEO_DIR_MASK + ";";
      s = Sage.readStringValue(Sage.HKEY_LOCAL_MACHINE,
          "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders", "CommonPictures");
      if (s != null && s.length() > 0)
        defaultArchiveDirs += new File(s).getAbsolutePath() + "," + PICTURE_DIR_MASK + ";";
      s = Sage.readStringValue(Sage.HKEY_LOCAL_MACHINE,
          "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders", "CommonMusic");
      if (s != null && s.length() > 0)
        defaultArchiveDirs += new File(s).getAbsolutePath() + "," + MUSIC_DIR_MASK + ";";
      archiveDirStrs = Sage.get(prefs + ARCHIVE_DIRECTORY, defaultArchiveDirs);
    }
    else if (Sage.MAC_OS_X)
    {
      /*   TODO: This results in /private/root/xxx rather than what we want, figure out a better way
  String defaultArchiveDirs = new File(System.getProperty("user.home"),
      "Movies").getAbsolutePath() + "," + VIDEO_DIR_MASK + ";";
  defaultArchiveDirs += new File(System.getProperty("user.home"),
      "Pictures").getAbsolutePath() + "," + PICTURE_DIR_MASK + ";";
  defaultArchiveDirs += new File(System.getProperty("user.home"),
      "Music").getAbsolutePath() + "," + MUSIC_DIR_MASK + ";";
       */
      String defaultArchiveDirs = "";
      new File("/Users/Shared/SageTV/Music").mkdirs();
      new File("/Users/Shared/SageTV/Video").mkdirs();
      new File("/Users/Shared/SageTV/Pictures").mkdirs();
      defaultArchiveDirs += new File("/Users/Shared/SageTV/Music").getAbsolutePath() + "," + MUSIC_DIR_MASK + ";";
      defaultArchiveDirs += new File("/Users/Shared/SageTV/Pictures").getAbsolutePath() + "," + PICTURE_DIR_MASK + ";";
      defaultArchiveDirs += new File("/Users/Shared/SageTV/Video").getAbsolutePath() + "," + VIDEO_DIR_MASK + ";";
      archiveDirStrs = Sage.get(prefs + ARCHIVE_DIRECTORY, defaultArchiveDirs);
    }
    else if (Sage.LINUX_OS)
    {
      String defaultArchiveDirs = SageTV.LINUX_ROOT_MEDIA_PATH + "/videos" + "," + VIDEO_DIR_MASK + ";";
      new File(SageTV.LINUX_ROOT_MEDIA_PATH + "/videos").mkdirs();
      defaultArchiveDirs += SageTV.LINUX_ROOT_MEDIA_PATH + "/pictures" + "," + PICTURE_DIR_MASK + ";";
      new File(SageTV.LINUX_ROOT_MEDIA_PATH + "/pictures").mkdirs();
      defaultArchiveDirs += SageTV.LINUX_ROOT_MEDIA_PATH + "/music" + "," + MUSIC_DIR_MASK + ";";
      new File(SageTV.LINUX_ROOT_MEDIA_PATH + "/music").mkdirs();
      archiveDirStrs = Sage.get(prefs + ARCHIVE_DIRECTORY, defaultArchiveDirs);
    }
    else
      archiveDirStrs = Sage.get(prefs + ARCHIVE_DIRECTORY, "");

    toker = new StringTokenizer(archiveDirStrs, ";");
    archiveDirs = new File[toker.countTokens()];
    archiveDirMasks = new int[toker.countTokens()];
    for (int i = 0; i < archiveDirs.length; i++)
    {
      String currToke = toker.nextToken();
      if (currToke.indexOf(',') == -1)
      {
        archiveDirs[i] = new File(currToke);
        archiveDirMasks[i] = ALL_DIR_MASK;
      }
      else
      {
        archiveDirs[i] = new File(currToke.substring(0, currToke.indexOf(',')));
        try
        {
          archiveDirMasks[i] = Integer.parseInt(currToke.substring(currToke.indexOf(',') + 1));
        }
        catch (Exception e)
        {
          archiveDirMasks[i] = ALL_DIR_MASK;
        }
      }
    }

    writeStoragePrefs();

    if (Sage.LINUX_OS || Sage.MAC_OS_X)
    {
      // Read in the mount map. This is used to make library imports of SMB shares significantly easier.
      String mounts = Sage.get("linux/smb_mounts", "");
      toker = new StringTokenizer(mounts, ";");
      while (toker.hasMoreTokens())
      {
        String mountInfo = toker.nextToken();
        int commIdx = mountInfo.indexOf(',');
        if (commIdx != -1)
        {
          String smbPath = mountInfo.substring(0, commIdx);
          String localPath = mountInfo.substring(commIdx + 1);
          smbMountMap.put(smbPath, localPath);
        }
      }
      // Now handle NFS mounts
      mounts = Sage.get("linux/nfs_mounts", "");
      toker = new StringTokenizer(mounts, ";");
      while (toker.hasMoreTokens())
      {
        String mountInfo = toker.nextToken();
        int commIdx = mountInfo.indexOf(',');
        if (commIdx != -1)
        {
          String smbPath = mountInfo.substring(0, commIdx);
          String localPath = mountInfo.substring(commIdx + 1);
          nfsMountMap.put(smbPath, localPath);
        }
      }
      establishMountPoints();
    }
    String validRootStr = Sage.get(VALID_ROOT_IMPORT_PATHS, "");//Sage.WINDOWS_OS ? "" : (SageTV.LINUX_ROOT_MEDIA_PATH + ",/mnt/shares"));
    toker = new StringTokenizer(validRootStr, ",");
    validRootImportPaths = new String[toker.countTokens()];
    int i = 0;
    while (toker.hasMoreTokens())
      validRootImportPaths[i++] = toker.nextToken();
  }

  public static String[] getVideoDiskspaceRules()
  {
    return new String[] {Sage.rez("Diskspace_Use_Only"), Sage.rez("Diskspace_Leave_Free"), Sage.rez("Diskspace_Use_All")};
  }

  private void init()
  {
    mmcPresent = Sage.getBoolean(prefs + MMC_PRESENT, true) && (Sage.MAC_OS_X || mmc.getCaptureDeviceNames().length > 0) && !Sage.client;
    defaultQuality = MMC.cleanQualityName(Sage.get(prefs + DEFAULT_RECORDING_QUALITY, Sage.rez("Great")));

    fastMuxSwitch = Sage.getBoolean(prefs + FAST_MUX_SWITCH, true);

    performFullContentReindex = (Wizard.GENERATE_MEDIA_MASK || Sage.getBoolean("force_full_content_reindex", false)) &&
        !Sage.getBoolean("disable_full_content_reindex", false);
    performPicLibReindex = !Sage.getBoolean("completed_exif_parser_import", false);
    FSManager.getInstance(); // make sure ignore files are set properly
    checkDirsForFiles(true);
    // If this setting doesn't exist then this is an upgrade that has this as a new feature so do the embedding of the data now
    boolean addMpegMetadataNow = (Sage.get("seeker/mpeg_metadata_embedding", null) == null);
    if (addMpegMetadataNow && Sage.DBG) System.out.println("Seeker is embedding MPEG metadata into all existing recordings now");


    // Check to make sure the default DVD/CD drive MF is created
    boolean hasDefaultDVDMF = false;
    MediaFile[] myFiles = wiz.getFiles();
    for (int i = 0; i < myFiles.length; i++)
    {
      if (myFiles[i].generalType == MediaFile.MEDIAFILE_DEFAULT_DVD_DRIVE)
        hasDefaultDVDMF = true;
      if (myFiles[i].generalType == MediaFile.MEDIAFILE_LOCAL_PLAYBACK)
      {
        // Old legacy stuff
        wiz.removeMediaFile(myFiles[i]);
      }
      else if (!Sage.client && (performPicLibReindex || performFullContentReindex) && myFiles[i].isTV())
      {
        // The pic lib reindex also means we need to reindex TS files for PID detection
        // Check for format redetection in the TV files in the DB
        checkForDataReimport("", myFiles[i]);
      }
      else if (addMpegMetadataNow && !Sage.client && myFiles[i].isTV() && !myFiles[i].isAnyLiveStream())
      {
        sage.media.format.MpegMetadata.addMediaFileMetadata(myFiles[i]);
      }
    }
    if (addMpegMetadataNow)
      Sage.putBoolean("seeker/mpeg_metadata_embedding", true);
    if (!hasDefaultDVDMF)
    {
      MediaFile dvdMF = wiz.addMediaFileSpecial(MediaFile.MEDIAFILE_DEFAULT_DVD_DRIVE, null, null,
          DBObject.MEDIA_MASK_DVD, null);
      if (Sage.DBG) System.out.println("Setup DVD Drive: " + dvdMF);
    }


    if (!Sage.client)
    {
      FileExportPlugin testPlugin = null;
      if (Sage.WINDOWS_OS && !Sage.getBoolean(prefs + "disable_roxio_export_plugin", true))
      {
        testPlugin = new RoxioFileExport();
        if (testPlugin.openPlugin())
          exportPlugins.add(testPlugin);
      }
      if (Sage.MAC_OS_X && !Sage.getBoolean(prefs + "disable_itunes_export_plugin", false))//true))
      {
        testPlugin = new iTunesFileExport();
        if (testPlugin.openPlugin())
          exportPlugins.add(testPlugin);
      }
      String exportPluginNames = Sage.get(prefs + "export_plugins", "");
      StringTokenizer toker = new StringTokenizer(exportPluginNames, ";,");
      while (toker.hasMoreTokens())
      {
        String name = toker.nextToken();
        try
        {
          testPlugin = (FileExportPlugin) Class.forName(name).newInstance();
          if (testPlugin.openPlugin())
          {
            if (Sage.DBG) System.out.println("Adding export plugin: " + name);
            exportPlugins.add(testPlugin);
          }
        }
        catch (Throwable e)
        {
          System.out.println("Error adding export plugin: " + name + " of " + e);
        }
      }
    }
  }

  // Does NOT include playlist files
  public boolean hasImportableFileExtension(String s)
  {
    if (s == null) return false;
    int lastDot = s.lastIndexOf('.');
    if (lastDot != -1)
      s = s.substring(lastDot);
    s = s.toLowerCase();
    return picLibFileExts.contains(s) || musicLibFileExts.contains(s) || vidLibFileExts.contains(s);
  }

  public int guessImportedMediaMaskFast(String s)
  {
    return guessImportedMediaMask(s, true);
  }
  public int guessImportedMediaMask(String s)
  {
    return guessImportedMediaMask(s, false);
  }
  private int guessImportedMediaMask(String s, boolean fast)
  {
    if (s == null) return 0;
    String orgS = s;
    int lastDot = s.lastIndexOf('.');
    if (lastDot != -1)
      s = s.substring(lastDot);
    s = s.toLowerCase();
    if (s.endsWith(File.separatorChar + DVD_VOLUME_SECRET))
      return DBObject.MEDIA_MASK_DVD;
    else if (s.endsWith(File.separatorChar + BLURAY_VOLUME_SECRET))
      return DBObject.MEDIA_MASK_BLURAY;
    else if (".iso".equals(s))
    {
      if (fast)
        return DBObject.MEDIA_MASK_DVD;
      synchronized (sage.media.format.FormatParser.FORMAT_DETECT_MOUNT_DIR)
      {
        File mountDir;
        mountDir = FSManager.getInstance().requestISOMount(new File(orgS));
        if (mountDir != null)
        {
          // Check for a BluRay folder structure
          boolean isBluRay = (new File(mountDir, "BDMV").isDirectory() || new File(mountDir, "bdmv").isDirectory());
          boolean isDVD = false;
          if (!isBluRay)
          {
            // Check for a DVD
            if (new File(mountDir, "VIDEO_TS").isDirectory() ||
                new File(mountDir, "video_ts").isDirectory())
              isDVD = true;
          }
          FSManager.getInstance().releaseISOMount(mountDir);
          if (isBluRay)
            return DBObject.MEDIA_MASK_BLURAY;
          else if (isDVD)
            return DBObject.MEDIA_MASK_DVD;
        }
        return 0;
      }
    }
    else if (picLibFileExts.contains(s) || ".bmp".equals(s))
      return DBObject.MEDIA_MASK_PICTURE;
    else if (musicLibFileExts.contains(s))
      return DBObject.MEDIA_MASK_MUSIC;
    else if (vidLibFileExts.contains(s))
      return DBObject.MEDIA_MASK_VIDEO;
    else
      return DBObject.MEDIA_MASK_VIDEO; // not a bad default
  }

  public boolean isAutoImportEnabled()
  {
    return false;
  }

  private static boolean checkFileAccess(File f)
  {
    try
    {
      // NOTE: We can't check for write access or we wouldn't be able to import read only files!
      FileInputStream fis = new FileInputStream(f);
      fis.close();
    }
    catch (Exception e)
    {
      return false;
    }
    return true;
  }

  // We broke this up so that we can call the scans from the library importer since they can end up keeping
  // the system from going into standby if the recording paths are UNC
  // NARFLEX - 01/27/10 - I removed the synchronized keyword from this since I don't see anything
  // related to the main Seeker data structures that would require a sync lock.
  private void checkDirsForFiles(boolean scanVidDirs)
  {
    if (Sage.DBG) System.out.println("Checking video directories for new files");
    // Move any video files out of our directory that we don't know about anymore.
    MediaFile[] myFiles = wiz.getFiles();
    Set<String> accountedFiles = scanVidDirs ? new HashSet<String>() : null;
    CaptureDeviceInput[] allSrcs = mmc.getAllInputs();
    String[] allSrcString = new String[allSrcs.length];
    for (int i = 0; i < allSrcs.length; i++)
      allSrcString[i] = allSrcs[i].toString();
    Set<String> neededLiveSrcs = new HashSet<String>(Arrays.asList(allSrcString));
    Set<String> neededLiveBuffSrcs = new HashSet<String>(Arrays.asList(allSrcString));
    for (int i = 0; i < myFiles.length; i++)
    {
      if (Sage.client)
      {
        if (myFiles[i].isAnyLiveStream())
        {
          CaptureDeviceInput cdi = mmc.getCaptureDeviceInputNamed(myFiles[i].encodedBy);
          (myFiles[i].isLiveStream() ? MediaFile.liveFileMap : MediaFile.liveBuffFileMap).put(cdi, myFiles[i]);
        }
        continue;
      }

      if (myFiles[i].isLiveStream())
      {
        if (!neededLiveSrcs.remove(myFiles[i].encodedBy))
        {
          if (Sage.DBG) System.out.println("Seeker removing LiveStreamMF because the source is gone: " + myFiles[i]);
          myFiles[i].delete(false);
          wiz.removeMediaFile(myFiles[i]);
        }
        else
        {
          CaptureDeviceInput cdi = mmc.getCaptureDeviceInputNamed(myFiles[i].encodedBy);
          MediaFile.liveFileMap.put(cdi, myFiles[i]);
          if (scanVidDirs)
          {
            for (int j = 0; j < myFiles[i].getNumSegments(); j++)
              accountedFiles.add(myFiles[i].getFile(j).getAbsolutePath());
          }
        }
      }
      else if (myFiles[i].isLiveBufferedStream())
      {
        if (!neededLiveBuffSrcs.remove(myFiles[i].encodedBy))
        {
          if (Sage.DBG) System.out.println("Seeker removing LiveBuffStreamMF because the source is gone: " + myFiles[i]);
          myFiles[i].delete(false);
          wiz.removeMediaFile(myFiles[i]);
        }
        else if (!videoStore.isEmpty() && getStorageForFile(myFiles[i]) == null)
        {
          if (Sage.DBG) System.out.println("Removing LiveBuffStreamMF because its video directory is no longer valid:" + myFiles[i]);
          myFiles[i].delete(false);
          wiz.removeMediaFile(myFiles[i]);
        }
        else
        {
          CaptureDeviceInput cdi = mmc.getCaptureDeviceInputNamed(myFiles[i].encodedBy);
          if (cdi.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
          {
            // Bug fix for this not getting sent from client->server
            myFiles[i].setStreamBufferSize(Sage.getLong("default_pause_buffer_size_dtv", 5*16*1024*1024));
          }
          MediaFile.liveBuffFileMap.put(cdi, myFiles[i]);
          if (scanVidDirs)
          {
            for (int j = 0; j < myFiles[i].getNumSegments(); j++)
              accountedFiles.add(myFiles[i].getFile(j).getAbsolutePath());
          }
        }
      }
      else if (scanVidDirs)
      {
        for (int j = 0; j < myFiles[i].getNumSegments(); j++)
          accountedFiles.add(myFiles[i].getFile(j).getAbsolutePath());
      }
    }

    if (Sage.client)
      return; // the only thing the client needs to do is setup the MediaFile maps

    // Create the live/buff mf objects that need to be
    for (String newInputName : neededLiveSrcs)
    {
      if (Sage.DBG) System.out.println("Seeker creating live stream MF file for " + newInputName);
      CaptureDeviceInput cdi = mmc.getCaptureDeviceInputNamed(newInputName);
      MediaFile newMF;
      MediaFile.liveFileMap.put(cdi,
          newMF = wiz.addMediaFileSpecial(MediaFile.MEDIAFILE_LIVE_STREAM, newInputName, null,
              cdi.getType() == CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX ?
                  DBObject.MEDIA_MASK_MUSIC : DBObject.MEDIA_MASK_VIDEO,
                  cdi.getEncoderMediaFormat()));
      if (scanVidDirs)
      {
        for (int j = 0; j < newMF.getNumSegments(); j++)
          accountedFiles.add(newMF.getFile(j).getAbsolutePath());
      }
    }
    if (!neededLiveBuffSrcs.isEmpty())
    {
      File[] stores = getVideoStoreDirectories();
      for (String newInputName : neededLiveBuffSrcs)
      {
        if (Sage.DBG) System.out.println("Seeker creating live buffered stream MF file for " + newInputName);
        CaptureDeviceInput cdi = mmc.getCaptureDeviceInputNamed(newInputName);
        MediaFile newMF;
        MediaFile.liveBuffFileMap.put(cdi,
            newMF = wiz.addMediaFileSpecial(MediaFile.MEDIAFILE_LIVE_BUFFERED_STREAM, newInputName,
                stores.length > 0 ? stores[0].toString() : System.getProperty("user.home"),
                    cdi.getType() == CaptureDeviceInput.FM_RADIO_CROSSBAR_INDEX ?
                        DBObject.MEDIA_MASK_MUSIC : DBObject.MEDIA_MASK_VIDEO,
                        cdi.getEncoderMediaFormat()));
        if (scanVidDirs)
        {
          for (int j = 0; j < newMF.getNumSegments(); j++)
            accountedFiles.add(newMF.getFile(j).getAbsolutePath());
        }
      }
    }

    if (!Sage.client && scanVidDirs)
    {
      boolean tvFilesModified = false;
      File[] testFiles = listAllFilesInVideoStorageDirs();
      Arrays.sort(testFiles); // so we hit the -0 ones first
      MediaFile[] tvFileList = null; // for advanced file recovery where we extensively search all TV MediaFile objects
      for (int i = 0; (testFiles != null) && i < testFiles.length; i++)
      {
        String name = testFiles[i].getName();
        if (!name.endsWith(".mpg") && !name.endsWith(".ts") && !name.endsWith(".avi") && !name.endsWith(".mp4") && !name.endsWith(".mkv")) continue;
        if (accountedFiles.contains(testFiles[i].getAbsolutePath()) || ignoreFiles.contains(testFiles[i].getAbsolutePath())) continue;
        if (testFiles[i].length() == 0)
        {
          if (testFiles[i].isFile())
          {
            if (Sage.DBG) System.out.println("Removing zero length file from video dir:" + testFiles[i]);
            testFiles[i].delete();
          }
          else
          {
            if (Sage.DBG) System.out.println("Skipping the removal of zero length file from video dir: " + testFiles[i] + " since it fails isFile()");
          }
          continue;
        }
        // Make sure we can access the file. i.e. something else isn't still writing it
        if (!checkFileAccess(testFiles[i]))
        {
          if (Sage.DBG) System.out.println("Ignoring file for import because we can't get access to it:" + testFiles[i]);
          continue;
        }
        if (Sage.getBoolean("advanced_video_file_recovery", false))
        {
          if (tvFileList == null)
          {
            List<MediaFile> tempVec = new ArrayList<MediaFile>();
            for (int j = 0; j < myFiles.length; j++)
            {
              if (myFiles[j].isTV())
                tempVec.add(myFiles[j]);
            }
            tvFileList = tempVec.toArray(new MediaFile[0]);
          }

          MediaFile matchFile = doAdvancedFileRecovery(tvFileList, testFiles[i]);
          if (matchFile != null)
          {
            tvFilesModified = true;
            for (int k = 0; k < matchFile.getNumSegments(); k++)
              accountedFiles.add(matchFile.getFile(k).getAbsolutePath());
            continue;
          }
        }
        /*
         * This restores the media files if removed from the DB
         */
        Airing air = null;
        int fileIdx = 0;
        try
        {
          int idx1 = name.lastIndexOf('-');
          if (idx1 != -1)
          {
            int idx2 = name.lastIndexOf('-', idx1 - 1);
            if (idx2 != -1)
            {
              int airingID = Integer.parseInt(name.substring(idx2 + 1, idx1));
              air = wiz.getAiringForID(airingID);
              idx2 = name.indexOf('.', idx1);
              if (idx2 != -1)
                fileIdx = Integer.parseInt(name.substring(idx1 + 1, idx2));
            }
          }
        }
        catch (Exception nfe){}
        if (air != null && fileIdx == 0)
        {
          if (wiz.getFileForAiring(air) != null)
          {
            // This happens when the user moves files between video directories. The correct
            // way is to move things into a library import folder.
            // If these files on longer exist that are in that MediaFile then switch them to be this file
            MediaFile alternateMF = wiz.getFileForAiring(air);
            if (alternateMF.getFile(0) != null && !alternateMF.getFile(0).exists())
            {
              if (Sage.DBG) System.out.println("Swapping MediaFile's file to be " + testFiles[i] + " from " + alternateMF);
              alternateMF.setFiles(new File[] { testFiles[i] });
              alternateMF.thisIsComplete();
            }
            continue;
          }
          // If the timestamp for the file is totally off; then fix it. It's probably from an outside conversion
          if (Math.abs(testFiles[i].lastModified() - air.getEndTime()) > Sage.MILLIS_PER_HR && air.getEndTime() < Sage.time())
            testFiles[i].setLastModified(air.getEndTime());
          MediaFile mf = wiz.addMediaFileRecovered(air, testFiles[i]);
          // NARFLEX - 1-19-09 quite often we'll pull files back in through this recovery method; but they may have been
          // edited. They could then end up as a partial and get auto-deleted; which is bad. So we should mark all of
          // these as complete.
          if (air.getEndTime() < Sage.time())
          {
            mf.thisIsComplete();
          }
          if (Sage.DBG) System.out.println("MediaFile RECOVERY Added " + mf);
          tvFilesModified = true;
        }
        else if (Sage.getBoolean(prefs + "use_errant_dir", false))
        {
          if (Sage.DBG) System.out.println("ERRANT VIDEO FILE " + testFiles[i]);
          File errantDir = new File(testFiles[i].getParentFile(), "errant");
          IOUtils.safemkdirs(errantDir);
          testFiles[i].renameTo(new File(errantDir, testFiles[i].getName()));
        }
        else
        {
          MediaFile mf = wiz.addMediaFile(testFiles[i], "", MediaFile.ACQUISITION_AUTOMATIC_BY_VIDEO_STORAGE_PATH);
          PluginEventManager.postEvent(PluginEventManager.MEDIA_FILE_IMPORTED,
              new Object[] { PluginEventManager.VAR_MEDIAFILE, mf });
        }
      }
      if (tvFilesModified)
        sched.kick(true);
    }
  }

  private MediaFile doAdvancedFileRecovery(MediaFile[] fileList, File testFile)
  {
    boolean matchFound = false;
    String name = testFile.getName();
    for (int j = 0; j < fileList.length && !matchFound; j++)
    {
      for (int seg = 0; seg < fileList[j].getNumSegments(); seg++)
      {
        File segFile = fileList[j].getFile(seg);
        // Make sure its name matches and that the original file is no longer there
        String segFileName = segFile.getName();
        // In case we converted over from Windows to Linux/Mac
        if (!Sage.WINDOWS_OS)
        {
          int lastBS = segFileName.lastIndexOf('\\');
          if (lastBS != -1)
            segFileName = segFileName.substring(lastBS + 1);
        }
        if (segFileName.equals(name) && !segFile.isFile())
        {
          // Now check to make sure all the segment files in the set match
          List<File> newFileSet = new ArrayList<File>();
          for (int k = 0; k < fileList[j].getNumSegments(); k++)
          {
            File test2Seg = fileList[j].getFile(k);
            if (k == seg)
            {
              newFileSet.add(testFile);
              continue;
            }
            segFileName = test2Seg.getName();
            // In case we converted over from Windows to Linux/Mac
            if (!Sage.WINDOWS_OS)
            {
              int lastBS = segFileName.lastIndexOf('\\');
              if (lastBS != -1)
                segFileName = segFileName.substring(lastBS + 1);
            }
            File newSegFile = new File(testFile.getParentFile(), segFileName);
            if (newSegFile.isFile() && !test2Seg.isFile())
            {
              newFileSet.add(newSegFile);
              continue;
            }
            else
            {
              newFileSet = null;
              break;
            }
          }
          if (newFileSet != null)
          {
            if (Sage.DBG) System.out.println("Found matching media file for recovery of " + testFile + " to be " + fileList[j]);
            // Fix the modification timestamps on the files if they were copied and that was lost
            for (int m = 0; m < newFileSet.size(); m++)
              newFileSet.get(m).setLastModified(fileList[j].getEnd(m));
            fileList[j].setFiles(newFileSet.toArray(new File[0]));
            matchFound = true;
            return fileList[j];
          }
          // stop checking segments for this file
          break;
        }
      }
    }
    return null;
  }

  public boolean isDoingImportScan()
  {
    return currentlyImporting;
  }

  public void scanLibrary(boolean waitTillComplete)
  {
    doThoroughLibScan = true;
    establishMountPoints();
    if (!waitTillComplete)
      libraryImportScan();
    else
    {
      synchronized (importLock)
      {
        libraryImportScan();
        while (currentlyImporting)
        {
          try { importLock.wait(0);}catch(InterruptedException e){}
        }
      }
    }
  }

  private long lastSeekerGCTime;
  private void libraryImportScan()
  {
    if (Sage.client || disableLibraryScanning) return;
    synchronized (importLock)
    {
      needsAnImport = true;
      if (currentlyImporting) return;
      currentlyImporting = true;
    }
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        if (delayForNextLibScan > 0)
        {
          try{Thread.sleep(delayForNextLibScan);}catch(Exception e){}
          delayForNextLibScan = 0;
        }
        Catbert.distributeHookToAll("SystemStatusChanged", Pooler.EMPTY_OBJECT_ARRAY);
        PluginEventManager.postEvent(PluginEventManager.IMPORTING_STARTED, (Object[])null);
        // Check our video dirs now for new files as well
        checkDirsForFiles(true);
        if (disableLibraryScanning)
          currentlyImporting = false;
        while (needsAnImport && !disableLibraryScanning)
        {
          try{Thread.sleep(500);}catch(Exception e){}
          lastLibScanTime = Sage.time();
          if (Sage.DBG) System.out.println("Seeker is starting the library import scan...");
          needsAnImport = false;
          try
          {
            importLibraryFiles();
          }
          finally
          {
            synchronized (importLock)
            {
              if (!needsAnImport)
              {
                if (Sage.DBG) System.out.println("Seeker has finished the library import scan.");
                currentlyImporting = false;
                importLock.notifyAll();
              }
              if (disableLibraryScanning)
              {
                if (Sage.DBG) System.out.println("Seeker has terminated the library import scan.");
                currentlyImporting = false;
                importLock.notifyAll();
              }
            }
          }

          // As good a place as any to do something regular like this. With the ParallelGC,
          // we shouldn't notice any problems due to this.
          // FORCE it because we want to do that somewhere in the app to ensure cleanup
          // over long periods of time.
          // NOTE: BUT DON'T DO THIS IF MEDIA IS PLAYING OR WE HAVE CLIENTS WATCHING FILES (because it can interrupt the streaming)!!!
          if (Sage.getBoolean("allow_periodic_explicit_gc", false) && clientWatchFileMap.isEmpty() && Sage.time() - lastSeekerGCTime > 15*Sage.MILLIS_PER_MIN)
          {
            if (Sage.DBG) System.out.println("Seeker invoking System.gc()");
            lastSeekerGCTime = Sage.time();
            System.gc();
            if (Sage.DBG) System.out.println("Seeker System.gc() done");
          }
        }
        Catbert.distributeHookToAll("SystemStatusChanged", Pooler.EMPTY_OBJECT_ARRAY);
      }
    }, "LibraryImporter", Thread.MIN_PRIORITY);
  }

  private void checkForDataReimport(String namePrefix, MediaFile mf)
  {
    if (mf == null || (!doThoroughLibScan && !performFullContentReindex && (!performPicLibReindex ||
        (!mf.isPicture() && (!mf.isVideo() || sage.media.format.MediaFormat.MPEG2_TS.equals(mf.getContainerFormat())))))) return;
    int numFiles = mf.getNumSegments();
    boolean doReinit = true;
    for (int j = 0; j < numFiles; j++)
    {
      File currFile = mf.getFile(j);
      if (!currFile.exists()) // use exists() instead of isFile() so we detect DVD folders as well
      {
        doReinit = false;
      }
      if (!doThoroughLibScan && (j%20)==0)
        controlImportCPUUsage();
    }
    // NOTE: 11/17/04 - This does lots of disk access determining file modification times
    // so we don't want to do this every run or it hits the CPU nastily. Only do it
    // when the users requests a new scan.
    if (doReinit)
      mf.reinitializeMetadata(doThoroughLibScan,
          performFullContentReindex || (performPicLibReindex && (mf.isPicture() ||
              (mf.isVideo() && sage.media.format.MediaFormat.MPEG2_TS.equals(mf.getContainerFormat())))), namePrefix);
  }

  private void importLibraryFiles()
  {
    // Check to see if a full reindex has been set to go
    if (!performFullContentReindex && Sage.getBoolean("force_full_content_reindex", false) && !Sage.getBoolean("disable_full_content_reindex", false))
    {
      if (Sage.DBG) System.out.println("FULL REINDEX of imported database content has been triggered");
      performFullContentReindex = true;
    }
    MediaFile[] myFiles = wiz.getFiles();
    Map<String, MediaFile> accountedFileMap = new HashMap<String, MediaFile>();
    Set<File> desiredThumbFiles = new HashSet<File>();
    Set<MediaFile> mfsWithoutOwnAiring = new HashSet<MediaFile>();
    // Redoing the airing for a MF also requires saving the DB to clear the extra entries
    boolean contentCleared = false;
    boolean hasDefaultDVDMF = false;
    for (int i = 0; i < myFiles.length; i++)
    {
      if (myFiles[i].generalType == MediaFile.MEDIAFILE_DEFAULT_DVD_DRIVE)
      {
        hasDefaultDVDMF = true;
        continue;
      }

      desiredThumbFiles.add(myFiles[i].getGeneratedThumbnailFileLocation());
      // Remove non-local files and any files that aren't part of the import system.
      // NOTE: It's possible for a file to not be marked MEDIAFILE_IMPORTED if it had its EPG data
      // updated to change it to a TV file. But in tht case, it'll still have it's acquisition flag set to
      // automatic by import path and we can go off that.
      // NARFLEX: 1/22/07 - we added ACQUISITION_MANUAL here so we don't reimport files that were created as
      // a result of a transcode process
      if (!myFiles[i].isLocalFile() ||
          (myFiles[i].generalType != MediaFile.MEDIAFILE_IMPORTED &&
          myFiles[i].acquisitionTech != MediaFile.ACQUISITION_AUTOMATIC_BY_IMPORT_PATH &&
          myFiles[i].acquisitionTech != MediaFile.ACQUISITION_MANUAL))
        continue;
      if (myFiles[i].infoAiringID == myFiles[i].id &&
          myFiles[i].acquisitionTech == MediaFile.ACQUISITION_AUTOMATIC_BY_IMPORT_PATH)
      {
        mfsWithoutOwnAiring.add(myFiles[i]);
      }
      int numFiles = myFiles[i].getNumSegments();
      for (int j = 0; j < numFiles; j++)
      {
        accountedFileMap.put(myFiles[i].getFile(j).getAbsolutePath(), myFiles[i]);
      }
      // NOTE: 11/17/04 - This does lots of disk access determining file modification times
      // so we don't want to do this every run or it hits the CPU nastily. Only do it
      // when the users requests a new scan.
      /*      if (doReinit && doThoroughLibScan)
    contentCleared |= myFiles[i].reinitializeMetadata(doThoroughLibScan, performFullContentReindex);
       */    }

    // If we need to restart the lib scan then just bail
    if (needsAnImport || disableLibraryScanning) return;

    // List all of the thumbnail and remove those we don't need anymore
    // I know the client won't run this and that's what I want. Because a client
    // can connect to multiple servers you need to allow thumbnails in all cases
    // NOTE: This used to be done later in the import process but then we'd end up removing any
    // thumbnails that get generated before the scan is done for files that were just
    // imported on that scan.
    if (Sage.get("ui/thumbnail_folder", "GeneratedThumbnails").length() > 0)
    {
      File thumbFolder = new File(Sage.getPath("cache"),
          Sage.get("ui/thumbnail_folder", "GeneratedThumbnails"));
      File[] currentThumbs = thumbFolder.listFiles();
      if (currentThumbs != null && currentThumbs.length > 0)
      {
        HashSet<File> deadThumbs = new HashSet<File>();
        deadThumbs.addAll(Arrays.asList(currentThumbs));
        deadThumbs.removeAll(desiredThumbFiles);
        for (File f : deadThumbs)
        {
          if (!f.getName().startsWith("url-")) // retain cached URL images
          {
            if (Sage.DBG) System.out.println("Removing thumbnail file " + f + " because its source file is gone");
            f.delete();
          }
        }
      }
    }

    // If we need to restart the lib scan then just bail
    if (needsAnImport || disableLibraryScanning) return;

    if (isAutoImportEnabled())
    {
      Set<File> currArchiveDirSet = new HashSet<File>(Arrays.asList(archiveDirs));
      if (Sage.DBG) System.out.println("Scanning network to find any new shares...");
      boolean addedNewPaths = false;
      int maxDirCount = Sage.getInt("seeker/max_num_import_dirs", 100) - archiveDirs.length;
      String myHostname = "localhost";
      try
      {
        myHostname = java.net.InetAddress.getLocalHost().getHostName();
      }
      catch (Exception e){}
      try
      {
        SmbFile[] workgroups = new SmbFile("smb://").listFiles();
        for (int i = 0; workgroups != null && i < workgroups.length && maxDirCount > 0; i++)
        {
          if (Sage.DBG) System.out.println("workgroup=" + workgroups[i]);
          try
          {
            SmbFile[] servers = workgroups[i].listFiles();
            for (int j = 0; servers != null && j < servers.length && maxDirCount > 0; j++)
            {
              // Pause a bit here to prevent memory issues we've seen in embedded testing (was 100, but we saw it again)
              // Skip ourself
              if (myHostname.equalsIgnoreCase(servers[j].getServer()))
                continue;
              if (Sage.DBG) System.out.println("server=" + servers[j]);
              try
              {
                SmbFile[] shares = servers[j].listFiles();
                for (int k = 0; shares != null && k < shares.length && maxDirCount > 0; k++)
                {
                  SmbFile currShare = shares[k];
                  String shareName = currShare.getName();
                  if (Sage.DBG) System.out.println("share=" + currShare);
                  if ((currShare.getType() == SmbFile.TYPE_SHARE ||
                      currShare.getType() == SmbFile.TYPE_FILESYSTEM) &&
                      !shareName.endsWith("$") && !shareName.endsWith("$/") &&
                      currShare.canRead())
                  {
                    // See if we can also get a directory listing on this share
                    try
                    {
                      currShare.listFiles();
                      String localSmbPath = createLocalPathForSMBPath(currShare.toString());
                      if (!autoImportIgnorePaths.contains(localSmbPath) &&
                          !currArchiveDirSet.contains(new File(localSmbPath)))
                      {
                        if (Sage.DBG) System.out.println("Adding newly found network share to library import paths: " + currShare);
                        autoImportAddedNetPaths.add(localSmbPath);
                        addArchiveDirectory(currShare.toString(), ALL_DIR_MASK, false);
                        addedNewPaths = true;
                        maxDirCount--;
                        if (maxDirCount == 0 && Sage.DBG)
                          System.out.println("MAX import directory count reached!");
                      }
                    }
                    catch (jcifs.smb.SmbException smbe)
                    {
                      if (Sage.DBG) System.out.println("Ignoring share this scan because we can't list it's contents:" + smbe);
                    }
                  }
                }
              }
              catch (jcifs.smb.SmbException smbe)
              {
                if (Sage.DBG) System.out.println("ERROR2 w/ network scanning of: " + smbe);
                if (Sage.DBG) smbe.printStackTrace();
              }
            }
          }
          catch (jcifs.smb.SmbException smbe)
          {
            if (Sage.DBG) System.out.println("ERROR1 w/ network scanning of: " + smbe);
            if (Sage.DBG) smbe.printStackTrace();
          }
        }
      }
      catch (jcifs.smb.SmbException smbe)
      {
        if (Sage.DBG) System.out.println("ERROR0 w/ network scanning of: " + smbe);
        if (Sage.DBG) smbe.printStackTrace();
      }
      catch (java.net.MalformedURLException mue)
      {
        if (Sage.DBG) System.out.println("URL ERROR w/ network scanning of: " + mue);
        if (Sage.DBG) mue.printStackTrace();
      }
      if (addedNewPaths)
      {
        Sage.put(prefs + AUTO_IMPORT_ADDED_NETWORK_PATHS, Sage.createCommaDelimSetString(autoImportAddedNetPaths));
        establishMountPoints();
        writeArchiveDirsProps();
      }
    }
    else if (autoImportAddedNetPaths.size() > 0)
    {
      if (Sage.DBG) System.out.println("Autoimport is now disabled but there were automatically added paths, so remove them: " + autoImportAddedNetPaths);
      Iterator<String> walker = autoImportAddedNetPaths.iterator();
      while (walker.hasNext())
      {
        removeArchiveDirectory(new File(walker.next()), ALL_DIR_MASK, false);
        walker.remove();
      }
      Sage.put(prefs + AUTO_IMPORT_ADDED_NETWORK_PATHS, Sage.createCommaDelimSetString(autoImportAddedNetPaths));
      writeArchiveDirsProps();
    }

    if (Sage.getBoolean("seeker/enforce_minimum_import_sizes", false))
    {
      minMusicImportSize = Sage.getLong("seeker/min_file_size_music_import", 50*1024);
      minPicImportSize = Sage.getLong("seeker/min_file_size_pic_import", 50*1024);
      minJpgImportSize = Sage.getLong("seeker/min_file_size_jpg_import", 15*1024);
    }
    else
      minMusicImportSize = minPicImportSize = minJpgImportSize = 0;

    List<MediaFile> newlyAddedFiles = new Vector<MediaFile>();
    Set<String> remAccFileSet = accountedFileMap.keySet();
    Set<String> accFileSet = new HashSet<String>(remAccFileSet);
    File[] currArchiveDirs = archiveDirs;
    List<Object[]> playlistsToProcess = new ArrayList<Object[]>();
    int[] currMasks = archiveDirMasks;
    List<File> offlineRootsList = new ArrayList<File>();
    for (int i = 0; i < currArchiveDirs.length; i++)
    {
      // If we need to restart the lib scan then just bail
      if (needsAnImport || disableLibraryScanning) return;

      // This avoids importing from paths that are not in the allowed roots
      if (validRootImportPaths.length > 0)
      {
        boolean foundPath = false;
        for (int j = 0; j < validRootImportPaths.length && !foundPath; j++)
        {
          if (Sage.WINDOWS_OS ? currArchiveDirs[i].getAbsolutePath().toLowerCase().startsWith(validRootImportPaths[j].toLowerCase()) :
            currArchiveDirs[i].getAbsolutePath().startsWith(validRootImportPaths[j]))
          {
            foundPath = true;
          }
        }
        if (!foundPath)
          continue;
      }
      if (Sage.DBG) System.out.println("Starting to scan lib import root: " + currArchiveDirs[i]);
      if (currArchiveDirs[i].isDirectory())
      {
        importLibraryFiles(currArchiveDirs[i], currMasks[i], "", accFileSet, remAccFileSet,
            newlyAddedFiles, accountedFileMap, myFiles, playlistsToProcess);
      }
      else
      {
        if (Sage.DBG) System.out.println("Skipping import directory because it's offline: " + currArchiveDirs[i]);
        offlineRootsList.add(currArchiveDirs[i]);
      }
    }
    // Now that we're done importing files, we can analyze the playlists
    for (int i = 0; i < playlistsToProcess.size(); i++)
    {
      Object[] currData = playlistsToProcess.get(i);
      if (Playlist.importPlaylist((File)currData[0], (String)currData[1]) == null)
      {
        // Don't keep trying to reimport a bad playlist
        ignoreFiles.add((String) currData[2]);
      }
    }
    if (Sage.MAC_OS_X && Sage.getBoolean("macintosh/import_photos_from_iphoto", true))
    {
      importIPhotoFiles(accFileSet, remAccFileSet, newlyAddedFiles, accountedFileMap);
    }

    // If we need to restart the lib scan then just bail; but after this point don't bail again because now we're actually
    // done w/ the scan and are doing cleanup work
    if (needsAnImport || disableLibraryScanning) return;
    if (doThoroughLibScan)
    {
      lastThroughLibScanTime = Sage.time();
      doThoroughLibScan = false;
    }
    if (!hasDefaultDVDMF)
    {
      MediaFile dvdMF = wiz.addMediaFileSpecial(MediaFile.MEDIAFILE_DEFAULT_DVD_DRIVE, null, null,
          DBObject.MEDIA_MASK_DVD, null);
      if (Sage.DBG) System.out.println("Setup DVD Drive: " + dvdMF);
    }

    // Remove all of the files from the Video directories. These are candidates for auto import
    // if they become errant.
    accountedFileMap.keySet().removeAll(Arrays.asList(listAllFilesInVideoStorageDirsAsStrings()));

    // All of what's left in accounted files is no longer in a valid library path, remove them
    Set<MediaFile> invalidSet = new HashSet<MediaFile>(accountedFileMap.values());
    if (Sage.DBG && !mfsWithoutOwnAiring.isEmpty()) System.out.println("Seeker removing MediaFiles that don't have metadata descriptions so they can be updated:" + mfsWithoutOwnAiring);
    invalidSet.addAll(mfsWithoutOwnAiring);
    // If we're using advanced recovery and an old file path is no longer valid and it had its data moved to a new file path;
    // but retained the same MF; then we need to remove all of those MFs from this list so they remain in the DB
    invalidSet.removeAll(newlyAddedFiles);
    String[] offlineRoots = new String[offlineRootsList.size()];
    for (int i = 0; i < offlineRoots.length; i++)
      offlineRoots[i] = offlineRootsList.get(i).getAbsolutePath().toLowerCase();
    for (MediaFile currMF : invalidSet)
    {
      if (disableLibraryScanning) return;
      if (currMF.generalType != MediaFile.MEDIAFILE_IMPORTED ||
          (currMF.acquisitionTech != MediaFile.ACQUISITION_AUTOMATIC_BY_IMPORT_PATH &&
          currMF.acquisitionTech != MediaFile.ACQUISITION_AUTOMATIC_BY_VIDEO_STORAGE_PATH &&
          currMF.acquisitionTech != MediaFile.ACQUISITION_MANUAL))
      {
        // We only remove files if they were automatically imported, actually...or manually added
        // otherwise we'd have lots of trash left from plugin imports
        continue;
      }
      if (currMF.acquisitionTech == MediaFile.ACQUISITION_AUTOMATIC_BY_VIDEO_STORAGE_PATH ||
          currMF.acquisitionTech == MediaFile.ACQUISITION_MANUAL)
      {
        // We only remove re-imported or manually added video files if they're gone from the disk
        if (currMF.verifyFiles(true, false))
          continue;
      }
      // Check to make sure this file isn't offline before we go removing it!
      // NOTE: We used to create a list of 'offline files', but this had timing issues
      // related to it, so now we check it right when it's time to remove it.
      int numFiles = currMF.getNumSegments();
      boolean offline = false;
      for (int j = 0; j < numFiles && !offline; j++)
      {
        File currFile = currMF.getFile(j);
        // Check if this path is in an offline import root directory
        String absPathLC = null;
        // This is a faster way to deal with offline roots since sometimes SMB access is very slow in verifying that
        if (offlineRoots.length > 0)
        {
          absPathLC = currFile.getAbsolutePath().toLowerCase();
          for (int i = 0; i < offlineRoots.length; i++)
          {
            if (absPathLC.startsWith(offlineRoots[i]))
            {
              offline = true;
              break;
            }
          }
        }
        if (!offline && !currFile.exists()) // use exists() instead of isFile() so we detect DVD folders as well
        {
          if (absPathLC == null)
            absPathLC = currFile.getAbsolutePath().toLowerCase();
          // Check based on the import root; not the filesystem root. This is safer across platforms.
          // It's only offline if its root is still in the library path
          File rootFile = null;
          for (int k = 0; k < archiveDirs.length; k++)
          {
            if (absPathLC.startsWith(archiveDirs[k].toString().toLowerCase()))
            {
              rootFile = archiveDirs[k];
              break;
            }
          }
          if (rootFile != null && (!rootFile.isDirectory() || failedMounts.contains(rootFile.toString())))
          {
            offline = true;
          }
        }
      }
      if (offline)
        continue;
      if (Sage.DBG) System.out.println("Seeker removing file because it's not in the library import path anymore:" + currMF);
      contentCleared = (contentCleared || !(currMF.getContentAiring() instanceof MediaFile.FakeAiring));
      wiz.removeMediaFile(currMF);
      // We do this to ensure the Show/Airing objects are accessible in the event call
      if (currMF.getContentAiring() != null)
        currMF.getContentAiring().getShow();
      PluginEventManager.postEvent(PluginEventManager.MEDIA_FILE_REMOVED,
          new Object[] { PluginEventManager.VAR_MEDIAFILE, currMF, PluginEventManager.VAR_REASON, "ImportLost" });
    }
    if (contentCleared)
    {
      // We need to do a DB update to clear the bogus content
      Wizard.getInstance().clearMaintenanceTime();
      EPG.getInstance().kick();
    }

    if (!newlyAddedFiles.isEmpty() || contentCleared)
    {
      if (UIManager.getLocalUI() != null)
      {
        Catbert.distributeHookToAll("MediaFilesImported", new Object[] { newlyAddedFiles.toArray(new MediaFile[0]) });
      }
    }
    PluginEventManager.postEvent(PluginEventManager.IMPORTING_COMPLETED,
        new Object[] { PluginEventManager.VAR_FULLREINDEX, performFullContentReindex });

    // In case this got set
    if (Sage.getBoolean("force_full_content_reindex", false) && performFullContentReindex)
      Sage.putBoolean("force_full_content_reindex", false);
    if (performPicLibReindex)
      Sage.putBoolean("completed_exif_parser_import", true);
    performFullContentReindex = performPicLibReindex = false;
  }

  private void importLibraryFiles(File importDir, int importMask, String namePrefix,
      Set<String> accountedFiles, Set<String> remainingAccountedFiles,
      List<MediaFile> newlyAddedFiles, Map<String, MediaFile> accountedFileMap,
      MediaFile[] startMediaFileList, List<Object[]> playlistsToProcess)
  {
    if (prepped)
      controlImportCPUUsage();

    // If this is a video storage directory then skip it so we don't double import (if it's video)
    File[] vidDirs = getVideoStoreDirectories();
    for (int i = 0; i < vidDirs.length; i++)
      if (vidDirs[i].equals(importDir))
      {
        importMask = importMask & ~VIDEO_DIR_MASK;
        if (importMask == 0)
        {
          if (Sage.DBG) System.out.println("Skipping video import dir that's also a recording dir:" + vidDirs[i]);
          return;
        }
      }

    String[] testFiles = importDir.list();
    String importDirPath = importDir.getAbsolutePath();
    if (!importDirPath.endsWith(File.separator))
      importDirPath += File.separatorChar;
    // Sort the file list so that we'll hit them in ascending order
    if (testFiles != null)
      Arrays.sort(testFiles);
    for (int i = 0; (testFiles != null) && i < testFiles.length; i++)
    {
      // If we need to restart the lib scan then just bail
      if (needsAnImport || disableLibraryScanning) return;

      if (testFiles[i] == null || testFiles[i].length() == 0)
      {
        if (Sage.DBG) System.out.println("Skipping null file in import directory: " + importDir);
        continue;
      }
      String currFilename = importDirPath + testFiles[i];
      if (ignoreFiles.contains(currFilename))
      {
        remainingAccountedFiles.remove(currFilename);
        continue;
      }
      // Speed up the hidden file test on Linux
      if (Sage.LINUX_OS && testFiles[i].charAt(0) == '.') continue;
      File currFile = new File(currFilename);
      if (!Sage.LINUX_OS && currFile.isHidden()) continue; // ignore hidden directories as well
      if (currFile.isDirectory())
      {
        // Check for a DVD volume
        boolean nestedVolume = testFiles[i].equalsIgnoreCase(DVD_VOLUME_SECRET) ||
            testFiles[i].equalsIgnoreCase(BLURAY_VOLUME_SECRET);
        if (nestedVolume ||
            new File(currFile, "VIDEO_TS.IFO").isFile() ||
            (new File(currFile, "index.bdmv").isFile() && new File(currFile, "MovieObject.bdmv").isFile()))
        {
          if ((importMask & VIDEO_DIR_MASK) != 0)
          {
            if (accountedFiles.contains(currFilename))
            {
              checkForDataReimport(namePrefix, accountedFileMap.get(currFile.getAbsolutePath()));
              remainingAccountedFiles.remove(currFilename);
              if (nestedVolume)
                return;
              else
                continue;
            }
            MediaFile addedFile = wiz.addMediaFile(currFile, namePrefix, MediaFile.ACQUISITION_AUTOMATIC_BY_IMPORT_PATH);
            if (addedFile != null)
            {
              if (Sage.DBG) System.out.println("New Library DVD/BluRay " + addedFile);
              newlyAddedFiles.add(addedFile);
              PluginEventManager.postEvent(PluginEventManager.MEDIA_FILE_IMPORTED,
                  new Object[] { PluginEventManager.VAR_MEDIAFILE, addedFile });
            }
          }
          if (nestedVolume)
            return;
          else
            continue;
        }
        // Ignore directories that start with a '.' character (updated to be Linux only)
        if (Sage.LINUX_OS && testFiles[i].length() > 0 && testFiles[i].charAt(0) == '.')
          continue;
        importLibraryFiles(currFile, importMask, namePrefix + testFiles[i] + '/', accountedFiles,
            remainingAccountedFiles, newlyAddedFiles, accountedFileMap, startMediaFileList, playlistsToProcess);
        continue;
      }
      if (testFiles[i].indexOf('.') == -1) continue;
      String ext = testFiles[i].substring(testFiles[i].lastIndexOf('.')).toLowerCase();
      if ((((importMask & VIDEO_DIR_MASK) == 0) || (!vidLibFileExts.contains(ext) && !playlistFileExts.contains(ext))) &&
          (((importMask & MUSIC_DIR_MASK) == 0) || (!musicLibFileExts.contains(ext) && !playlistFileExts.contains(ext))) &&
          (((importMask & PICTURE_DIR_MASK) == 0) || !picLibFileExts.contains(ext)))
        continue;
      if (playlistFileExts.contains(ext) && ((importMask & (MUSIC_DIR_MASK | VIDEO_DIR_MASK)) != 0))
      {
        playlistsToProcess.add(new Object[] { currFile, namePrefix, currFilename });
        continue;
      }
      // Do this after the check so if they change the import type of a path its OK
      if (accountedFiles.contains(currFilename))
      {
        checkForDataReimport(namePrefix, accountedFileMap.get(currFile.getAbsolutePath()));
        remainingAccountedFiles.remove(currFilename);
        continue;
      }
      long currFileLength = currFile.length();
      // Skip files that are too short in length
      if (currFileLength == 0 ||
          (((currFileLength < minPicImportSize && !".jpg".equals(ext) && !".jpeg".equals(ext)) ||
              (currFileLength < minJpgImportSize && !".gif".equals(ext) && !".png".equals(ext))) &&
              ((importMask & PICTURE_DIR_MASK) != 0) && picLibFileExts.contains(ext)) ||
              (currFileLength < minMusicImportSize && ((importMask & MUSIC_DIR_MASK) != 0) && musicLibFileExts.contains(ext)))
        continue;
      // Make sure we can access the file. i.e. something else isn't still writing it
      if (!checkFileAccess(currFile))
      {
        if (Sage.DBG) System.out.println("Ignoring file for import because we can't get access to it:" + currFile);
        continue;
      }
      if (Sage.DBG) System.out.println("testFile=" + testFiles[i]);
      if (Sage.getBoolean("advanced_video_file_recovery", false))
      {
        MediaFile matchFile = doAdvancedFileRecovery(startMediaFileList, currFile);
        if (matchFile != null)
        {
          // We found an already existing import file which matches this path and no longer exists; so use that instead
          newlyAddedFiles.add(matchFile);
          continue;
        }
      }
      // Check for preservation of metadata; only relative for video files;
      // do this before we add the file so we can correct modification timestamps on files
      Airing air = null;
      if ((importMask & VIDEO_DIR_MASK) == VIDEO_DIR_MASK && !Sage.getBoolean("seeker/ignore_airing_ids_in_imported_filenames", false))
      {
        try
        {
          String name = currFile.getName();
          int idx1 = name.lastIndexOf('-');
          if (idx1 != -1)
          {
            int idx2 = name.lastIndexOf('-', idx1 - 1);
            if (idx2 != -1)
            {
              int airingID = Integer.parseInt(name.substring(idx2 + 1, idx1));
              air = wiz.getAiringForID(airingID);
            }
          }
        }
        catch (Exception nfe){}
        if (air != null && air.isTV())
        {
          if (Sage.DBG) System.out.println("Autoupdated reimported MediaFile timestamp for airing info air=" + air);
          currFile.setLastModified(air.getEndTime());
        }
      }
      // This'll return null if it's a failed add
      MediaFile addedFile = wiz.addMediaFile(currFile, namePrefix, MediaFile.ACQUISITION_AUTOMATIC_BY_IMPORT_PATH);
      if (addedFile != null)
      {
        if (Sage.DBG) System.out.println("New Library File " + addedFile);
        newlyAddedFiles.add(addedFile);
        if ((addedFile.isVideo() || addedFile.isTV()) && air != null && air.isTV())
        {
          if (Sage.DBG) System.out.println("Autoupdated airing info for imported mediafile air=" + air + " mf=" + addedFile);
          addedFile.setInfoAiring(air);
        }
        PluginEventManager.postEvent(PluginEventManager.MEDIA_FILE_IMPORTED,
            new Object[] { PluginEventManager.VAR_MEDIAFILE, addedFile });
      }
      else
        ignoreFiles.add(currFilename);
    }
  }

  private void importIPhotoFiles(Set<String> accountedFiles, Set<String> remainingAccountedFiles,
      List<MediaFile> newlyAddedFiles, Map<String, MediaFile> accountedFileMap)
  {
    // Use regex to extract all of the image paths from the iPhoto AlbumData.xml file
    File[] userDirs = new File("/Users").listFiles();
    if (userDirs == null || userDirs.length == 0)
      return;
    for (int u = 0; u < userDirs.length; u++)
    {
      File iphotoData = new File(userDirs[u], "Pictures/iPhoto Library/AlbumData.xml");
      if (!iphotoData.isFile())
        continue;
      java.io.BufferedReader is = null;
      String filePrefix = new File(userDirs[u], "Pictures/iPhoto Library/").toString();
      try
      {
        is = new java.io.BufferedReader(new FileReader(iphotoData));
        String currLine = is.readLine();
        while (currLine != null)
        {
          if (currLine.indexOf(">ImagePath<") != -1)
          {
            currLine = is.readLine();
            if (currLine != null)
            {
              int left = currLine.indexOf(">");
              int right = currLine.lastIndexOf("<");
              if (left != -1 && right != -1)
              {
                String currFilename = currLine.substring(left + 1, right);
                if (ignoreFiles.contains(currFilename))
                {
                  remainingAccountedFiles.remove(currFilename);
                  continue;
                }
                File currFile = new File(currFilename);
                if (currFile.isFile())
                {
                  if (currFilename.indexOf('.') == -1) continue;
                  if (currFile.length() == 0) continue;
                  String ext = currFilename.substring(currFilename.lastIndexOf('.')).toLowerCase();
                  if (!picLibFileExts.contains(ext))
                    continue;
                  // Do this after the check so if they change the import type of a path its OK
                  if (accountedFiles.contains(currFilename))
                  {
                    checkForDataReimport(filePrefix, accountedFileMap.get(currFile.getAbsolutePath()));
                    remainingAccountedFiles.remove(currFilename);
                    continue;
                  }
                  // Make sure we can access the file. i.e. something else isn't still writing it
                  if (!checkFileAccess(currFile))
                  {
                    if (Sage.DBG) System.out.println("Ignoring file for import because we can't get access to it:" + currFile);
                    continue;
                  }
                  if (Sage.DBG) System.out.println("testFile=" + currFilename);
                  // This'll return null if it's a failed add
                  MediaFile addedFile = wiz.addMediaFile(currFile, filePrefix, MediaFile.ACQUISITION_AUTOMATIC_BY_IMPORT_PATH);
                  if (addedFile != null)
                  {
                    if (Sage.DBG) System.out.println("New Library File " + addedFile);
                    newlyAddedFiles.add(addedFile);
                    PluginEventManager.postEvent(PluginEventManager.MEDIA_FILE_IMPORTED,
                        new Object[] { PluginEventManager.VAR_MEDIAFILE, addedFile });
                  }
                  else
                    ignoreFiles.add(currFilename);
                }
              }
            }
          }
          currLine = is.readLine();
        }
      }
      catch (java.io.IOException e)
      {
        if (Sage.DBG) System.out.println("ERROR parsing iTunes iPhoto file of " + e);
      }
      finally
      {
        if (is != null)
        {
          try
          {
            is.close();
          }catch (Exception e){}
          is = null;
        }
      }
    }
  }

  public void kick()
  {
    synchronized (this)
    {
      wasKicked = true;
      notifyAll();
    }
  }

  private void clientGCCleanup()
  {
    while (alive)
    {
      try
      {
        Thread.sleep(Sage.getLong("client_gc_period", 5*60*1000));
      }
      catch(Exception e){}
      // FORCE it because we want to do that somewhere in the app to ensure cleanup
      // over long periods of time.
      // NOTE: BUT DON'T DO THIS IF MEDIA IS PLAYING!!!
      if (Sage.getBoolean("allow_periodic_explicit_gc", false) && (!VideoFrame.isPlayinAny() || !VideoFrame.hasFileAny()))
      {
        if (Sage.DBG) System.out.println("Seeker invoking System.gc()");
        System.gc();
        if (Sage.DBG) System.out.println("Seeker System.gc()");
      }
      SageTV.writeOutWatchdogFile();
      // Need to do this somewhere. :)
      Sage.savePrefs();
      Iterator<UIManager> walker = UIManager.getUIIterator();
      while (walker.hasNext())
        walker.next().savePrefs();
    }
  }

  // NARFLEX - 5/12/09 - There used to be an argument for verifyDeletion which would ignore the return from mf.delete. This
  // was something we added at one point because we thought that mf.delete would return false if the file path didn't exist, but
  // that's not true. So now I'm putting this back to the old way so that the DeleteFile API call can actually return a proper value
  // and indicate to the user when there were failures trying to delete files.
  private Object destroyLock = new Object();
  public boolean destroyFile(MediaFile mf, boolean considerInProfile, String reason)
  {
    return destroyFile(mf, considerInProfile, reason, null);
  }
  public boolean destroyFile(MediaFile mf, boolean considerInProfile, String reason, String uiContext)
  {
    /*if (Sage.client)
{
  return SageTV.serverNotifier.deleteFile(mf, considerInProfile);
}*/
    if (Sage.DBG) System.out.println("Seeker.destroyFile called for: " + mf);
    // Don't do anything silly like delete these
    if (currRecordFiles.contains(mf))
    {
      if (Sage.DBG) System.out.println("Seeker not deleting file because it's a current recording: " + mf + " currRecordFiles=" + currRecordFiles);
      return false;
    }
    if (clientWatchFileMap.containsKey(mf))
    {
      if (Sage.DBG) System.out.println("Seeker not deleting file because it's in the client watch map: " + mf + " clientWatchMap=" + clientWatchFileMap);
      return false;
    }

    ManualRecord deadRecord = wiz.getManualRecord(mf.getContentAiring());
    // The file may be bad, but the MR might still be active so don't always kill it
    if (deadRecord != null && deadRecord.getEndTime() < Sage.time())
      wiz.removeManualRecord(deadRecord);
    synchronized (destroyLock)
    {
      // Ensure we don't try to delete a non-existent file....this can happen due to synchronization issues (verifyFiles has a list
      // of MFs which it walks through and the user could have deleted something manually during this traversal)
      boolean wizOK = wiz.isMediaFileOK(mf);
      if (Sage.DBG && !wizOK) System.out.println("Seeker not deleting file because it's no longer in the database: " + mf);
      if (wizOK && mf.delete(considerInProfile))
      {
        wiz.removeMediaFile(mf);
        // We do this to ensure the Show/Airing objects are accessible in the event call
        if (mf.getContentAiring() != null)
          mf.getContentAiring().getShow();
        PluginEventManager.postEvent(PluginEventManager.MEDIA_FILE_REMOVED,
            new Object[] { PluginEventManager.VAR_MEDIAFILE, mf, PluginEventManager.VAR_REASON, reason,
            PluginEventManager.VAR_UICONTEXT, uiContext});
        return true;
      }
      else
        return false;
    }
  }

  private void verifyFiles(boolean fixDurs, boolean avoidArchive)
  {
    if (Sage.DBG) System.out.println("Verifying existence of all TV media files in database fixDurs=" + fixDurs + " avoidArchive=" + avoidArchive);
    MediaFile[] myFiles = wiz.getFiles();
    boolean disablePathCheck = Sage.getBoolean("seeker/disable_root_path_existence_check", false);
    for (int i = 0; i < myFiles.length; i++)
    {
      if (myFiles[i].isTV() && (!avoidArchive || !myFiles[i].archive) && !currRecordFiles.contains(myFiles[i]) &&
          !clientWatchFileMap.containsKey(myFiles[i]) && !myFiles[i].verifyFiles(disablePathCheck, fixDurs))
      {
        if (Sage.DBG) System.out.println("Removing MediaFile because it failed verification:" + myFiles[i]);
        destroyFile(myFiles[i], false, "VerifyFailed");
      }
    }
    if (disablePathCheck)
      Sage.putBoolean("seeker/disable_root_path_existence_check", false);
  }

  private String createLocalPathForSMBPath(String s)
  {
    if (Sage.WINDOWS_OS)
    {
      try
      {
        return new SmbFile(s).getUncPath();
      }
      catch (Exception e)
      {
        if (Sage.DBG) System.out.println("ERROR w/ SMB URL:" + e);
        return s;
      }
    }
    String smbPrefix = Sage.get("linux/smb_mount_root", "/tmp/sagetv_shares/");
    s = s.substring("smb://".length());
    // Now modify the paths so it's only the share, not the subdirectory too
    int s1 = s.indexOf('/');
    int s2 = s.indexOf('/', s1 + 1);
    return smbPrefix + s.substring(0, s2).toLowerCase();
  }

  public File[] getArchiveDirectories(int importMask)
  {
    ArrayList<File> rv = new ArrayList<File>();
    for (int i = 0; i < archiveDirs.length; i++)
      if ((archiveDirMasks[i] & importMask) != 0)
        rv.add(archiveDirs[i]);
    return rv.toArray(new File[0]);
  }
  public void addArchiveDirectory(String s, int importMask)
  {
    addArchiveDirectory(s, importMask, true);
  }
  private void addArchiveDirectory(String s, int importMask, boolean triggerScan)
  {
    File f;
    if (Sage.LINUX_OS || Sage.MAC_OS_X)
    {
      if (s.startsWith("smb://"))
      {
        String smbPrefix = Sage.get("linux/smb_mount_root", "/tmp/sagetv_shares/");
        s = s.substring("smb://".length());
        String smbAuthInfo = "";
        int authIdx = s.indexOf('@');
        if (authIdx != -1)
        {
          smbAuthInfo = s.substring(0, authIdx + 1);
          s = s.substring(authIdx + 1);
        }
        // Now modify the paths so it's only the share, not the subdirectory too
        int s1 = s.indexOf('/');
        int s2 = s.indexOf('/', s1 + 1);
        String smbPath = "//" + smbAuthInfo + s.substring(0, s2).toLowerCase();
        String localPath = smbPrefix + s.substring(0, s2).toLowerCase();
        smbMountMap.put(smbPath, localPath);
        f = new File(localPath + s.substring(s2));
        if (triggerScan)
          establishMountPoints();
      }
      else if (s.startsWith("nfs://"))
      {
        String nfsPrefix = Sage.get("linux/nfs_mount_root", "/tmp/sagetv_shares/");
        s = s.substring("nfs://".length());
        int sidx = s.indexOf('/');
        String localPath = nfsPrefix + s;
        String nfsPath = s.substring(0, sidx) + ":" + s.substring(sidx);
        nfsMountMap.put(nfsPath, localPath);
        f = new File(localPath);
        if (triggerScan)
          establishMountPoints();
      }
      else
        f = new File(s);
    }
    else
    {
      if (s.startsWith("smb://"))
        s = IOUtils.convertSMBURLToUNCPath(s);
      f = new File(s);
    }
    f = f.getAbsoluteFile();
    int i = 0;
    for (; i < archiveDirs.length; i++)
      if (archiveDirs[i].equals(f))
      {
        archiveDirMasks[i] = archiveDirMasks[i] | importMask;
        break;
      }
    if (i == archiveDirs.length)
    {
      File[] oldF = archiveDirs;
      int[] oldMask = archiveDirMasks;
      archiveDirs = new File[oldF.length + 1];
      System.arraycopy(oldF, 0, archiveDirs, 0, oldF.length);
      archiveDirs[archiveDirs.length - 1] = f;
      archiveDirMasks = new int[archiveDirMasks.length + 1];
      System.arraycopy(oldMask, 0, archiveDirMasks, 0, oldMask.length);
      archiveDirMasks[archiveDirMasks.length - 1] = importMask;
    }
    // Check in case this was in the ignore paths and if so, remove it from that Set
    if (isAutoImportEnabled() && autoImportIgnorePaths.contains(f.toString()))
    {
      if (Sage.DBG) System.out.println("Library directory is no longer set to be ignored now:" + f);
      autoImportIgnorePaths.remove(f.toString());
      Sage.put(prefs + AUTO_IMPORT_IGNORE_PATHS, Sage.createCommaDelimSetString(autoImportIgnorePaths));
    }
    if (triggerScan)
      writeArchiveDirsProps();
    if (triggerScan && Sage.getBoolean("trigger_lib_scan_when_dirs_added_removed", true))
      scanLibrary(false);
  }
  public void removeArchiveDirectory(File f, int importMask)
  {
    removeArchiveDirectory(f, importMask, true);
  }
  private void removeArchiveDirectory(File f, int importMask, boolean triggerScan)
  {
    boolean didRemoveDirCompletely = false;
    for (int i = 0; i < archiveDirs.length; i++)
    {
      if (archiveDirs[i].equals(f))
      {
        archiveDirMasks[i] = archiveDirMasks[i] & ~importMask;
        if (archiveDirMasks[i] != 0)
          break;
        File[] oldF = archiveDirs;
        int[] oldMask = archiveDirMasks;
        archiveDirs = new File[oldF.length - 1];
        System.arraycopy(oldF, 0, archiveDirs, 0, i);
        System.arraycopy(oldF, i + 1, archiveDirs, i, oldF.length - i - 1);
        archiveDirMasks = new int[archiveDirMasks.length - 1];
        System.arraycopy(oldMask, 0, archiveDirMasks, 0, i);
        System.arraycopy(oldMask, i + 1, archiveDirMasks, i, oldMask.length - i - 1);
        didRemoveDirCompletely = true;
        break;
      }
    }
    if (isAutoImportEnabled() && f.toString().startsWith(Sage.WINDOWS_OS ? "\\\\" :
      Sage.get("linux/smb_mount_root", "/tmp/sagetv_shares/")) && didRemoveDirCompletely)
    {
      if (Sage.DBG) System.out.println("Library directory set to be ignored now:" + f);
      autoImportIgnorePaths.add(f.toString());
      Sage.put(prefs + AUTO_IMPORT_IGNORE_PATHS, Sage.createCommaDelimSetString(autoImportIgnorePaths));
    }
    if (triggerScan)
      writeArchiveDirsProps();
    if (triggerScan && Sage.getBoolean("trigger_lib_scan_when_dirs_added_removed", true))
      scanLibrary(false);
  }
  private void writeMountProps()
  {
    if (Sage.WINDOWS_OS) return;
    // Check to make sure all mount points are in use
    Set<String> remMounts = new HashSet<String>(smbMountMap.values());
    remMounts.addAll(nfsMountMap.values());
    for (int i = 0; i < archiveDirs.length; i++)
    {
      Iterator<String> walker = remMounts.iterator();
      while (walker.hasNext())
      {
        if (archiveDirs[i].getPath().startsWith(walker.next()))
        {
          walker.remove();
          break;
        }
      }
    }
    File[] vidDirs = getVideoStoreDirectories();
    for (int i = 0; i < vidDirs.length; i++)
    {
      Iterator<String> walker = remMounts.iterator();
      while (walker.hasNext())
      {
        if (vidDirs[i].getPath().startsWith(walker.next()))
        {
          walker.remove();
          break;
        }
      }
    }
    if (!remMounts.isEmpty())
    {
      if (Sage.DBG) System.out.println("Unused mount points: " + remMounts);
      unmountPaths(remMounts);
      smbMountMap.values().removeAll(remMounts);
      nfsMountMap.values().removeAll(remMounts);

      // Save the mount map too
      String mapStr = "";
      for (Map.Entry<String, String> ent : smbMountMap.entrySet())
      {
        String smbPath = ent.getKey();
        String localPath = ent.getValue();
        mapStr += smbPath + "," + localPath + ";";
      }
      Sage.put("linux/smb_mounts", mapStr);
      // Save the mount map too
      mapStr = "";
      for (Map.Entry<String, String> ent : nfsMountMap.entrySet())
      {
        String nfsPath = ent.getKey();
        String localPath = ent.getValue();
        mapStr += nfsPath + "," + localPath + ";";
      }
      Sage.put("linux/nfs_mounts", mapStr);
    }
  }
  private void writeArchiveDirsProps()
  {
    // Check to make sure all mount points are in use
    writeMountProps();
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < archiveDirs.length; i++)
    {
      sb.append(archiveDirs[i].toString()  + "," + archiveDirMasks[i] + ";");
    }
    Sage.put(prefs + ARCHIVE_DIRECTORY, sb.toString());
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        kick();
      }
    });
  }

  public File[] getSmbMountedFolders()
  {
    List<String> mountedPaths = new ArrayList<String>(smbMountMap.values());
    File[] rv = new File[mountedPaths.size()];
    for (int i = 0; i < rv.length; i++)
      rv[i] = new File(mountedPaths.get(i));
    return rv;
  }

  public boolean isSmbMountedFolder(File f)
  {
    return smbMountMap.containsValue(f.toString());
  }

  // [0] - Used, [1] - Total
  public long[] getUsedAndTotalVideoDiskspace()
  {
    long[] usedTotalRV = new long[2];
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
        videoStore.elementAt(i).addUsedTotalVideoDiskspace(usedTotalRV);
    }
    return usedTotalRV;
  }

  public long getAvailVideoDiskspace()
  {
    long freeSpace = 0;
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
        freeSpace += videoStore.elementAt(i).getAvailVideoDiskspace();
    }
    return freeSpace;
  }

  public long getUsedVideoDiskspace()
  {
    long usedSpace = 0;
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
        usedSpace += videoStore.elementAt(i).getUsedVideoDiskspace();
    }
    return usedSpace;
  }

  public long getTotalVideoDuration()
  {
    long totalDur = 0;
    MediaFile[] mFiles = wiz.getFiles();
    for (int k = 0; k < mFiles.length; k++)
    {
      if (!mFiles[k].archive && mFiles[k].isTV())
      {
        totalDur += mFiles[k].getRecordDuration();
      }
    }
    return totalDur;
  }

  public long getUsedImportedLibraryDiskspace()
  {
    MediaFile[] mFiles = wiz.getFiles();
    long totalSize = 0;
    for (int k = 0; k < mFiles.length; k++)
    {
      if (mFiles[k].getAcquistionTech() == MediaFile.ACQUISITION_AUTOMATIC_BY_IMPORT_PATH)
        totalSize += mFiles[k].getSize();
    }
    return totalSize;
  }

  public long getTotalImportedLibraryDuration()
  {
    long totalDur = 0;
    MediaFile[] mFiles = wiz.getFiles();
    for (int k = 0; k < mFiles.length; k++)
    {
      if (mFiles[k].getAcquistionTech() == MediaFile.ACQUISITION_AUTOMATIC_BY_IMPORT_PATH)
        totalDur += mFiles[k].getRecordDuration();
    }
    return totalDur;
  }

  private void removeFromCurrRecords(Airing x)
  {
    currRecords.remove(x);
    if (x instanceof ManualRecord.FakeAiring)
      currRecords.remove(((ManualRecord.FakeAiring) x).getManualRecord().getContentAiring());
  }

  private void addToCurrRecords(Airing x)
  {
    currRecords.add(x);
    if (x instanceof ManualRecord.FakeAiring)
      currRecords.add(((ManualRecord.FakeAiring) x).getManualRecord().getContentAiring());
  }

  private String getQualityForAiring(Airing air, CaptureDevice capDev, boolean liveClients)
  {
    String newQual = null;
    ManualRecord mr = wiz.getManualRecord(air);
    if (mr != null)
      newQual = mr.getRecordingQuality();
    if (newQual != null && newQual.length() == 0)
      newQual = null;
    Agent causeHead = null;
    if (newQual == null && (causeHead = god.getCauseAgent(air)) != null && causeHead.quality.length() > 0)
      newQual = causeHead.quality;
    if (newQual == null || newQual.length() == 0)
    {
      if (capDev.doesDeviceHaveDefaultQuality())
        newQual = capDev.getDefaultQuality();
      else if (liveClients)
      {
        newQual = Sage.get(prefs + "default_live_quality", "");
        if (newQual.trim().length() == 0)
          newQual = defaultQuality;
      }
      else
        newQual = defaultQuality;
    }

    if (newQual != null && newQual.length() > 0)
    {
      // Check to make sure the device supports the quality, if not then don't use it
      // because we'll end up tagging the file with an invalid quality & media type which is really bad.
      String[] devQuals = capDev.getEncodingQualities();
      for (int i = 0; i < devQuals.length; i++)
        if (devQuals[i].equals(newQual))
          return newQual;

      if (Sage.DBG) System.out.println("Not using desired quality setting of " + newQual + " because the capture device doesn't support it");
      // Quality not supported by device if we got here so use the default for the device
      newQual = capDev.getDefaultQuality();
    }
    return newQual;
  }

  private void startRecord(EncoderState encState, Airing me, long currTime, boolean switchIt)
  {
    if (Sage.DBG) System.out.println("Seeker.startRecord(" + encState.capDev + ' ' + me + ", currTime=" + Sage.df(currTime) + ") currRecord=" + encState.currRecord + " switch=" + switchIt);
    // Destroy any files that already exist for this one.
    if (encState.currRecord != null)
      removeFromCurrRecords(encState.currRecord);
    encState.currRecord = me;
    addToCurrRecords(encState.currRecord);
    if (encState.currRecordFile != null)
      currRecordFiles.remove(encState.currRecordFile);
    if (encState.capDev.canEncode())
    {
      encState.currRecordFile = wiz.getFileForAiring(encState.currRecord);
    }
    else
    {
      encState.currRecordFile = MediaFile.getLiveMediaFileForInput(encState.absoluteClientControl);
    }
    encState.doingStartup = true;
    encState.lastCheckedSize = 0;
    encState.lastSizeCheckTime = 0;

    if (encState.currRecordFile == encState.switcherFile && encState.switcherFile != null)
    {
      if (Sage.DBG) System.out.println("Finishing file switch early since we're not actually switching to a new file; only changing segments");
      // This is really only the metadata that's getting switch here; otherwise we'll screw up ending a segment
      // before we start the next one within the same MediaFile (but when it's across 2 different files its OK)
      completeFileSwitch(encState, currTime++);
    }

    CaptureDeviceInput mmcConn = null;
    if (encState.absoluteClientControl != null)
    {
      mmcConn = encState.absoluteClientControl;
      // For channel setting through clients on live preview
      mmcConn.syncLastChannel();
    }
    if (mmcConn == null)
    {
      // Sort this by input quality so that the highest quality input is picked first if the channel is on multiple inputs
      CaptureDeviceInput[] allEncConns = encState.capDev.getInputs();
      Arrays.sort(allEncConns, new Comparator<CaptureDeviceInput>()
      {
        public int compare(CaptureDeviceInput o1, CaptureDeviceInput o2)
        {
          CaptureDeviceInput c1 = o1;
          CaptureDeviceInput c2 = o2;
          return c2.getType() - c1.getType();
        }
      });
      Channel c = encState.currRecord.getChannel();
      for (int i = 0; i < allEncConns.length; i++)
      {
        mmcConn = allEncConns[i];
        if (c != null && c.isViewable(allEncConns[i].getProviderID()))
        {
          break;
        }
      }
    }

    // Check to make sure the capture device is loaded
    if (!encState.capDev.isLoaded())
    {
      try
      {
        encState.capDev.loadDevice();
      }
      catch (EncodingException e)
      {
        sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createDeviceLoadFailureMsg(encState.capDev));
        Catbert.distributeHookToAll("MediaPlayerError", new Object[] { Sage.rez("Capture"), e.getMessage() });
      }
    }

    // Stop any data scanning that the capture device is doing since we need to use it now
    if (encState.capDev.isDataScanning())
    {
      if (Sage.DBG) System.out.println("Stopping data scanning on capture device before we start recording.");
      encState.capDev.disableDataScanning();
    }

    if (Sage.DBG) System.out.println("Setting up MMC video for recording new show & tuning channel conn=" + mmcConn);
    encState.myQuality = currQuality = getQualityForAiring(encState.currRecord, encState.capDev, !encState.controllingClients.isEmpty());
    boolean fullChange = false;
    if (!mmcConn.isActive())
      fullChange = true;

    if (currQuality != null)
    {
      if (!currQuality.equals(encState.capDev.getEncodingQuality()) &&
          mmcConn.getType() != CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
        fullChange = true;
      encState.capDev.setEncodingQuality(currQuality);
    }
    if (Sage.DBG) System.out.println("Using quality setting \"" + currQuality + "\" for recording");

    Channel newChan = encState.currRecord.getChannel();
    // Check for a DTV station (US)
    String chanName = (newChan != null) ? newChan.getName() : "";
    String chanString = EPG.getInstance().getPhysicalChannel(mmcConn.getProviderID(), encState.currRecord.getStationID());

    if (switchIt && !fullChange && !chanString.equals(encState.capDev.getChannel()))
    {
      if (Sage.DBG) System.out.println("Disabling fast switch on new encoding since we're changing channels old=" + encState.capDev.getChannel() + " new=" + chanString);
      fullChange = true;
    }
    /*if (!encState.avSyncLockObtained &&
  System.currentTimeMillis() - encState.capDev.getRecordStart() < DURATION_FOR_AVSYNC_LOCK &&
  mmc.needsAVSyncManagement(encState.name))
  fullChange = true;
else
  encState.avSyncLockObtained = true;*/

    /*    // We handle all cases of PTS rollover during playback now and this will break our new fast mux switching on playback
if (encState.currRecord.getDuration() + (Sage.time() - encState.lastResetTime) > maxDurEncoderReset)
{
  // 2/20/2004 Only reset encoders that need A/V sync management. This should alleviate
  // a high percentage of the 350 lockups that occur after people use it for a day or so.
  if (Sage.DBG) System.out.println("Seeker resetting the encoder because it'll pass the max duration");
  fullChange = true;
}
     */
    long spaceMaybeNeeded = (encState.currRecord.getSchedulingEnd() - currTime) *
        ((currQuality != null && mmcConn.getType() != CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX) ? (mmc.getQualityOverallBitrate(currQuality)/8000) : (18000000/*18Mbps*//8000));
    // the 8k converts bps to Bpmsec

    if (encState.currRecordFile != null && !encState.currRecordFile.verifyFiles(true))
    {
      // Corruption in this prior record, kill it and create a fresh MF
      if (Sage.DBG) System.out.println("Removing MediaFile because it failed verification on startRecord:" + encState.currRecordFile);
      // NARFLEX - 6/9/09 - Just delete the segments from the file....since a client may be using the MF object for a
      // watch request already.
      encState.currRecordFile.delete(false);
      encState.currRecordFile.setMediafileFormat(mmcConn.getEncoderMediaFormat());
    }
    else if (encState.currRecordFile != null && !encState.currRecordFile.isAnyLiveStream())
    {
      sage.media.format.ContainerFormat oldFormat = encState.currRecordFile.getFileFormat();
      sage.media.format.ContainerFormat newFormat = mmcConn.getEncoderMediaFormat();
      if (oldFormat != null && newFormat != null &&
          ((!newFormat.getPrimaryVideoFormat().equals(oldFormat.getPrimaryVideoFormat()) && newFormat.getPrimaryVideoFormat().length() > 0) ||
              !newFormat.getFormatName().equals(oldFormat.getFormatName())))
      {
        // NOTE: If we don't do this somewhere its possible for the user to generate files for playback
        // that won't playback at all because it'll have inconsistent media types in it.
        // NOTE: BUT this is going to fail if they've already requested to view this file since it'll be in the client watch map!
        if (Sage.DBG) System.out.println("Removing MediaFile because of format change in startRecord:" + encState.currRecordFile);
        // NARFLEX - 6/9/09 - Just delete the segments from the file....since a client may be using the MF object for a
        // watch request already.
        encState.currRecordFile.delete(false);
        encState.currRecordFile.setMediafileFormat(newFormat);
      }
    }
    if (!switchIt || fullChange || !fastMuxSwitch || !encState.capDev.supportsFastMuxSwitch())
    {
      // Make sure the time doesn't align perfectly if we're not doing a fast switch
      currTime++;
    }

    String fileQualityName = encState.capDev + " ";
    if (mmcConn.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
    {
      fileQualityName += encState.currRecord.getChannelName();
    }
    else if (currQuality != null)
    {
      fileQualityName += currQuality;
    }
    // 12/6/02 This block used to be just after the if destroyfile above
    if (encState.currRecordFile == null)
    {
      VideoStorage bestStore = findBestStorageForSize(spaceMaybeNeeded, encState.capDev.getForcedVideoStoragePrefix());
      if (Sage.DBG) System.out.println("VideoStorage for new file: " + bestStore);
      if (bestStore == null) {
        encState.doingStartup = false;
        throw new IllegalArgumentException(Sage.rez("CAPTURE_ERROR_NO_RECORDING_DIRECTORY"));
      }
			sage.media.format.ContainerFormat cf = mmcConn.getEncoderMediaFormat();
      encState.currRecordFile = wiz.addMediaFile(encState.currRecord, currTime, bestStore.videoDir.toString(),
          fileQualityName, mmcConn.getProviderID(),
          ((DBObject.MEDIA_MASK_VIDEO | DBObject.MEDIA_MASK_TV) & DBObject.MEDIA_MASK_ALL), cf);
      if (wiz.getManualRecord(encState.currRecord) != null)
        encState.currRecordFile.setAcquisitionTech(MediaFile.ACQUISITION_MANUAL);
      else if (encState.currRecord.isMustSee())
        encState.currRecordFile.setAcquisitionTech(MediaFile.ACQUISITION_FAVORITE);
      else if (!encState.controllingClients.isEmpty())
        encState.currRecordFile.setAcquisitionTech(MediaFile.ACQUISITION_WATCH_BUFFER);
      else
        encState.currRecordFile.setAcquisitionTech(MediaFile.ACQUISITION_INTELLIGENT);
    }
    else if (!encState.currRecordFile.isLiveStream())
    {
      encState.currRecordFile.startSegment(currTime, fileQualityName);
      if (encState.currRecordFile.isLiveBufferedStream())
        spaceMaybeNeeded = 0;
    }
    currRecordFiles.add(encState.currRecordFile);
    if (!encState.currRecordFile.isLiveStream())
    {
      VideoStorage myvs = getStorageForFile(encState.currRecordFile);
      if (myvs != null)
      {
        synchronized (myvs.fileSpaceReserves)
        {
          myvs.fileSpaceReserves.put(encState, spaceMaybeNeeded);
        }
      }
    }
    else // I'm not sure why I had fullChange set for live streams. Because we have to stop the graph!
      fullChange = true;

    // We also need to do a full change if we're modifying the recording buffer size.
    long currRecBuffSize = encState.capDev.getRecordBufferSize();
    fullChange |= encState.currRecordFile.isLiveBufferedStream()
        ? (currRecBuffSize != encState.currRecordFile.getStreamBufferSize())
        : (currRecBuffSize != 0);

    // At this point the new file is created of zero length by one of the above operations

    if (fullChange || !fastMuxSwitch || !encState.capDev.supportsFastMuxSwitch())
    {
      if (switchIt)
      {
        // We need to kill the encoding instead for this.
        if (Sage.DBG) System.out.println("stopping the current encoding...");
        switchIt = false;
        encState.capDev.stopEncoding();
        completeFileSwitch(encState, currTime);
      }
    }

    Sage.putInt(prefs + LAST_STATION_ID + '/' + encState.capDev,
        encState.lastStationID = encState.currRecord.getStationID());

    if (Sage.DBG) System.out.println("Seeker channel string=" + chanString);
    if (encState.currRecordFile.isLiveBufferedStream())
    {
      encState.capDev.setRecordBufferSize(encState.currRecordFile.getStreamBufferSize());

      // Media format can change on this, so reset it now
			encState.currRecordFile.setMediafileFormat(mmcConn.getEncoderMediaFormat());
    }
    else
      encState.capDev.setRecordBufferSize(0);
    if (encState.currRecordFile.isLiveStream())
			encState.currRecordFile.setMediafileFormat(mmcConn.getEncoderMediaFormat());
    try
    {
      if (!encState.currRecordFile.isLiveStream())
      {
        if (switchIt && fastMuxSwitch && encState.capDev.supportsFastMuxSwitch())
        {
          if (Sage.DBG) System.out.println("About to call switch encoding on " + encState.capDev);
          // We won't get a new media format message on this switch so preserve the format information from it. But rebuild the object
          // so we don't have metadata sharing issues!
          if (encState.switcherFile != null)
          {
            sage.media.format.ContainerFormat cf = encState.switcherFile.getFileFormat();
            if (cf != null)
              encState.currRecordFile.setMediafileFormat(sage.media.format.ContainerFormat.buildFormatFromString(cf.getFullPropertyString(false)));
          }
          encState.capDev.switchEncoding(encState.currRecordFile.getRecordingFile().toString(), chanString);
          if (Sage.DBG) System.out.println("Done with switch encoding on " + encState.capDev);
          completeFileSwitch(encState, currTime);
        }
        else
        {
          encState.capDev.startEncoding(mmcConn, encState.currRecordFile.getRecordingFile().toString(), chanString);
        }
      }
      else
      {
        encState.capDev.applyQuality();
        encState.capDev.activateInput(mmcConn);
        // Fixup any issue with the last channel being set incorrectly from a WatchLive call which would then
        // cause the always_tune_channel to potentially skip it
        mmcConn.setLastChannel("");
        encState.capDev.tuneToChannel(chanString);
      }
    }
    catch (EncodingException e)
    {
      sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createDeviceRecordFailureMsg(encState.capDev.getActiveInput(),
          encState.currRecord, encState.currRecord.getChannel(),
          EPG.getInstance().getPhysicalChannel(encState.capDev.getActiveInput() != null ?
              encState.capDev.getActiveInput().getProviderID() : 0, encState.currRecord.getStationID())));
      Catbert.distributeHookToAll("MediaPlayerError", new Object[] { Sage.rez("Capture"), e.getMessage() });
    }
    if (!encState.currRecordFile.isAnyLiveStream())
      PluginEventManager.postEvent(PluginEventManager.RECORDING_STARTED,
          new Object[] { PluginEventManager.VAR_MEDIAFILE, encState.currRecordFile });
    encState.doingStartup = false;
  }

  public MediaFile getCurrRecordMediaFile(CaptureDevice capDev)
  {
    EncoderState es = encoderStateMap.get(capDev);
    return (es != null) ? es.currRecordFile : null;
  }

  public MediaFile getCurrRecordFileForClient(UIClient uiClient)
  {
    return getCurrRecordFileForClient(uiClient, true);
  }
  public MediaFile getCurrRecordFileForClient(UIClient uiClient, boolean syncLock)
  {
    if (Sage.client)
      return NetworkClient.getSN().getCurrRecordFileForClient(uiClient);
    if (syncLock)
    {
      synchronized (this)
      {
        for (EncoderState encState : encoderStateMap.values())
        {
          if (encState.controllingClients.contains(uiClient))
            return encState.currRecordFile;
        }
      }
    }
    else
    {
      List<EncoderState> encStateVec = new ArrayList<EncoderState>(encoderStateMap.values());
      for (int i = 0; i < encStateVec.size(); i++)
      {
        if (encStateVec.get(i).controllingClients.contains(uiClient))
          return encStateVec.get(i).currRecordFile;
      }
    }
    return null;
  }

  // NOTE: This call should NOT be made from SageTVClient
  public CaptureDevice getCaptureDeviceControlledByClient(UIClient uiClient)
  {
    if (Sage.client) return null;
    List<EncoderState> encStateVec = new ArrayList<EncoderState>(encoderStateMap.values());
    for (int i = 0; i < encStateVec.size(); i++)
    {
      if (encStateVec.get(i).controllingClients.contains(uiClient))
        return encStateVec.get(i).capDev;
    }
    return null;
  }

  public MediaFile[] getCurrRecordFiles()
  {
    if (Sage.client)
      return NetworkClient.getSN().getCurrRecordFiles();
    else
      return currRecordFiles.toArray(new MediaFile[0]);
  }

  public boolean isAClientControllingCaptureDevice(CaptureDevice capDev)
  {
    EncoderState es = encoderStateMap.get(capDev);
    return es != null && !es.controllingClients.isEmpty();
  }

  public void checkForEncoderHalts()
  {
    long encHaltDur = Sage.getLong("seeker/duration_for_halt_detection", 60000);
    EncoderState[] allES = encoderStateMap.values().toArray(new EncoderState[0]);
    boolean kickMe = false;
    for (int i = 0; i < allES.length; i++)
    {
      EncoderState encState = allES[i];
      MediaFile mf = encState.currRecordFile;
      if (mf == null || mf.isAnyLiveStream() || encState.doingStartup)
        continue;
      long recLength = encState.capDev.getRecordedBytes();
      if (encState.lastSizeCheckTime != 0)
      {
        if (Sage.time() - encState.lastSizeCheckTime > encHaltDur && encState.lastCheckedSize == recLength)
        {
          // Kick the Seeker so it notices this as well and then does the reset
          if (Sage.DBG) System.out.println("Async encoder halt detector found a halt; kick the Seeker so it can restart the device file=" + encState.currRecordFile + " size=" + recLength);
          kickMe = true;
        }
        else if (encState.lastCheckedSize != recLength && encState.currRecordFile == mf)
        {
          encState.lastSizeCheckTime = Sage.time();
          encState.lastCheckedSize = recLength;
        }
      }
      else if (encState.currRecordFile == mf)
      {
        encState.lastSizeCheckTime = Sage.time();
        encState.lastCheckedSize = recLength;
      }
    }
    if (kickMe)
      kick();
  }

  private void checkFileSizeLimit()
  {
    long encHaltDur = Sage.getLong("seeker/duration_for_halt_detection", 60000);
    synchronized (this)
    {
      for (EncoderState encState : encoderStateMap.values())
      {
        if (encState.currRecordFile == null || encState.currRecordFile.isAnyLiveStream())
          continue;
        File testFile = encState.currRecordFile.getRecordingFile();
        File rootFile = IOUtils.getRootDirectory(testFile.getAbsoluteFile());
        long recLength = encState.capDev.getRecordedBytes();
        String fsType = Sage.getFileSystemTypeX(rootFile.toString());
        if (Sage.DBG) System.out.println("RootFile=" + rootFile + " fstype=" + fsType +
            " fileLength=" + recLength + " file=" + testFile);
        // NOTE: This is to deal with the DivX AVI file writer not supporting AVI 2.0 so it
        // suffers from the 4GB file boundary limitation. Instead of dealing with it in the transcode
        // we just take care of it now. This is much easier to handle.
        if ((fsType != null && fsType.toLowerCase().indexOf("fat") != -1) ||
            (encState.currRecordFile.getPrimaryVideoFormat().equals(sage.media.format.MediaFormat.MPEG4X) &&
                encState.currRecordFile.getContainerFormat().equals(sage.media.format.MediaFormat.MPEG2_PS)) ||
                (sage.media.format.MediaFormat.AVI.equals(encState.currRecordFile.getContainerFormat()) &&
                    Sage.getBoolean("seeker/break_avi_recordings_at_4GB", true)))
        {
          // We've got a FAT file system to deal with. Check the file size limit.
          if (recLength > FAT_FILE_SIZE_LIMIT) // 3.5GB
          {
            Sage.processInactiveFile(encState.currRecordFile.getRecordingFile().toString());
            try
            {
              encState.capDev.stopEncoding();
              encState.currRecordFile.rollSegment(false);
              encState.capDev.startEncoding(null, encState.currRecordFile.getRecordingFile().toString(), "");
              PluginEventManager.postEvent(PluginEventManager.RECORDING_SEGMENT_ADDED,
                  new Object[] { PluginEventManager.VAR_MEDIAFILE, encState.currRecordFile });
            }
            catch (EncodingException e)
            {
              sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createDeviceRecordFailureMsg(encState.capDev.getActiveInput(),
                  encState.currRecord, encState.currRecord.getChannel(),
                  EPG.getInstance().getPhysicalChannel(encState.capDev.getActiveInput() != null ?
                      encState.capDev.getActiveInput().getProviderID() : 0, encState.currRecord.getStationID())));
              // Don't send a hook for this....the system message will do the job and otherwise the errors can stack up
              //Catbert.distributeHookToAll("MediaPlayerError", new Object[] { Sage.rez("Capture"), e.getMessage() });
            }
            encState.lastCheckedSize = encState.lastSizeCheckTime = 0;
          }
        }
        if (encState.lastSizeCheckTime != 0 || encState.resetRequested)
        {
          if (encState.resetRequested || (Sage.time() - encState.lastSizeCheckTime > encHaltDur &&
              encState.lastCheckedSize == recLength))
          {
            // See if there's already a dead file...if so, just give up.
            /*if (recLength == 0 && encState.resetCount > 0)
        {
          // We've effectively lost the ability to record...which is the MMC
          if (Sage.DBG) System.out.println("SEEKER IS ABANDONING THE ENCODER...IT'S DEAD");
          // THIS WAS KILLING THE SYSTEM WHEN WE SHOULDN'T HAVE BEEN SO LETS JUST LEAVE
          // IT RUNNING AND HOPE ALL WILL BE WELL
//              mmcPresent = false;
//              javax.swing.JOptionPane.showMessageDialog(null,
//                "The Video Capture hardware has stopped producing data.\n" +
//                "Please shutdown SageTV and restart your computer to correct the problem");
        }


        else*/ // Keep trying to reset it every 5 minutes for now until we do device recovery
            {
              if (encState.resetRequested)
              {
                if (Sage.DBG) System.out.println("SEEKER is forcibly resetting the encoder due to a request to do so");
                encState.currRecordFile.addMetadata("VARIED_FORMAT", "true");
              }
              else if (Sage.DBG)
                System.out.println("SEEKER HAS DETECTED A HALT IN THE ENCODER...TRYING TO RESET IT file=" + encState.currRecordFile.getRecordingFile() + " size=" + recLength);
              Sage.processInactiveFile(encState.currRecordFile.getRecordingFile().toString());
              encState.capDev.stopEncoding();
              // Remove the dead segments (it'll leave at least one though)
              encState.currRecordFile.rollSegment(true);
              boolean loadedOK = false;
              try
              {
                // Completely unload the device and destroy the graph, don't just stop the encoding!
                if (encState.capDev.getActiveInput() == null || encState.capDev.getActiveInput().getType() != CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX ||
                    Sage.getBoolean("mmc/fully_reload_digital_tuners_on_halt_detection", false))
                {
                  encState.capDev.freeDevice();
                  encState.capDev.loadDevice();
                }
                loadedOK = true;
                encState.capDev.startEncoding(null, encState.currRecordFile.getRecordingFile().toString(), "");
                PluginEventManager.postEvent(PluginEventManager.RECORDING_SEGMENT_ADDED,
                    new Object[] { PluginEventManager.VAR_MEDIAFILE, encState.currRecordFile });
              }
              catch (EncodingException e)
              {
                if (!loadedOK)
                  sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createDeviceLoadFailureMsg(encState.capDev));
                else
                  sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createDeviceRecordFailureMsg(encState.capDev.getActiveInput(),
                      encState.currRecord, encState.currRecord.getChannel(),
                      EPG.getInstance().getPhysicalChannel(encState.capDev.getActiveInput() != null ?
                          encState.capDev.getActiveInput().getProviderID() : 0, encState.currRecord.getStationID())));
                // Don't send a hook for this....the system message will do the job and otherwise the errors can stack up
                //Catbert.distributeHookToAll("MediaPlayerError", new Object[] { Sage.rez("Capture"), e.getMessage() });
              }
              encState.resetCount++;
                sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createEncoderHaltMsg(encState.capDev, encState.capDev.getActiveInput(),
                    encState.currRecord, encState.currRecord.getChannel(),
                    EPG.getInstance().getPhysicalChannel(encState.capDev.getActiveInput() != null ?
                        encState.capDev.getActiveInput().getProviderID() : 0, encState.currRecord.getStationID()),
                      encState.resetCount));
              encState.resetRequested = false;
              encState.lastSizeCheckTime = 0;
              encState.lastCheckedSize = 0;
            }
          }
          else if (encState.lastCheckedSize != recLength)
          {
            encState.resetCount = 0;
            encState.lastSizeCheckTime = Sage.time();
            encState.lastCheckedSize = recLength;
          }
        }
        else
        {
          encState.lastSizeCheckTime = Sage.time();
          encState.lastCheckedSize = recLength;
        }
      }
    }
    VideoFrame.kickAll();
  }

  // This clones what's in endRecord for when we need to cleanup after a fast file switch
  private void completeFileSwitch(EncoderState es, long currTime)
  {
    if (es.switcherFile == null) return;
    if (!es.switcherFile.isLiveStream())
    {
      Sage.processInactiveFile(es.switcherFile.getRecordingFile().toString());
    }
    // If we covered enough time with this record to count for a full
    // one, then we add it into the ignore list
    // Find the aggregate start/stop for this watch
    if (!es.switcherFile.isLiveStream())
    {
      es.switcherFile.endSegment(currTime);
    }

    /*
     * NOTE: When we finish recording a favorite, but it's not complete, we want to
     * mark it as complete if its not on again otherwise we could lose some desired content.
     * But don't do it if we've got less than 1/4 of it
     */
    if (!es.switcherFile.isCompleteRecording() && god.isLoveAir(es.switcherAir) &&
        es.switcherFile.getRecordDuration() > es.switcherAir.getSchedulingDuration()/4)
    {
      if (!BigBrother.isUnique(es.switcherAir) || (!es.switcherAir.hasLaterWatchableAiring() && !es.switcherAir.isWatched()))
      {
        if (Sage.DBG) System.out.println("Forcing completion of partially recorded favorite file:" + es.switcherFile);
        // Only post the system message if they're not watching it...since if they are they'd know about the issue
        // Also don't post it if we're shutting down which would be something the user is forcing the system to do (and since it'll likely just come back up shortly)
        // Also don't post it if it was marked watched since that could cause it to stop early
        if (!isMediaFileBeingViewed(es.switcherFile) && SageTV.isAlive() && !BigBrother.isWatched(es.switcherAir))
          sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createPartialRecordingFromConflictMsg(es.switcherAir, es.switcherAir.getChannel()));
        es.switcherFile.thisIsComplete();
      }
    }

    if (!es.switcherFile.isAnyLiveStream())
      PluginEventManager.postEvent(PluginEventManager.RECORDING_STOPPED,
          new Object[] { PluginEventManager.VAR_MEDIAFILE, es.switcherFile });

    // General file export plugins
    if (es.switcherFile.isCompleteRecording())
    {
      processFileExport(es.switcherFile.getFiles(), es.switcherFile.acquisitionTech);

      // Setup persistence for the Airing this MediaFile is based on to handle later recovery
      es.switcherAir.setPersist(Airing.PERSIST_TV_MEDIAFILE_LINK);

      // Update the acquisition technique to match the current requested record state for this item so we don't have
      // files marked as being from the watch buffer if the user added them as a record/favorite during recording time.
      if (Wizard.getInstance().getManualRecord(es.switcherAir) != null)
      {
        es.switcherFile.setAcquisitionTech(MediaFile.ACQUISITION_MANUAL);
      }
      else if (es.switcherAir.isMustSee())
      {
        es.switcherFile.setAcquisitionTech(MediaFile.ACQUISITION_FAVORITE);
      }

      // Store the metadata information in the recorded file
      if (Sage.getBoolean("seeker/mpeg_metadata_embedding", true))
        sage.media.format.MpegMetadata.addMediaFileMetadata(es.switcherFile);
      if (!es.switcherFile.isAnyLiveStream())
        PluginEventManager.postEvent(PluginEventManager.RECORDING_COMPLETED,
            new Object[] { PluginEventManager.VAR_MEDIAFILE, es.switcherFile });
    }

    // Internal transcoding engine
    if (alive &&
        (Sage.getBoolean("seeker/check_all_files_for_transcode", true) ||
            (Sage.getBoolean("seeker/check_favorites_for_transcode", true) && es.switcherFile.acquisitionTech ==
            MediaFile.ACQUISITION_FAVORITE) || (Sage.getBoolean("seeker/check_manuals_for_transcode", true) &&
                es.switcherFile.acquisitionTech == MediaFile.ACQUISITION_MANUAL)))
    {
      Ministry.getInstance().submitForPotentialTranscoding(es.switcherFile);
    }

    // If we do this here after a recording completes then it'll mean less on-demand thumbnail generation when the recordings view is shown and reduce the likelihood
    // that thumbnail generation will interfere with UI activity
    if (Sage.getBoolean("seeker/generate_thumbnail_when_recordings_complete", true) && es.switcherFile.isCompleteRecording())
    {
      final MediaFile thumbGenMF = es.switcherFile;
      Pooler.execute(new Runnable()
      {
        public void run()
        {
          try{Thread.sleep(Math.round(Math.random()*120000) + 30000);}catch(Exception e){}
          thumbGenMF.getThumbnail(null);
        }
      }, "SeekThumbGen", Thread.MIN_PRIORITY);
    }

    es.switcherFile = null;
    es.switcherAir = null;
  }

  private void endRecord(EncoderState es, long currTime, boolean readySwitch)
  {
    if (es.currRecord == null || es.currRecordFile == null) return;

    synchronized (this)
    {
      if (Sage.DBG) System.out.println("Seeker.endRecord(" + Sage.df(currTime) + ") currRecord=" + es.currRecord + " readySwitch=" + readySwitch);
      /*
       * NOTE: I moved this from the end to the beginning on 4/27/04. The reason was the MF DB update
       * would propogate before stopEncoding was called. This meant the DB indicated the file was
       * not recording, but the inactiveFile notification hadn't been distributed yet. Its important
       * to distribute that notifcation before we make any other state changes regarding that recording.
       **/
      if (!es.currRecordFile.isLiveStream() && !readySwitch)
      {
        Sage.processInactiveFile(es.currRecordFile.getRecordingFile().toString());
      }

      /*
       * We have to stop the graph before we update the DB. Otherwise we may attempt to delete
       * the file because its marked as not recording, but it'll still be open by the dumper.
       */
      if (!readySwitch)
      {
        es.capDev.stopEncoding();
      }
      else
      {
        es.switcherFile = es.currRecordFile;
        es.switcherAir = es.currRecord;
      }

      // If we covered enough time with this record to count for a full
      // one, then we add it into the ignore list
      // Find the aggregate start/stop for this watch
      if (!es.currRecordFile.isLiveStream())
      {
        if (!readySwitch)
          es.currRecordFile.endSegment(currTime);
        VideoStorage myvs = getStorageForFile(es.currRecordFile);
        if (myvs != null)
        {
          synchronized (myvs.fileSpaceReserves)
          {
            myvs.fileSpaceReserves.remove(es);
          }
        }
      }
      removeFromCurrRecords(es.currRecord);
      currRecordFiles.remove(es.currRecordFile);
      if (es.forceWatch == es.currRecord)
      {
        es.forceWatch = null;
        es.forceProcessed = false;
      }

      // Check to see if this recording was done badly; we can tell that because it would have a size of zero.
      // There's going to be more cases of that I think beyond zero; but I'm not sure yet about those and if we
      // really want to mark them as failed.
      // It's possible to mark something watched at a time so it would stop at a zero-sized recording w/out it being an error, so check that case as well
      if (es.currRecordFile.getSize() == 0 && (god.isLoveAir(es.currRecord) || wiz.getManualRecord(es.currRecord) != null) && !BigBrother.isWatched(es.currRecord))
      {
        if (Sage.DBG) System.out.println("Seeker detected a zero-length completed recording of an MR/Fav. Notify about this!");
        sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createFailedRecordingMsg(es.capDev.getActiveInput(), es.currRecord,
					es.currRecord.getChannel(), EPG.getInstance().getPhysicalChannel(es.capDev.getActiveInput() != null ?
                es.capDev.getActiveInput().getProviderID() : 0, es.currRecord.getStationID())));
      } else if (!es.currRecordFile.isAnyLiveStream()) {
        // Check if we have low bitrate detection configured, and if it was too low...post a message about it.
        long bitrateThreshold = es.capDev.getBitrateThresholdForError();
        if (bitrateThreshold > 0) {
          // The 5000 is just a fudge factor because this was getting hit falsely for recordings with
          // small durations, part of it was due to the tuning delay, but based on the examples, even accounting
          // for that would have put us really close to the edge of detection.
          long actualBitrate = es.currRecordFile.getSize() * 8000 / Math.max(1,
              es.currRecordFile.getRecordDuration() - es.capDev.getPostTuningDelay() - 5000);
          if (actualBitrate < bitrateThreshold) {
            if (Sage.DBG) System.out.println("Seeker detected a bitrate of " + actualBitrate + " which is below the error threshold of " +
                bitrateThreshold + " and will post a system message about this for: " + es.currRecordFile);
            sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createBitrateTooLowMsg(es.capDev, es.capDev.getActiveInput(), es.currRecord,
              es.currRecord.getChannel(), EPG.getInstance().getPhysicalChannel(es.capDev.getActiveInput() != null ?
                    es.capDev.getActiveInput().getProviderID() : 0, es.currRecord.getStationID())));
          }
        }
      }

      /*
       * NOTE: When we finish recording a favorite, but it's not complete, we want to
       * mark it as complete if its not on again otherwise we could lose some desired content.
       * But don't do it if we've got less than 1/4 of it
       */
      if (!readySwitch && !es.currRecordFile.isCompleteRecording() && god.isLoveAir(es.currRecord) &&
          es.currRecordFile.getRecordDuration() > es.currRecord.getSchedulingDuration()/4)
      {
        if (!BigBrother.isUnique(es.currRecord) || (!es.currRecord.hasLaterWatchableAiring() && !es.currRecord.isWatched()))
        {
          if (Sage.DBG) System.out.println("Forcing completion of partially recorded favorite file:" + es.currRecordFile);
          es.currRecordFile.thisIsComplete();
          // Only post the system message if they're not watching it...since if they are they'd know about the issue.
          // Also don't post it if we're shutting down which would be something the user is forcing the system to do (and since it'll likely just come back up shortly)
          if (!isMediaFileBeingViewed(es.currRecordFile) && SageTV.isAlive() && !BigBrother.isWatched(es.currRecord))
            sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createPartialRecordingFromConflictMsg(es.currRecord, es.currRecord.getChannel()));
        }
      }

      if (!readySwitch && !es.currRecordFile.isAnyLiveStream())
        PluginEventManager.postEvent(PluginEventManager.RECORDING_STOPPED,
            new Object[] { PluginEventManager.VAR_MEDIAFILE, es.currRecordFile });

      // General file export plugins
      if (!readySwitch && es.currRecordFile.isCompleteRecording())
      {
        processFileExport(es.currRecordFile.getFiles(), es.currRecordFile.acquisitionTech);

        // Setup persistence for the Airing this MediaFile is based on to handle later recovery
        es.currRecord.setPersist(Airing.PERSIST_TV_MEDIAFILE_LINK);

        // Update the acquisition technique to match the current requested record state for this item so we don't have
        // files marked as being from the watch buffer if the user added them as a record/favorite during recording time.
        if (Wizard.getInstance().getManualRecord(es.currRecord) != null)
        {
          es.currRecordFile.setAcquisitionTech(MediaFile.ACQUISITION_MANUAL);
        }
        else if (es.currRecord.isMustSee())
        {
          es.currRecordFile.setAcquisitionTech(MediaFile.ACQUISITION_FAVORITE);
        }

        // Store the metadata information in the recorded file
        if (Sage.getBoolean("seeker/mpeg_metadata_embedding", true))
          sage.media.format.MpegMetadata.addMediaFileMetadata(es.currRecordFile);
        if (!es.currRecordFile.isAnyLiveStream())
          PluginEventManager.postEvent(PluginEventManager.RECORDING_COMPLETED,
              new Object[] { PluginEventManager.VAR_MEDIAFILE, es.currRecordFile });
      }

      // Internal transcoding engine
      if (!readySwitch && alive &&
          (Sage.getBoolean("seeker/check_all_files_for_transcode", true) ||
              (Sage.getBoolean("seeker/check_favorites_for_transcode", true) && es.currRecordFile.acquisitionTech ==
              MediaFile.ACQUISITION_FAVORITE) || (Sage.getBoolean("seeker/check_manuals_for_transcode", true) &&
                  es.currRecordFile.acquisitionTech == MediaFile.ACQUISITION_MANUAL)))
      {
        Ministry.getInstance().submitForPotentialTranscoding(es.currRecordFile);
      }

      // If we do this here after a recording completes then it'll mean less on-demand thumbnail generation when the recordings view is shown and reduce the likelihood
      // that thumbnail generation will interfere with UI activity
      if (Sage.getBoolean("seeker/generate_thumbnail_when_recordings_complete", true) && es.currRecordFile.isCompleteRecording())
      {
        final MediaFile thumbGenMF = es.currRecordFile;
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            try{Thread.sleep(Math.round(Math.random()*120000) + 30000);}catch(Exception e){}
            thumbGenMF.getThumbnail(null);
          }
        }, "SeekThumbGen", Thread.MIN_PRIORITY);
      }

      es.currRecord = null;
      es.currRecordFile = null;
    }
  }

  public void processFileExport(File[] files, byte acquisitionTech)
  {
    for (int i = 0; i < exportPlugins.size(); i++)
      exportPlugins.get(i).filesDoneRecording(files, acquisitionTech);
  }

  public boolean requiresPower()
  {
    // This is true if there's any clients watching TV or if we're recording an MR or a Favorite
    synchronized (this)
    {
      for (EncoderState encState : encoderStateMap.values())
      {
        if (!encState.controllingClients.isEmpty())
          return true;
        if (encState.currRecord != null && (wiz.getManualRecord(encState.currRecord) != null ||
            encState.currRecord.isMustSee()))
          return true;
      }
    }
    return false;
  }

  private void enforceKeepAtMost()
  {
    if (Sage.DBG) System.out.println("Enforcing keep at most limits for the files...");
    MediaFile[] mFiles = wiz.getFiles();
    Map<Agent, Set<MediaFile>> agentFileMap = new HashMap<Agent, Set<MediaFile>>();
    for (int i = 0; i < mFiles.length; i++)
    {
      // NARFLEX: Updated on 10/30/06 to ignore files that have a zero station ID since those are from conversions and
      // we don't want to auto-delete those.
      if (!mFiles[i].isTV() || mFiles[i].archive || god.isDoNotDestroy(mFiles[i]) ||
          wiz.getManualRecord(mFiles[i].getContentAiring()) != null || (mFiles[i].getContentAiring().getStationID() == 0))
        continue;
      Agent fileAgent = god.getCauseAgent(mFiles[i].getContentAiring());
      if (fileAgent != null && fileAgent.getAgentFlag(Agent.KEEP_AT_MOST_MASK) != 0)
      {
        if (mFiles[i].isCompleteRecording())
        {
          // This agent does keep at most tracking.
          Set<MediaFile> currAgentSet = agentFileMap.get(fileAgent);
          if (currAgentSet == null)
          {
            currAgentSet = new HashSet<MediaFile>();
            agentFileMap.put(fileAgent, currAgentSet);
          }
          currAgentSet.add(mFiles[i]);
          if (currAgentSet.size() > fileAgent.getAgentFlag(Agent.KEEP_AT_MOST_MASK))
          {
            // Find the oldest file in this set and delete it now!
            long oldest = Long.MAX_VALUE;
            MediaFile oldestFile = null;
            Iterator<MediaFile> walker = currAgentSet.iterator();
            while (walker.hasNext())
            {
              MediaFile mf = walker.next();
              if (oldestFile == null || oldest > mf.getContentAiring().getStartTime())
              {
                oldest = mf.getContentAiring().getStartTime();
                oldestFile = mf;
              }
            }
            if (Sage.DBG) System.out.println("Seeker enforcing KEEP AT MOST of " + fileAgent.getAgentFlag(Agent.KEEP_AT_MOST_MASK) +
                " for " + fileAgent + " on " + oldestFile);
            // Since it's a favorite, it'd seem kind of silly to apply a don't like to it
            // when we delete it.
            destroyFile(oldestFile, false, "KeepAtMost");
            // Remove it from the set so we don't process it again next time
            currAgentSet.remove(oldestFile);
          }
        }
      }
    }
    if (Sage.DBG) System.out.println("DONE enforcing keep at most limits for the files.");
  }

  public boolean requestFileStorage(File theFile, long theSize)
  {
    synchronized (videoStore)
    {
      File targetDir = theFile.getParentFile();
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.elementAt(i);
        if (vs.videoDir.equals(targetDir))
        {
          synchronized (vs.fileSpaceReserves)
          {
            vs.fileSpaceReserves.put(theFile, theSize);
          }
          return true;
        }
      }
    }
    return false;
  }

  public void clearFileStorageRequest(File theFile)
  {
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.elementAt(i);
        synchronized (vs.fileSpaceReserves)
        {
          vs.fileSpaceReserves.remove(theFile);
        }
      }
    }
  }

  public File requestDirectoryStorage(String dirName, long theSize)
  {
    synchronized (videoStore)
    {
      VideoStorage vs = findBestStorageForSize(theSize, null);
      File tempDir = new File(vs.videoDir, dirName);
      IOUtils.safemkdirs(tempDir);
      vs.fileSpaceReserves.put(tempDir, theSize);
      ignoreFiles.add(tempDir.getAbsolutePath());
      return tempDir;
    }
  }

  public void clearDirectoryStorageRequest(File theFile)
  {
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.elementAt(i);
        synchronized (vs.fileSpaceReserves)
        {
          vs.fileSpaceReserves.remove(theFile);
        }
      }
      ignoreFiles.remove(theFile);
    }
  }

  private VideoStorage findBestStorageForSize(long sizeNeeded, String forcedPathPrefix)
  {
    synchronized (videoStore)
    {
      long maxFree = 0;
      int minSimWrites = Integer.MAX_VALUE;
      boolean perfBalance = Sage.getBoolean("seeker/recording_disk_balance_maxbw", false);
      // If we're doing disk balancing based off bandwidth, then we only choose from the disks
      // that have the lowest number of files currently being written to them. After that we proceed
      // with our normal disk selection routines....however if we don't think there's enough files
      // that will ever be freed from the disks remaining in that set; then we'll choose the disk
      // using the space balancing method.
      if (perfBalance)
      {
        for (int i = 0; i < videoStore.size(); i++)
        {
          VideoStorage vs = videoStore.elementAt(i);
          minSimWrites = Math.min(minSimWrites, vs.fileSpaceReserves.size());
        }
      }
      VideoStorage maxStore = null;
      Map<VideoStorage, Long> freeMap = new HashMap<VideoStorage, Long>();
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.elementAt(i);
        boolean online = vs.videoDir.isDirectory();
        if (!online)
        {
          if (!vs.offline)
          {
            // Notify the user that this video directory is offline; since this may be a bad thing.
            // It's a serious error if they only have one directory; its a warning if they have more than one
            vs.offline = true;
            sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createOfflineVideoDirectoryMessage(vs.videoDir,
                videoStore.size() == 1));
          }
        }
        else
          vs.offline = false;
        if (online && (forcedPathPrefix == null || forcedPathPrefix.length() == 0 ||
            vs.videoDir.toString().toLowerCase().startsWith(forcedPathPrefix.toLowerCase())))
        {
          long currUnres = vs.getUnreservedVideoDiskspace();
          if (currUnres >= sizeNeeded && currUnres > maxFree && (!perfBalance || minSimWrites == vs.fileSpaceReserves.size()))
          {
            maxFree = currUnres;
            maxStore = vs;
          }
          freeMap.put(vs, currUnres);
        }
      }

      if (videoStore.size() == 1) return videoStore.firstElement();

      // Return the video drive with the most free space, not just the first drive with enough space
      // That way we balance the disks if they're not keeping them full.
      if (maxStore != null)
        return maxStore;

      int numSafeRecentWatches = Sage.getInt("seeker/num_recent_watches_to_keep", 4);
      Set<Airing> safeAirs = new HashSet<Airing>();
      Watched[] recentWatch = wiz.getLatestWatchedTV(numSafeRecentWatches*4); // extra for nulls & dupes
      for (int i = 0; safeAirs.size() < numSafeRecentWatches && i < recentWatch.length; i++)
      {
        Airing a = recentWatch[i].getAiring();
        if (a != null)
          safeAirs.add(a);
      }

      safeAirs.addAll(currRecords);

      synchronized (clientWatchFileMap) {
        Set<MediaFile> vfFiles = new HashSet<MediaFile>(clientWatchFileMap.keySet());
        Iterator<MediaFile> walker = vfFiles.iterator();
        while (walker.hasNext()) {
          safeAirs.add(walker.next().getContentAiring());
        }
      }

      MediaFile[] mFiles = wiz.getFiles();
      for (int i = 0; i < mFiles.length; i++)
        if (mFiles[i].archive || !mFiles[i].isTV() || safeAirs.contains(mFiles[i].getContentAiring()) ||
            (god.isDoNotDestroy(mFiles[i]) && (mFiles[i].isCompleteRecording() || mFiles[i].getContentAiring().getSchedulingEnd() > Sage.time())) ||
            wiz.getManualRecord(mFiles[i].getContentAiring()) != null || (mFiles[i].getContentAiring().getStationID() == 0))
          mFiles[i] = null;

      Arrays.sort(mFiles, getMediaFileComparator());

      VideoStorage backupPerfChoice = null;
      for (int k = mFiles.length - 1; k >= 0; k--)
      {
        if (mFiles[k] == null)
          continue;
        VideoStorage currStore = getStorageForFile(mFiles[k]);
        if (currStore != null)
        {
          Long currFree = freeMap.get(currStore);
          // Don't use video stores that aren't in the map!!! That would mean they're not valid directories
          // or they're not following the forced storage path rules.
          if (currFree == null)
            continue;
          long freeNow = 0;
          if (currFree != null)
            freeNow += currFree;
          freeNow += mFiles[k].getSize();
          if (freeNow >= sizeNeeded)
          {
            if (!perfBalance || currStore.fileSpaceReserves.size() == minSimWrites)
              return currStore;
            else if (backupPerfChoice == null)
              backupPerfChoice = currStore;
          }
          freeMap.put(currStore, freeNow);
        }
      }

      // This is where we use the result of the space balancing algorithm if the bandwidth balancing algorithm
      // doesn't give a result
      if (perfBalance && backupPerfChoice != null)
        return backupPerfChoice;

      // We have to return something from here. So pick a video storage location, even if it
      // has negative space unreserved
      maxFree = Long.MIN_VALUE;
      for (Map.Entry<VideoStorage, Long> ent : freeMap.entrySet())
      {
        long currLong = ent.getValue();
        if (maxFree < currLong)
        {
          maxFree = currLong;
          maxStore = ent.getKey();
        }
      }
      return maxStore;
    }
  }

  private VideoStorage getStorageForFile(MediaFile mf)
  {
    if (mf == null) return null;
    synchronized (videoStore)
    {
      File targetDir = new File(mf.videoDirectory);
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.elementAt(i);
        if (vs.videoDir.equals(targetDir))
          return vs;
      }
    }
    return null;
  }

  private void cleanupPartialsAndUnwanted()
  {
    if (Sage.DBG) System.out.println("Seeker clearing unwanted and partial files...");
    Set<Airing> safeAirs = new HashSet<Airing>();

    // Only keep recently watched that were watched within the last half hour.
    Iterator<UIManager> uiWalker = UIManager.getUIIterator();
    boolean allSleeping = true;
    while (uiWalker.hasNext())
    {
      if (!uiWalker.next().isAsleep())
      {
        allSleeping = false;
        break;
      }
    }
    if (!allSleeping)
    {
      int numSafeRecentWatches = Sage.getInt("seeker/num_recent_watches_to_keep", 4);
      Watched[] recentWatch = wiz.getLatestWatchedTV(numSafeRecentWatches*4); // extra for nulls & dupes
      for (int i = 0; safeAirs.size() < numSafeRecentWatches && i < recentWatch.length; i++)
      {
        Airing a = recentWatch[i].getAiring();
        if (a != null && a.getSchedulingEnd() > Sage.time() - MILLIS_PER_HOUR/2)
          safeAirs.add(a);
      }
    }

    synchronized (clientWatchFileMap) {
      Set<MediaFile> vfFiles = new HashSet<MediaFile>(clientWatchFileMap.keySet());
      for (MediaFile f : vfFiles) {
        safeAirs.add(f.getContentAiring());
      }
    }

    MediaFile[] mfs = wiz.getFiles();
    for (int i = 0; i < mfs.length; i++)
    {
      // Only remove TV files that are not archived, not currently recording,
      // not manual records, and not favorites. If they are a favorite, delete it if
      // its over and a partial and meets the other criteria.
      if (mfs[i].isTV() && !mfs[i].archive && !currRecordFiles.contains(mfs[i]) &&
          !clientWatchFileMap.containsKey(mfs[i]) &&
          wiz.getManualRecord(mfs[i].getContentAiring()) == null &&
          !safeAirs.contains(mfs[i].getContentAiring()) &&
          (!god.isLoveAir(mfs[i].getContentAiring()) ||
              (mfs[i].getContentAiring().getSchedulingEnd() < Sage.time() &&
                  !mfs[i].isCompleteRecording())) &&
                  (!mfs[i].isCompleteRecording() ||
                      (mfs[i].acquisitionTech == MediaFile.ACQUISITION_WATCH_BUFFER)))
      {
        destroyFile(mfs[i], false, "PartialOrUnwanted");
      }
    }
    if (Sage.DBG) System.out.println("DONE clearing unwanted and partial files.");
  }

  // Cache some objects for performance
  private Set<Airing> safeAirs = new HashSet<Airing>();
  private Set<Airing> verySafeAirs = new HashSet<Airing>();
  private List<MediaFile> deleteBeforeUnwatchedFavs = new ArrayList<MediaFile>();

  private boolean ensureDiskSpace()
  {
    if (Sage.DBG) System.out.println("Diskspace checking is running");
    if (!Sage.getBoolean("seeker/disable_video_directory_verifications", false))
      verifyFiles(false, true);

    if (!sched.isPrepped()) return true;

    enforceKeepAtMost();

    if (disableProfilerRecs && Sage.getBoolean("seeker/clear_partials_and_unwanted_when_ir_off", true))
      cleanupPartialsAndUnwanted();
    safeAirs.clear();

    int numSafeRecentWatches = Sage.getInt("seeker/num_recent_watches_to_keep", 4);
    Watched[] recentWatch = wiz.getLatestWatchedTV(numSafeRecentWatches*4); // extra for nulls & dupes
    for (int i = 0; safeAirs.size() < numSafeRecentWatches && i < recentWatch.length; i++)
    {
      Airing a = recentWatch[i].getAiring();
      if (a != null)
        safeAirs.add(a);
    }

    // The VERY safe airs absolutely cannot be deleted because they are in use. The safeAirs can
    // be deleted if it'll prevent deletion of an unwatched Favorite
    verySafeAirs.clear();
    verySafeAirs.addAll(currRecords);
    synchronized (clientWatchFileMap) {
      for (MediaFile f : clientWatchFileMap.keySet()) {
        verySafeAirs.add(f.getContentAiring());
      }
    }
    safeAirs.removeAll(verySafeAirs);

    MediaFile[] mFiles = wiz.getFiles();
    if (mFiles.length == 0) return true;
    // NARFLEX: Updated on 10/30/06 to ignore files that have a zero station ID since those are from conversions and
    // we don't want to auto-delete those.
    for (int i = 0; i < mFiles.length; i++) {
      if (mFiles[i].archive || !mFiles[i].isTV() || verySafeAirs.contains(mFiles[i].getContentAiring())) {
        mFiles[i] = null;
        continue;
      }
      // NARFLEX: 9/20/12 - Allow deletion of zero station ID content on embedded since the only
      // reason we ever added that was to protect transcoded files and those don't exist on embedded and it's
      // preventing auto deletion of recovered content even after Keep Forever (i.e. archive/ManualRecord) is cleared
      if (((god.isDoNotDestroy(mFiles[i]) && (mFiles[i].isCompleteRecording() ||
             mFiles[i].getContentAiring().getSchedulingEnd() > Sage.time())) ||
             wiz.getManualRecord(mFiles[i].getContentAiring()) != null ||
             (mFiles[i].getContentAiring().getStationID() == 0))) {
        mFiles[i] = null;
        continue;
      }
    }
    Arrays.sort(mFiles, getMediaFileComparator());
    // Check each video storage out and if we're writing to it ensure we've got enough
    // space for all the streams that are writing to that drive
    boolean validFormatsFound = false;
    VideoStorage[] currStores = videoStore.toArray(new VideoStorage[0]);
    for (int i = 0; i < currStores.length; i++)
    {
      validFormatsFound = true;
      if (currStores[i].fileSpaceReserves.isEmpty())
      {
        // Nothing being written to this store so nothing to delete
        // Except on embedded...then we need to be sure they have room for
        // personal content copying as well.
        continue;
      }
      long freeSpace;
      long needFreeSize = 0;
      freeSpace = currStores[i].getAvailVideoDiskspace();

      synchronized (currStores[i].fileSpaceReserves)
      {
        for (Object obj : currStores[i].fileSpaceReserves.keySet())
        {
          if (obj instanceof EncoderState)
          {
            EncoderState es = (EncoderState) obj;
            // This'll be in bytes/millisecond
            double avgRate = 18e6/8000; // better to guess high than low
            if (es.capDev.getActiveInput() != null && es.capDev.getActiveInput().getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX)
              avgRate = 18e6/8000; // always predict 18Mbps for digital TV sources to be safe
            else if (es.myQuality != null)
            {
              if (mmc.getQualityFormat(es.myQuality).indexOf("TS") != -1)
              {
                // Use 18Mbps for TS
                avgRate = 18e6 / 8000;
              }
              else
              {
                avgRate = mmc.getQualityOverallBitrate(es.myQuality) / 8000;
              }
            }
            // Don't count ones that are for live or live-buffered streams since they don't
            // increase in size
            if (es.currRecordFile == null || !es.currRecordFile.isAnyLiveStream())
              needFreeSize += Math.round(avgRate * DISK_BONUS_TIME);
          }
          else if (obj instanceof File)
          {
            File f = (File) obj;
            long totalReqSize = currStores[i].fileSpaceReserves.get(obj);
            // If it's a directory, we need to get the size of everything in it
            if (f.isDirectory())
              needFreeSize += totalReqSize - IOUtils.getDirectorySize(f);
            else
              needFreeSize += totalReqSize - f.length();
          }
        }
      }

      if (Sage.DBG) System.out.println("needFreeSize = " + needFreeSize/1.0e9 + " GB currFreeSize=" +
          freeSpace/1.0e9 + " GB");
      // Check to see if we've already got enough space
      if (freeSpace >= needFreeSize || needFreeSize < 0)
        continue;

      if (Sage.DBG) System.out.println("DISKSPACE MANAGEMENT: freeSpace=" + (freeSpace/1000000L) + "MB safeAirs=" + safeAirs + " verySafeAirs=" + verySafeAirs);

      long currAvail = currStores[i].getAvailVideoDiskspace();
      deleteBeforeUnwatchedFavs.clear();
      for (int k = mFiles.length - 1; (currAvail < needFreeSize) && (k >= 0); k--)
      {
        if (mFiles[k] == null ||
            getStorageForFile(mFiles[k]) != currStores[i])
          continue;
        if (safeAirs.contains(mFiles[k].getContentAiring()))
        {
          deleteBeforeUnwatchedFavs.add(mFiles[k]);
          continue;
        }
        // If the video directory is offline then DO NOT clear stuff out of it since we won't actually
        // gain any free space from it and we'll just destroy the DB info.
        if (!currStores[i].videoDir.isDirectory())
        {
          if (Sage.DBG) System.out.println("Skipping deletion of file because the video directory is offline: " + mFiles[k]);
          continue;
        }
        if (!deleteBeforeUnwatchedFavs.isEmpty() && god.isLoveAir(mFiles[k].getContentAiring()) && !mFiles[k].getContentAiring().isWatched() &&
            mFiles[k].isCompleteRecording())
        {
          if (Sage.DBG) System.out.println("Deleting watched safe air instead of unwatched favorite:" + mFiles[k]);
          destroyFile(deleteBeforeUnwatchedFavs.remove(0), true, "Diskspace");
          k++; // Stay on this index because we may still need to delete it
        }
        else
          destroyFile(mFiles[k], true, "Diskspace");
        // Only update this when we delete a file, don't do it on every loop cycle like we used to since
        // that's a big performance hit
        currAvail = currStores[i].getAvailVideoDiskspace();
      }
      if (Sage.DBG) System.out.println("DISKSPACE MANAGEMENT COMPLETE FreeSpace=" + currStores[i].getAvailVideoDiskspace()/1000000L);

      // Did NOT get enough diskspace AND we're actively recording to this storage space
      if (currAvail < needFreeSize && !currStores[i].fileSpaceReserves.isEmpty())
        return false;
    }
    if (!validFormatsFound)
    {
      if (Sage.DBG) System.out.println("ERROR Did not find any configured recording directories in the EXT3 file format!");
      return false;
    }
    return true;
  }

  private void cleanDeniedMustSees()
  {
    for (int i = 0; i < deniedMustSees.size(); i++)
    {
      Airing air = deniedMustSees.elementAt(i);
      if (air.getSchedulingEnd() <= Sage.time())
        deniedMustSees.removeElementAt(i--);
    }
  }

  private void cleanChanChangeAsks()
  {
    for (int i = 0; i < completeChanChangeAsks.size(); i++)
    {
      Airing air = completeChanChangeAsks.elementAt(i);
      if (air.getSchedulingEnd() <= Sage.time())
        completeChanChangeAsks.removeElementAt(i--);
    }
  }

  /*
   * IMPORTANT NOTE: We're not detecting lost media extenders that connect through another client
   */
  private void cleanupLostClientWatches()
  {
    Set<String> allClients = new HashSet<String>(Arrays.asList(
        NetworkClient.getConnectedClients()));
    allClients.add("localhost");
    boolean madeChanges = false;
    synchronized (clientWatchFileMap)
    {
      Iterator<Set<UIClient>> walker = clientWatchFileMap.values().iterator();
      while (walker.hasNext())
      {
        Set<UIClient> currSet = walker.next();
        Iterator<UIClient> clientWalker = currSet.iterator();
        while (clientWalker.hasNext())
        {
          UIClient currClient = clientWalker.next();
          if(!isClientAlive(allClients, currClient)) {
            clientWalker.remove();
          }
        }
        if (currSet.isEmpty())
        {
          walker.remove();
          madeChanges = true;
        }
      }
    }
    if (madeChanges)
    {
      kick();
      // With curr records getting inserted into the schedule, we need to evaluate it
      // on simple watch changes
      sched.kick(false); // this'll come back and kick us again, but that's OK
    }
  }

  /**
   * Test if the given client is still active in the system.
   *
   * @param allClients set of default client names to test against
   * @param currClient to test
   * @return true if connected, false otherwise
   */
  private boolean isClientAlive(Set<String> allClients, UIClient currClient) {
    switch(currClient.getUIClientType()) {
      case UIClient.REMOTE_UI:
        if(UIManager.getLocalUIByName(currClient.getLocalUIClientName()) == null) {
          if (Sage.DBG) System.out.println("Seeker is dropping RUI client from watch map because its connection is dead:" + currClient);
          return false;
        }
        break;
      default:
        if (!allClients.contains(currClient.getUIClientHostname()))
        {
          if (Sage.DBG) System.out.println("Seeker is dropping client from watch map because its connection is dead:" + currClient);
          return false;
        }
        break;
    }
    return true;
  }

  public MediaFile getMediaFileBeingViewedByClient(UIClient client)
  {
    synchronized (clientWatchFileMap)
    {
      Iterator<Map.Entry<MediaFile, Set<UIClient>>> walker = clientWatchFileMap.entrySet().iterator();
      while (walker.hasNext())
      {
        Map.Entry<MediaFile, Set<UIClient>> currEnt = walker.next();
        Set<UIClient> currSet = currEnt.getValue();
        if (currSet.contains(client))
          return currEnt.getKey();
      }
    }
    return null;
  }

  public MediaFile[] getMediaFilesBeingViewed()
  {
    synchronized (clientWatchFileMap)
    {
      return clientWatchFileMap.keySet().toArray(new MediaFile[0]);
    }
  }

  public boolean isMediaFileBeingViewed(MediaFile mf)
  {
    return clientWatchFileMap.containsKey(mf);
  }

  public boolean isPrepped()
  {
    return prepped;
  }

  private void controlImportCPUUsage()
  {
    // If the user isn't doing anything then don't bother controlling CPU usage
    try{Thread.sleep(30);}catch (Exception e){}
  }

  public void run()
  {
    Thread fst = new Thread(FSManager.getInstance(), "FSManager");
    fst.setDaemon(true);
    fst.setPriority(Thread.MIN_PRIORITY);
    fst.start();

    if (Sage.client)
    {
      alive = true;
      clientGCCleanup();
      return;
    }

    seekerThread = Thread.currentThread();
    canRecord = false;
    prepped = false;
    boolean couldRecord = false;
    alive = true;
    boolean didInitScan = !Sage.getBoolean("seeker/do_initial_lib_scan", true);

    long altExpireTime = Sage.LINUX_OS ? Sage.getLong("window_size", 0) : 0;

    verifyFiles(true, false);

    Thread watchdog = new Thread("SeekerWatchdog")
    {
      public void run()
      {
        long lastDumpTime = 0;
        while (alive)
        {
          long testTime = lastSeekerWakeupTime;
          if (testTime != 0 && testTime != lastDumpTime && Sage.eventTime() - testTime > 60000)
          {
            if (Sage.DBG) System.out.println("ERROR - Seeker has been hung for more than 60 seconds...system appears deadlocked...dumping thread states");
            AWTThreadWatcher.dumpThreadStates();
            lastDumpTime = testTime;
          }
          try{Thread.sleep(60000);}catch(Exception e){}
        }
      }
    };
    watchdog.setDaemon(true);
    watchdog.setPriority(Thread.MIN_PRIORITY);
    watchdog.start();

    //try{Thread.sleep(1000);}catch(Exception e){}
    //java.awt.Toolkit.getDefaultToolkit().beep();
    long wakeupTime = Long.MAX_VALUE;
    while (alive)
    {
      try{

        long currTime = 0;
        postScheduleChange();

        // Set this in case the property changed...we don't want to do it directly in BigBrother since that adds overhead of checking that property a lot
        BigBrother.advRedCheck = Sage.getBoolean("epg/advanced_show_redundancy_check", false);

        checkFileSizeLimit();

        synchronized (this)
        {
          // Be sure we wake up before the current record is over.
          for (EncoderState es : encoderStateMap.values())
          {
            if (es.currRecord != null)
            {
              if (es.currRecord.getSchedulingEnd() < Sage.time() &&
                  !(es.currRecord instanceof ManualRecord.FakeAiring))
              {
                wakeupTime = Math.min(wakeupTime, es.currRecord.getEndTime());
              }
              else
                wakeupTime = Math.min(wakeupTime, es.currRecord.getSchedulingEnd());
            }
            if (es.forceWatch != null && !es.forceProcessed)
              wakeupTime = 0;
          }

          // If recording or sleep status changed or we have a schedule
          // modification request, process it now
          if (couldRecord != canRecord)
            wakeupTime = 0;

          boolean diskOK = true;
          if (wakeupTime - Sage.time() > 1000 || wakeupTime == Long.MAX_VALUE)
          {
            // Do this asynchronously so it has no impact on timing in the Seeker
            Pooler.execute(new Runnable()
            {
              public void run()
              {
                if (Sage.getRawProperties().isDirty())
                  Sage.savePrefs();
                Iterator<UIManager> uiWalker = UIManager.getUIIterator();
                while (uiWalker.hasNext())
                {
                  UIManager currUIMgr = uiWalker.next();
                  if (currUIMgr.getProperties() != null && currUIMgr.getProperties().isDirty())
                    currUIMgr.savePrefs();
                }
              }
            }, "AsyncPropSaver");
            if (!currentlyImporting && (!didInitScan ||
                (Sage.getBoolean("seeker/periodically_scan_library", true) &&
                    Sage.time() - lastLibScanTime > libScanPeriod)))
            {
              didInitScan = true;
              if (!doThoroughLibScan &&
                  Sage.time() - lastThroughLibScanTime > Sage.getLong("seeker/" + THOROUGH_LIB_SCAN_PERIOD, 4*Sage.MILLIS_PER_HR))
                doThoroughLibScan = true;
              libraryImportScan(); // async so it doesn't bog us down if we've got large dir structure to parse
            }

            if (!disableLibraryScanning)
            {
              checkDirsForFiles(false);

              diskOK = ensureDiskSpace();
              if (!diskOK)
              {
                if (Sage.DBG) System.out.println("DISKSPACE INADEQUATE: Seeker cannot continue recording.");
                // THIS HAPPENS AS THE RESULT OF AN ERROR MORE OFTEN THAN ANYTHING ELSE, SO LETS
                // NOT STOP RECORDING IN THIS SITUATION ANYMORE.
                if (Sage.getBoolean("seeker/treat_diskspace_warnings_as_serious", !Sage.WINDOWS_OS))
                {
                  Catbert.distributeHookToAll("MediaPlayerError", new Object[] { Sage.rez("Capture"),
                      Sage.rez("OUT_OF_DISKSPACE_WARNING") });
                  sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createDiskspaceMsg(true));
                }
                else
                {
                  diskOK = true;
                  sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createDiskspaceMsg(false));
                }
              }
            }
          }

          if (Sage.DBG) System.out.println("Seeker waiting for" + ((wakeupTime - Sage.time()==0) ? "ever." : (" " + ((wakeupTime - Sage.time())/60000L) + " mins.")));
          notifyAll();

          if (rebootMe)
          {
            if (Sage.getBoolean("reboot_quickly", false) ||
                (Sage.time() - ServerPowerManagement.getInstance().getLastActivityTime() > 20 * Sage.MILLIS_PER_MIN))
            {
              long nextTime = Scheduler.getInstance().getNextMustSeeTime();
              if ((nextTime == 0 || nextTime > Sage.time() + Sage.getLong("reboot_duration", 20 * Sage.MILLIS_PER_MIN)) &&
                  !requiresPower())
              {
                // Reboot the system. This is done by exiting with a special exit code
                System.out.println("Seeker is performing the reboot now!!!!");
                rebootMe = false;
                new Thread()
                {
                  public void run() { SageTV.reboot(); }
                }.start();
              }
            }
          }

          long waitTime = Math.min(wakeupTime - Sage.time(), MAX_SLEEP_TIME);
          waitTime = Math.max(1, waitTime);
          if (wasKicked)
            waitTime = 1;

          if (!alive) break;

          lastSeekerWakeupTime = 0;
          SageTV.writeOutWatchdogFile();
          try { wait(waitTime); }catch(InterruptedException e){}
          lastSeekerWakeupTime = Sage.eventTime();

          while (SageTV.isPoweringDown() && alive)
          {
            try{
              wait(100);
            }
            catch(Exception e){}
          }
          if (!alive) break;

          couldRecord = canRecord;
          currTime = Sage.time();
          if (Sage.DBG) System.out.println("Seeker awoken");
          if (Sage.DBG) System.out.println("MemStats: Used=" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1000000 +
              "MB Total=" + Runtime.getRuntime().totalMemory()/1000000 + "MB Max=" + Runtime.getRuntime().maxMemory()/1000000 + "MB");
          wasKicked = false;

          // Don't start recording until we're all ready to go!
          // Narflex 1/14/16 - This used to only be done during Seeker init. But there was a change made a few
          // months ago where redetectCaptureDevices in MMC may be called by an encoder plugin after startup.
          // If that happens, and there were no prior existing devices...then this would stay false until the
          // next restart of SageTV. Putting it here corrects that problem.
          mmcPresent = Sage.getBoolean(prefs + MMC_PRESENT, true) && (Sage.MAC_OS_X || mmc.getCaptureDeviceNames().length > 0);
          canRecord = mmcPresent && sched.isPrepped() && !disableLibraryScanning;

          cleanDeniedMustSees();
          cleanChanChangeAsks();
          cleanupLostClientWatches();

          // Check which encoders we're supposed to be using, and add/remove as appropriate
          Set<CaptureDevice> myEncs = new HashSet<CaptureDevice>(Arrays.asList(
              sched.getMyEncoders()));

          // Process scheduling information. When this returns we then deal
          // with the current record issue.
          Iterator<EncoderState> walker = encoderStateMap.values().iterator();
          while (walker.hasNext())
          {
            EncoderState es = walker.next();
            if (myEncs.remove(es.capDev) && es.capDev.isFunctioning())
            {
              es.schedule = disableProfilerRecs ? sched.getMustSee(es.capDev) : sched.getSchedule(es.capDev);
              es.mustSee = sched.getMustSee(es.capDev);
              es.scheduleContent.clear();
              es.mustSeeContent.clear();
              for (int i = 0; i < es.mustSee.size(); i++)
              {
                Airing tempAir = es.mustSee.elementAt(i);
                if (tempAir instanceof ManualRecord.FakeAiring)
                  es.mustSeeContent.add(((ManualRecord.FakeAiring) tempAir).getManualRecord().getContentAiring());
                else
                  es.mustSeeContent.add(tempAir);
              }
              for (int i = 0; i < es.schedule.size(); i++)
              {
                Airing tempAir = es.schedule.elementAt(i);
                if (tempAir instanceof ManualRecord.FakeAiring)
                  es.scheduleContent.add(((ManualRecord.FakeAiring) tempAir).getManualRecord().getContentAiring());
                else
                  es.scheduleContent.add(tempAir);
              }
              if (!canRecord || !diskOK)
              {
                // We're in a bad system state
                if (es.currRecord != null)
                {
                  endRecord(es, currTime, false);
                }
              }
            }
            else if (es.absoluteClientControl != null)
            {
              es.schedule = new Vector<Airing>();
              es.mustSee = new Vector<Airing>();
              es.scheduleContent.clear();
              es.mustSeeContent.clear();
              if (!canRecord)
              {
                // We're in a bad system state
                if (es.currRecord != null)
                {
                  endRecord(es, currTime, false);
                }
              }
            }
            else
            {
              // We've lost this encoder, kill its recording
              if (Sage.DBG) System.out.println("Seeker removing dead Encoder: " + es.capDev);
              if (es.currRecord != null)
                endRecord(es, currTime, false);
              if (Sage.getBoolean("seeker/unload_tuners_when_not_configured", false))
                es.capDev.freeDevice();
              walker.remove();
            }
          }
          for (CaptureDevice capDev : myEncs)
          {
            // Add all of the new ones
            if (!capDev.isFunctioning())
              continue;
            if (Sage.DBG) System.out.println("Seeker adding new Encoder: " + capDev);
            EncoderState es = new EncoderState(capDev);
            encoderStateMap.put(capDev, es);
            es.schedule = disableProfilerRecs ? sched.getMustSee(capDev) : sched.getSchedule(capDev);
            es.mustSee = sched.getMustSee(capDev);
            es.scheduleContent.clear();
            es.mustSeeContent.clear();
            for (int i = 0; i < es.mustSee.size(); i++)
            {
              Airing tempAir = es.mustSee.elementAt(i);
              if (tempAir instanceof ManualRecord.FakeAiring)
                es.mustSeeContent.add(((ManualRecord.FakeAiring) tempAir).getManualRecord().getContentAiring());
              else
                es.mustSeeContent.add(tempAir);
            }
            for (int i = 0; i < es.schedule.size(); i++)
            {
              Airing tempAir = es.schedule.elementAt(i);
              if (tempAir instanceof ManualRecord.FakeAiring)
                es.scheduleContent.add(((ManualRecord.FakeAiring) tempAir).getManualRecord().getContentAiring());
              else
                es.scheduleContent.add(tempAir);
            }
          }

          // Update all of the station data for the Encoder
          walker = encoderStateMap.values().iterator();
          while (walker.hasNext())
          {
            EncoderState es = walker.next();
            // 4/14/09 - Narflex - We need to synchronize on the stationSet since that's used in
            // other places from other threads. I had a log from home where findBestEncoderForNow failed
            // to select an encoder that was currently recording what was selected to watch; it got fixed up
            // in the Seeker later but then the forcedWatch was on the wrong tuner.
            synchronized (es.stationSet)
            {
              es.stationSet.clear();
              //if (es.absoluteClientControl == null)
              {
                CaptureDeviceInput[] allSrcDesc = es.capDev.getInputs();
                for (int i = 0; i < allSrcDesc.length; i++)
                {
                  if (allSrcDesc[i].getProviderID() != 0)
                  {
                    int[] allStats = EPG.getInstance().getAllStations(allSrcDesc[i].getProviderID());
                    EPGDataSource ds = EPG.getInstance().getSourceForProviderID(allSrcDesc[i].getProviderID());
                    if (ds != null)
                      for (int j = 0; j < allStats.length; j++)
                        if (ds.canViewStation(allStats[j]))
                          es.stationSet.add(allStats[j]);
                  }
                }
              }
            }
          }

          wakeupTime = Long.MAX_VALUE;

          // If we've lost the ability to record, just kill it now.
          if (!canRecord || !diskOK)
          {
            continue;
          }
        }

        wakeupTime = Math.min(work(), wakeupTime);
        prepped = true;

      }catch (Throwable throwy)
      {
        System.out.println("SEEKER EXCEPTION THROWN:" + throwy);
        Sage.printStackTrace(throwy);
        // Try to post a system message on this
        if (throwy instanceof OutOfMemoryError)
        {
          sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createOOMMsg());
        }
      }
    }

    for (EncoderState es : encoderStateMap.values())
    {
      if (es.currRecordFile != null)
        endRecord(es, Sage.time(), false);
    }
  }

  private boolean stringEquals(String s1, String s2)
  {
    return s1 == s2 || (s1 != null && s1.equals(s2));
  }

  public void releaseAllEncoding()
  {
    // This is used for when we enter a power down state
    synchronized (this)
    {
      List<Thread> cleanupThreadToJoin = new ArrayList<Thread>();
      for (final EncoderState es : encoderStateMap.values())
      {
        if (es.capDev.isDataScanning())
        {
          if (Sage.DBG) System.out.println("Stopping data scanning on capture device before we enter standby.");
          es.capDev.disableDataScanning();
        }
        if (es.currRecord != null)
        {
          if (Sage.DBG) System.out.println("Stopping record to enter system standby.");
          endRecord(es, Sage.time(), false);
        }
        if (es.capDev.isLoaded() && Sage.getBoolean("seeker/unload_capture_devices_on_standby", !Sage.VISTA_OS))
        {
          // There aren't threading rules for freeing the graph, so parallelize this so we can be sure it gets done in time.
          Thread t = new Thread("CapDevCleanup")
          {
            public void run()
            {
              if (Sage.DBG) System.out.println("Freeing capture device before entering standby:" + es.capDev);
              es.capDev.freeDevice();
            }
          };
          t.start();
          cleanupThreadToJoin.add(t);
        }
      }
      if (Sage.DBG) System.out.println("Waiting for parallel capture threads to cleanup count=" + cleanupThreadToJoin.size());
      for (int i = 0; i < cleanupThreadToJoin.size(); i++)
      {
        Thread t = cleanupThreadToJoin.get(i);
        if (t.isAlive())
        {
          try
          {
            t.join(10000);
          }
          catch (Exception e){}
        }
      }
      if (Sage.DBG) System.out.println("Done waiting for parallel capture thread cleanup to complete!");
    }
  }

  private long work()
  {
    long wakeupTime = Long.MAX_VALUE;
    boolean switchAny = false;
    synchronized (this)
    {
      // We need to stop all of the encodings before we start any.
      // Otherwise if a file switches tuners it could have 2 encoding the same
      // file at the same time, which is bad
      Vector<Object[]> pendingStarts = new Vector<Object[]>();
      long currTime = Sage.time();

      Set<String> allClients = new HashSet<String>(Arrays.asList(
          NetworkClient.getConnectedClients()));
      allClients.add("localhost");

      // We want to iterate through this starting with the encoder that has the most
      // clients controlling it. That way we'll retain the same channel for the most clients
      // possible.
      // NARFLEX - 5/12/09 - the next thing we want to sort on is tuner delay; the bigger delay tuners should
      // go at the end of the list so their delays don't affect as many other tuners
      EncoderState[] allES = encoderStateMap.values().toArray(new EncoderState[0]);
      Arrays.sort(allES, new Comparator<EncoderState>()
      {
        public int compare(EncoderState o1, EncoderState o2)
        {
          int rv = o2.controllingClients.size() - o1.controllingClients.size();
          if (rv != 0)
            return rv;
          long d1 = o1.capDev.getPostTuningDelay();
          long d2 = o2.capDev.getPostTuningDelay();
          if (d1 == d2)
            return 0;
          else if (d1 < d2)
            return -1;
          else
            return 1;
        }
      });
      for (int esNum = 0; esNum < allES.length; esNum++)
      {
        EncoderState es = allES[esNum];

        if (Sage.DBG) System.out.println("MARK 1 currRecord=" + es.currRecord + " enc=" + es.capDev +
            " clients=" + es.controllingClients + " ir=" + !disableProfilerRecs);
        Iterator<UIClient> clientWalker = es.controllingClients.iterator();
        while (clientWalker.hasNext())
        {
          UIClient currClient = clientWalker.next();
          if(!isClientAlive(allClients, currClient)) {
            clientWalker.remove();
          }
        }

        Airing newRecord = null;
        boolean autopilot = es.controllingClients.isEmpty();
        Airing defaultRecord = es.currRecord;
        boolean switchThis = false;

        /*
         * This block is used to establish a default to record in the case that
         * the current one is over. It also then terminate the current record
         * Also check for dead manual records...these occur because of the FakeAiring
         * used in time based recordings because the MR object gets held onto
         * through the currRecord airing; which means that the airing is invalid.
         */
        if (es.currRecord != null)
        {
          boolean endItNow = false;
          /*
           * If the manual record is not OK anymore (i.e. it's been cancelled) then getManualRecord will
           * return null so we've got a logic inconsistency here we need to fix:
      ManualRecord currRecMR = wiz.getManualRecord(es.currRecord);
      if (currRecMR != null && !wiz.isManualRecordOK(currRecMR))
        endItNow = true;
           */
          ManualRecord currRecMR = wiz.getManualRecord(es.currRecord);
          if ((currRecMR != null && !wiz.isManualRecordOK(currRecMR)) ||
              (es.currRecord instanceof ManualRecord.FakeAiring &&
                  !wiz.isManualRecordOK(((ManualRecord.FakeAiring)es.currRecord).getManualRecord())))
          {
            endItNow = true;
            // This is when an MR is cancelled on an active recording. We can just leave it
            // recording this program and if we shouldn't be doing that for some reason, then that'll get
            // cleaned up on the next round.
            if (es.currRecord instanceof ManualRecord.FakeAiring)
            {
              ManualRecord mr = ((ManualRecord.FakeAiring)es.currRecord).getManualRecord();
              Airing actualAir = mr.getContentAiring();
              if (!(actualAir instanceof ManualRecord.FakeAiring) &&
                  actualAir.getStartTime() <= currTime && actualAir.getEndTime() > currTime)
              {
                if (Sage.DBG) System.out.println("ManualRecord was cancelled, transition recording to the actual airing:" + actualAir);
                endItNow = false;
                currRecords.remove(es.currRecord);
                if (es.forceWatch == es.currRecord)
                  es.forceWatch = actualAir;
                es.currRecord = actualAir;
                currRecords.add(actualAir);
                defaultRecord = actualAir;
              }
            }
          }
          else if (autopilot && es.currRecordFile.isAnyLiveStream())
            endItNow = true;
          else if (es.currRecord.getSchedulingEnd() <= currTime)
          {
            // Check if we're in the stop short area.
            if (es.currRecord instanceof ManualRecord.FakeAiring)
              endItNow = true;
            else if (currRecMR == null && (autopilot || es.currRecord.getEndTime() <= currTime))
            {
              // NARFLEX - 04/21/09 - I changed this so it doesn't end the record now if
              // there are clients that still have control of this tuner. If something else
              // needs to be recorded; then it will get switched out below. This relates to stop padding
              // on Favorites when they end early and that tuner is still under live control. The user should
              // still be watching that airing even though it is outside the scheduling bounds. But when it really
              // is over we definitely need to terminate it then or unless there's another forced/MR/Fav it won't
              // tune to the next Airing.
              // OLDNOTE: This is not really correct. When a Favorite is done early we may
              // still need to continue recording that file for a user to watch
              endItNow = true;
            }
            else if (currRecMR != null && currRecMR.getSchedulingAiring().getEndTime() <= currTime &&
                es.currRecord.getEndTime() <= currTime)
            {
              // Narflex - 3/12/13 - We have a case we missed here where we have a MR for the current recording
              // but that MR is over due to negative stop padding. If someone was watching that channel we'd then
              // end up with the Seeker continually re-evaluating and we'd never stop recording that program.
              endItNow = true;
            }
          }

          if (endItNow)
          {
            if (Sage.DBG) System.out.println("Current record is over.");
            defaultRecord = null;
            endRecord(es, currTime, switchThis = fastMuxSwitch && es.capDev.supportsFastMuxSwitch() && es.capDev.getRecordedBytes() > 0 &&
                es.currRecordFile.getSize() > 0);
            switchAny = true;
          }
        }

        // If we don't have a default, get one if there's one available and we want a default
        if (defaultRecord == null && es.lastStationID != 0 &&
            (!autopilot || !disableProfilerRecs || es.capDev.shouldNotStopDevice()) && es.stationSet.contains(es.lastStationID))
        {
          Airing[] nextOnChan = wiz.getAirings(es.lastStationID,
              currTime, currTime + 1, false);
          if (nextOnChan.length > 0)
          {
            defaultRecord = nextOnChan[0];
            // NARFLEX: 6/17/09 - There's a bad case here where if the default recording on a channel
            // ends up being a manual recording that's being done by another tuner; then it'll slip through
            // the checks below potentially and end up using the same MediaFile on multiple tuners (which is very bad)
            // We'll make this even stronger though so that the default won't get set if its already being recorded by
            // another tuner
            if (currRecords.contains(defaultRecord))
              defaultRecord = null;
            else
            {
              ManualRecord swapMR = wiz.getManualRecord(defaultRecord);
              if (swapMR != null)
              {
                if (currRecords.contains(swapMR.getSchedulingAiring()))
                  defaultRecord = null;
                else if (swapMR.getEndTime() > currTime)
                  defaultRecord = swapMR.getSchedulingAiring();
              }
            }
          }
          if (Sage.DBG) System.out.println("defaultRecord=" + defaultRecord);
        }

        Airing nextRecord = null;
        Vector<Airing> schedToUse;
        boolean mustSeeControl = false;
        if (!disableProfilerRecs &&
            (autopilot || (defaultRecord == null)))
        {
          //schedToUse = es.schedule;
          // NARFLEX: 11/1/2006 - I can't see why we don't want to remove the deniedMustSees
          // from the schedule we use here as well. We fall into this block when IR is on and
          // no one is watching anything or we can't find a default record. I guess the logic would be
          // that if you denied a channel change and then stopped watching TV, it would then resume what it wanted
          // to do. But that doesn't seem like proper behavior. We also need to put it here for the case when
          // a currently recording favorite conflicts with a scheduled MR and the user tries to override recording
          // of the favorite.
          schedToUse = new Vector<Airing>(es.schedule);
          schedToUse.removeAll(deniedMustSees);
        }
        else
        {
          schedToUse = new Vector<Airing>(es.mustSee);
          schedToUse.removeAll(deniedMustSees);
          mustSeeControl = true;
        }

        // There's another case here of when Sage is put to sleep with the profiler disabled
        // we need to clear the default record because there is no such thing. Do not clear
        // the default recording; we need that to maintain the current recording unless it's one
        // that's not scheduled.
        // But if it's not in there; because it was a forceWatch request; then it'll get cleared
        // when put to sleep as desired.
        // NOTE: On 7/22/03 I commented out disableProfilerRecs. This conditional
        // serves the purpose of clearing the default recording if we're in automatic
        // mode and its not scheduled. We want that regardless of whether or not the profiler
        // is disabled; this is because too many people complain about it continually
        // recording the same channel when there's lack of profiler data.
        // 1/11/05 - I updated this to not disable the default record if it's an MR because
        // this might be during the gap before the Scheduler has finished its update
        if (/*disableProfilerRecs && */defaultRecord != null && autopilot && !schedToUse.contains(defaultRecord) &&
            !es.capDev.shouldNotStopDevice() && (wiz.getManualRecord(defaultRecord) == null ||
            (wiz.getManualRecord(defaultRecord).getEndTime() < Sage.time())))
          defaultRecord = null;

        // Because of the asynchronicity of the Scheduler now, we need to skip
        // over the airings in the schedule that will be cleaned on the next run
        // of the scheduler. (Changed on 7/13/03 from clearing them to skipping them)
        // 5/26/05 - Changed it to also skip over any live or buffered airings as records
        // since these should never be part of the schedule and are controlled by the user watching them
        for (int i = 0; i < schedToUse.size(); i++)
        {
          Airing testMe = schedToUse.get(i);
          if (testMe.getSchedulingEnd() <= currTime || testMe == es.currRecord ||
              (testMe instanceof MediaFile.FakeAiring && ((MediaFile.FakeAiring) testMe).getMediaFile().isAnyLiveStream()) ||
              (testMe instanceof ManualRecord.FakeAiring && !wiz.isManualRecordOK(((ManualRecord.FakeAiring) testMe).getManualRecord())))
          {
            //schedToUse.remove(0);
          }
          else
          {
            nextRecord = testMe;
            break;
          }
        }

        // The default record needs to be killed if someone else is using it
        if (es.currRecord != defaultRecord && currRecords.contains(defaultRecord))
          defaultRecord = null;

        long nextTTA = (nextRecord == null) ? Long.MAX_VALUE : Math.max(0, nextRecord.getSchedulingStart() - currTime);
        if (Sage.DBG) System.out.println("Seeker in AUTOMATIC mode nextRecord=" + nextRecord + " nextTTA=" + nextTTA);

        if (nextRecord != null && nextTTA > 0 && nextTTA <= chanWaitAskAdv &&
            mustSeeControl && defaultRecord != null && (defaultRecord.stationID != nextRecord.stationID) &&
            !completeChanChangeAsks.contains(nextRecord))
        {
          completeChanChangeAsks.addElement(nextRecord);
          if (Sage.DBG) System.out.println("Channel Change Query for " + nextRecord);
          final Airing promptRecord = nextRecord;
          final long promptTTA = nextTTA;
          clientWalker = es.controllingClients.iterator();
          while (clientWalker.hasNext())
          {
            final UIClient currClient = clientWalker.next();
            if (currClient instanceof UIManager && !Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, (UIManager) currClient))
            {
              continue;
            }
            // This is the client we want to send the network popup to.
            Pooler.execute(new Runnable()
            {
              public void run()
              {
                if (Sage.DBG) System.out.println("Waiting 3 seconds before we send out the deny hook so the client ends up on the target menu...");
                try{Thread.sleep(3000);}catch (Exception e){}
                Object res = currClient.processUIClientHook("DenyChannelChangeToRecord", new Object[] {
                    promptRecord });
                if (res instanceof Boolean && (Boolean) res)
                  deniedMustSees.add(promptRecord);
              }
            }, "NetworkPopup");
            // NOTE: Narflex - 5/9/11 - I changed this to send the notice to all clients that are actively using this tuner since we
            // want all of them to agree to this in order for it to happen. This also allows more correct application of the new permissions
            // system since for SageTVClient we won't know if it has permissions or not from the server side.
            //break;
          }
        }

        if (nextRecord == null)
        {
          // We can do nothing whatsoever automatically, stick with the default
          wakeupTime = Math.min(wakeupTime, Long.MAX_VALUE);
          newRecord = defaultRecord;
        }
        else if (nextTTA > leadTime)
        {
          // The next thing isn't ready yet, keep what were doing but
          // be sure to wake up when its ready to switch
          wakeupTime = Math.min(wakeupTime, (nextTTA > chanWaitAskAdv) ? (nextTTA - chanWaitAskAdv + currTime) :
            (nextTTA - leadTime + currTime));
          newRecord = defaultRecord;
        }
        else
        {
          if ((nextTTA == 0) || (defaultRecord == null))
          {
            // The new record is ready to go OR it's lead time
            // is up and there's no default. Wake up when it's over
            wakeupTime = Math.min(wakeupTime, nextRecord.getSchedulingEnd());
            newRecord = nextRecord;
          }
          else
          {
            // The next record's lead time is up, but the default
            // exists so keep it on. Wakeup when next is ready.
            newRecord = defaultRecord;
            wakeupTime = Math.min(wakeupTime, nextTTA + currTime);
          }
        }
        ManualRecord mr = wiz.getManualRecord(newRecord);
        if (es.forceWatch != null)
        {
          // I added this on 9/25/02 to skip force watches when we're doing a current
          // manual record. This way we don't kill current recordings without them
          // being disabled first.
          // 11/18, I added a requestWatch check for this, so this should never happen here anymore
          if (newRecord != null && mr != null &&
              mr.getEndTime() > currTime)
          {
            if (Sage.DBG) System.out.println("FORCE WATCH abandoned because ManualRecord is active! clients=" +
                es.controllingClients);
          }
          else if (currRecords.contains(es.forceWatch) && (es.forceWatch != es.currRecord))
          {
            // Narflex - 6/20/13 - I added the above check for comparing the forceWatch to
            // the currRecord because otherwise we are not continuing to honor the forceWatch
            // throughout it's duration. This never came up because the Scheduler would usually
            // have modified it's schedule before we got here and put the forceWatch in for this
            // tuner already. But in the case of a slow Scheduler we may have a conflict with the
            // existing schedule after we have executed the forceWatch and then revert back to
            // what was scheduled here instead because we did not override the newRecord.
            if (Sage.DBG) System.out.println("FORCE WATCH abandoned because it's currently being recorded clients=" +
                es.controllingClients);
          }
          else
          {
            if (!(es.forceWatch instanceof ManualRecord.FakeAiring) || wiz.isManualRecordOK(((ManualRecord.FakeAiring) es.forceWatch).getManualRecord()))
            {
              if (Sage.DBG) System.out.println("FORCE WATCH executing clients=" +
                  es.controllingClients);
              newRecord = es.forceWatch;
            }
            else
            {
              if (Sage.DBG) System.out.println("FORCE WATCH skipping ManualRecord because it is no longer valid: " + es.forceWatch);
            }
          }
          es.forceProcessed = true;
        }

        // In rare cases, an MR will be created after the scheduler has asked the seeker for what is
        // reality. It will schedule a new recording on a different encoder. Don't let this happen.
        Airing testAiring = newRecord;
        if(newRecord instanceof ManualRecord.FakeAiring && es.currRecord != newRecord && mr != null) {
          testAiring = mr.getContentAiring();
          if (Sage.DBG && mr.getContentAiring() != null)
            System.out.println("newRecord(" + newRecord + ") was fake; real: "
                + mr.getContentAiring());
        }
        if (currRecords.contains(testAiring) && es.currRecord != testAiring)
        {
          // Someone else is recording it already, this is absolutely wrong
          // in all cases so abort this recording
          // The Scheduler will correct these mistakes.
          if (Sage.DBG) System.out.println("Aborting recording "+newRecord+" because it's already being done: " + testAiring);
          newRecord = null;
          switchAny = true;
        }
        if (Sage.DBG) System.out.println("newRecord=" + newRecord);

        // Check for starting a manual record on something we're already recording.
        // In that case, don't cut the file; just update the EncoderState variables
        if (newRecord instanceof ManualRecord.FakeAiring &&
            ((ManualRecord.FakeAiring) newRecord).getManualRecord() == wiz.getManualRecord(es.currRecord) &&
            stringEquals(getQualityForAiring(newRecord, es.capDev, !es.controllingClients.isEmpty()), es.myQuality))
        {
          if (Sage.DBG) System.out.println("Record was enabled on what's already being recorded; update status.");
          removeFromCurrRecords(es.currRecord);
          if (es.forceWatch == es.currRecord)
          {
            es.forceWatch = null;
            es.forceProcessed = false;
          }
          es.currRecord = newRecord;
          addToCurrRecords(es.currRecord);
        }

        if ((es.currRecord != newRecord) && (es.currRecord != null))
        {
          if (Sage.DBG) System.out.println("Change in record, logging recorded data.");
          endRecord(es, currTime, switchThis = fastMuxSwitch && es.capDev.supportsFastMuxSwitch() && es.capDev.getRecordedBytes() > 0 &&
              es.currRecordFile.getSize() > 0);
          switchAny = true;
        }

        if ((es.currRecord != newRecord) && (newRecord != null))
        {
          pendingStarts.add(new Object[] { es, newRecord, currTime, switchThis });
          addToCurrRecords(newRecord);
          if (Sage.DBG) System.out.println("Change in record to another show. Entering device record mode. - LATER");
        }
        else if (newRecord == null)
        {
          if (Sage.DBG) System.out.println("NOTHING TO RECORD FOR NOW...");
          if (switchThis)
          {
            // We need to shut off the encoder because it was prepped for a fast switch.
            es.capDev.stopEncoding();
            completeFileSwitch(es, currTime);
          }
          // Enable data scanning if this capture device supports it since it's stopped now
          if (!es.capDev.isDataScanning())
          {
            CaptureDeviceInput[] cdis = es.capDev.getInputs();
            for (int j = 0; j < cdis.length; j++)
            {
              if (es.capDev.wantsDataScanning(cdis[j]) && es.capDev.isLoaded())
              {
                if (Sage.DBG) System.out.println("Enabling data scanning for input " + cdis[j]);
                try
                {
                  es.capDev.enableDataScanning(cdis[j]);
                  epg.kick();
                }
                catch (EncodingException ee)
                {
                  // NOTE: We should post a message to the system here about the error!!!
                  System.out.println("ERROR enabling data scanning of:" + ee);
                  sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createDataScanFailureMsg(cdis[j]));
                  ee.printStackTrace();
                }
                break;
              }
            }
          }
          else if (!es.capDev.wantsDataScanning(es.capDev.getActiveInput()))
          {
            if (Sage.DBG) System.out.println("Diabling data scanning for input " + es.capDev.getActiveInput());
            es.capDev.disableDataScanning();
          }
        }
        else
        {
          if (Sage.DBG) System.out.println("Keeping record just the way it is.");
        }
      }

      for (int i = 0; i < pendingStarts.size(); i++)
      {
        Object[] currStartData = pendingStarts.elementAt(i);
        if (Sage.DBG) System.out.println("Change in record to another show. Entering device record mode. - NOW");
        try
        {
          startRecord((EncoderState) currStartData[0], (Airing) currStartData[1],
            (Long) currStartData[2], (Boolean) currStartData[3]);
        } catch (Throwable e) {
          Catbert.distributeHookToAll(
            "MediaPlayerError", new Object[] { Sage.rez("Capture"), e.getMessage() });
        }
      }
    }

    if (switchAny)
    {
      // If we change the recording on the watcher this could cause
      // it to miss its timing so wake it up to recheck it
      VideoFrame.kickAll();

      // This might be affected so it needs to run
      sched.kick(true);
    }

    return wakeupTime;
  }

  public void goodbye()
  {
    //Set savedPartials = new HashSet(currRecordFiles);
    // NOTE: NARFLEX - 02/28/08 - This needs to be synchronized on the Seeker because the VideoFrame will
    // call back into the Seeker for finishWatch; and that will require this sync lock. But if the Seeker
    // already has that lock and is doing an inactiveFile distribution; it could have the lock on all of the
    // activeVFs; which would then cause a deadlock.
    synchronized (this)
    {
      VideoFrame.goodbyeAll();
    }
    alive = false;
    Ministry.getInstance().goodbye();
    FSManager.getInstance().goodbye();
    unmountPaths(smbMountMap.values());
    unmountPaths(nfsMountMap.values());
    synchronized (this)
    {
      notifyAll();
    }
    if (seekerThread != null)
      try{seekerThread.join(30000);}catch(InterruptedException e){}
    for (int i = 0; i < exportPlugins.size(); i++)
      exportPlugins.get(i).closePlugin();
    /*if (!Sage.client && Sage.getBoolean(prefs + DELETE_PARTIALS_ON_EXIT, true))
{
  if (Sage.DBG) System.out.println("Seeker clearing partial files...");
  MediaFile[] mfs = wiz.getFiles();
  for (int i = 0; i < mfs.length; i++)
  {
    if (!mfs[i].archive && !mfs[i].isCompleteRecording() && !savedPartials.contains(mfs[i]) &&
      wiz.getManualRecord(mfs[i].getContentAiring()) == null && !god.isLoveAir(mfs[i].getContentAiring()))
    {
      destroyFile(mfs[i], false);
    }
  }
}*/
  }


  // This may prompt the user, and will return null if there's a rejection or no encoder can be found.
  // The big difference between now and later is that later doesn't have recordings
  // bounds to a specific Encoder yet. Once something's started, we don't want it to switch
  // sources which is one of the limitations of the NOW algorithm
	private EncoderState findBestEncoderForNow(Airing theAir, boolean recordIt, UIClient requestor,
      int[] returnError)
  {
    if (Sage.DBG) System.out.println("findBestEncoderForNow(" + theAir + " record=" + recordIt
        + " host=" + requestor + ")");
    Set<EncoderState> tryUs = new HashSet<EncoderState>(encoderStateMap.values());
    int desireLevel = 0;
    EncoderState iKnowIWantThisEncoder = null;
    MediaFile mf = wiz.getFileForAiring(theAir);
    if (mf != null && mf.isAnyLiveStream())
    {
      CaptureDeviceInput streamInput = mmc.getCaptureDeviceInputNamed(mf.encodedBy);
      if (streamInput != null)
      {
        iKnowIWantThisEncoder = encoderStateMap.get(streamInput.getCaptureDevice());
        // If someone else is using this tuner then don't allow us to use it in live mode.
        if (iKnowIWantThisEncoder != null &&
            (iKnowIWantThisEncoder.controllingClients.size() > 1 ||
                (!iKnowIWantThisEncoder.controllingClients.isEmpty() && !iKnowIWantThisEncoder.controllingClients.contains(requestor))))
        {
          returnError[0] = VideoFrame.WATCH_FAILED_ALL_ENCODERS_UNDER_LIVE_CONTROL;
          return null;
        }
      }
    }
    boolean multiConflictCapable = iKnowIWantThisEncoder == null && requestor != null
        && "true".equalsIgnoreCase(requestor.getCapability(recordIt ? "RecordRequestLiveMultiConflict"
            : "WatchRequestLiveMultiConflict"));
    Map<Airing, EncoderState> conflictMap = multiConflictCapable ? new LinkedHashMap<Airing, EncoderState>(encoderStateMap.size()) : null;
    while (true)
    {
      Iterator<EncoderState> walker = tryUs.iterator();
      int leastMustSeeOverlaps = Integer.MAX_VALUE;
      // NOTE: Below where we multiply by 200 and then add the highest input configured type is a way to compare capture devices
      // with a combination of merit + input quality without overriding the merit settings. This will then be sure live tuner selection
      // uses similar priority rules to the scheduling algorithms.
      int highestMerit = -1;
      float lowestWPSameMerit = 2;
      long maxTimeUntilNextMustSee = Long.MIN_VALUE;
      EncoderState highestES = iKnowIWantThisEncoder;
      while (walker.hasNext() && iKnowIWantThisEncoder == null)
      {
        EncoderState es = walker.next();
        if (!es.stationSet.contains(theAir.stationID))
        {
          walker.remove();
          continue;
        }
        if (es.currRecord != null && es.currRecord.stationID == theAir.stationID && theAir.stationID != 0)
        {
          if (Sage.DBG) System.out.println("foundBestEncoder0=" + es);
          returnError[0] = 0;
          return es;
        }
        // If more than one client is watching this tuner, we can't change it. We can only
        // change it if its not in use, or the requesting client is the only one watching it AND it wasn't a record request
        if ((es.controllingClients.size() > 1) ||
            (!es.controllingClients.isEmpty() && (!es.controllingClients.contains(requestor) || recordIt)))
        {
          walker.remove();
          returnError[0] = VideoFrame.WATCH_FAILED_ALL_ENCODERS_UNDER_LIVE_CONTROL;
          continue;
        }
        Airing tempRecord = es.currRecord;
        if (tempRecord == null || (!tempRecord.isMustSee() && (wiz.getManualRecord(tempRecord) == null ||
            wiz.getManualRecord(tempRecord).getEndTime() < Sage.time())))
        {
          // Find how many must sees occuring later this will conflict with and minimize that
          List<Airing> msCopy = new ArrayList<Airing>(es.mustSee);
          int currOverlaps = 0;
          long currTimeUntilNextMustSee = Long.MAX_VALUE;
          for (int i = 0; i < msCopy.size(); i++)
          {
            Airing checkAir = msCopy.get(i);
            if (i == 0)
              currTimeUntilNextMustSee = checkAir.getSchedulingStart() - Sage.time();
            if (checkAir.getSchedulingStart() >= theAir.getSchedulingEnd())
              break;
            else if (checkAir.doesSchedulingOverlap(theAir))
              currOverlaps++;
          }
          if (desireLevel > 0 ||
              (currOverlaps < leastMustSeeOverlaps) ||
              (currOverlaps == leastMustSeeOverlaps &&
              (es.capDev.getMerit()*200 + es.capDev.getHighestQualityConfiguredInputType() > highestMerit ||
                  (es.capDev.getMerit()*200 + es.capDev.getHighestQualityConfiguredInputType() == highestMerit &&
                  ((tempRecord == null ? 0 : calcW(tempRecord)) < lowestWPSameMerit)))))
          {
            if (currTimeUntilNextMustSee > maxTimeUntilNextMustSee)
            {
              leastMustSeeOverlaps = currOverlaps;
              highestMerit = es.capDev.getMerit()*200 + es.capDev.getHighestQualityConfiguredInputType();
              highestES = es;
              lowestWPSameMerit = tempRecord == null ? 0 : calcW(tempRecord);
              maxTimeUntilNextMustSee = currTimeUntilNextMustSee;
              if (desireLevel > 0)
              {
                // this is possible because of the asynchronicity of this
                break;
              }
            }
          }
        }
        else if (desireLevel > 0 && (wiz.getManualRecord(tempRecord) == null ||
            wiz.getManualRecord(tempRecord).getEndTime() < Sage.time()) && tempRecord.isMustSee())
        {
          if (desireLevel > 1 ||
              es.capDev.getMerit()*200 + es.capDev.getHighestQualityConfiguredInputType() > highestMerit)
          {
            highestMerit = es.capDev.getMerit()*200 + es.capDev.getHighestQualityConfiguredInputType();
            highestES = es;
            if (desireLevel > 1)
            {
              // this is possible because of the asynchronicity of this
              break;
            }
          }
        }
        else if (desireLevel > 1 && wiz.getManualRecord(tempRecord) != null)
        {
          if (es.capDev.getMerit()*200 + es.capDev.getHighestQualityConfiguredInputType() > highestMerit)
          {
            highestMerit = es.capDev.getMerit()*200 + es.capDev.getHighestQualityConfiguredInputType();
            highestES = es;
          }
        }
      }
      if (highestES != null)
      {
        // We may need to prompt the user about what we're killing here
        // Check to see if we're overriding a current manual record.
        ManualRecord mr = wiz.getManualRecord(highestES.currRecord);
        Airing tempCurrRecord = highestES.currRecord;
        if (mr != null && mr.getContentAiring() != theAir && mr.getEndTime() > Sage.time())
        {
          if(multiConflictCapable) {
            if (Sage.DBG) System.out.println("findBestEncoderMultiManual: rec:" + tempCurrRecord + " es:" + highestES);
            conflictMap.put(tempCurrRecord, highestES);
            tryUs.remove(highestES);
            highestES = null;
            continue;
          }
          Object res = (requestor == null) ? null : requestor.processUIClientHook(recordIt ? "RecordRequestLiveConflict" : "WatchRequestConflict",
              new Object[] { theAir, mr.getSchedulingAiring() });
          // Conflict resolution, ask about what you're going to kill
          if (!(res instanceof Boolean) || !((Boolean) res))//("No".equals(res) || res == null)
          {
            tryUs.remove(highestES);
            highestES = null;
            returnError[0] = VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT;
            // They tried to do a direct hardware control but rejected the conflict about what
            // it needed to kill for this to happen.
            if (iKnowIWantThisEncoder != null) return null;
            continue;
          }
          else
          {
            returnError[0] = 0;
            return removeManualRecord(highestES, mr);
          }
        }
        else if (tempCurrRecord != null && tempCurrRecord.isMustSee() && !deniedMustSees.contains(tempCurrRecord) && tempCurrRecord != theAir)
        {
          if(multiConflictCapable) {
            if (Sage.DBG) System.out.println("findBestEncoderMultiMustSee: rec:" + tempCurrRecord + " es:" + highestES);
            conflictMap.put(tempCurrRecord, highestES);
            tryUs.remove(highestES);
            highestES = null;
            continue;
          }
          // Conflict resolution, ask about what you're going to kill
          Object res = (requestor == null) ? null : requestor.processUIClientHook(recordIt ? "RecordRequestLiveConflict" : "WatchRequestConflict",
              new Object[] { theAir, tempCurrRecord });
          if (!(res instanceof Boolean) || !((Boolean) res))//("No".equals(res) || res == null)
          {
            tryUs.remove(highestES);
            highestES = null;
            returnError[0] = VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT;
            // They tried to do a direct hardware control but rejected the conflict about what
            // it needed to kill for this to happen.
            if (iKnowIWantThisEncoder != null) return null;
            continue;
          }
          else
          {
            returnError[0] = 0;
            return removeMustSee(highestES, tempCurrRecord);
          }
        }

        if (Sage.DBG) System.out.println("foundBestEncoder3=" + highestES);
        returnError[0] = 0;
        return highestES;
      }

      // For multicast capture we don't want to restrict this to local UIs
      if (false && !recordIt && desireLevel == 0 && requestor != null /*&& requestor.getUIClientType() == UIClient.LOCAL*/)
      {
        // Watch requests that aren't satisfied without prompting should be first
        // checked against any non-encoding capture devices. If there's any available
        // we'll use that first.
        CaptureDevice[] allCapDevs = mmc.getCaptureDevices();
        CaptureDeviceInput liveInput = null;
        boolean localUser = requestor.getUIClientType() == UIClient.LOCAL;
        // Check the current encoder map for any non-encoding devices
        synchronized (this)
        {
          Set<EncoderState> currEncoders = new HashSet<EncoderState>(encoderStateMap.values());
          for (EncoderState testES : currEncoders)
          {
            boolean localDevice = !testES.capDev.isNetworkEncoder();
            if (!testES.capDev.canEncode() && (!localDevice || (localDevice && localUser)) && (!localDevice || testES.controllingClients.isEmpty() ||
                (testES.controllingClients.size() == 1 && testES.controllingClients.contains(requestor))))
            {
              // Check to see if this device can tune this station
              CaptureDeviceInput[] allSrcDesc = testES.capDev.getInputs();
              for (int j = 0; j < allSrcDesc.length; j++)
              {
                if (allSrcDesc[j].getProviderID() != 0)
                {
                  EPGDataSource ds = EPG.getInstance().getSourceForProviderID(allSrcDesc[j].getProviderID());
                  if (ds != null && ds.canViewStation(theAir.stationID) &&
                      EPG.getInstance().getChannel(allSrcDesc[j].getProviderID(), theAir.stationID).length() > 0)
                  {
                    if (Sage.DBG) System.out.println("Found an unused non-encoding capture device for live viewing:" + allSrcDesc[j]);
                    testES.absoluteClientControl = allSrcDesc[j];
                    returnError[0] = VideoFrame.WATCH_FAILED_NO_ENCODERS_HAVE_STATION;
                    return testES;
                  }
                }
              }
            }
          }
        }

        for (int i = 0; i < allCapDevs.length && liveInput == null; i++)
        {
          boolean localDevice = !allCapDevs[i].isNetworkEncoder();
          if (!encoderStateMap.containsKey(allCapDevs[i]) && !allCapDevs[i].canEncode() && (!localDevice || (localDevice && localUser)))
          {
            // Check to see if this device can tune this station
            CaptureDeviceInput[] allSrcDesc = allCapDevs[i].getInputs();
            for (int j = 0; j < allSrcDesc.length && liveInput == null; j++)
            {
              if (allSrcDesc[j].getProviderID() != 0)
              {
                EPGDataSource ds = EPG.getInstance().getSourceForProviderID(allSrcDesc[j].getProviderID());
                if (ds != null && ds.canViewStation(theAir.stationID) &&
                    EPG.getInstance().getChannel(allSrcDesc[j].getProviderID(), theAir.stationID).length() > 0)
                  liveInput = allSrcDesc[j];
              }
            }
          }
        }

        if (liveInput != null)
        {
          synchronized (this)
          {
            if (Sage.DBG) System.out.println("Found a non-encoding capture device for live viewing:" + liveInput);
            CaptureDevice cDev = liveInput.getCaptureDevice();
            if (Sage.DBG) System.out.println("Seeker adding new Encoder for absolute control: " + cDev);
            highestES = new EncoderState(cDev);
            encoderStateMap.put(cDev, highestES);
            highestES.schedule = new Vector<Airing>();
            highestES.mustSee = new Vector<Airing>();
            highestES.scheduleContent.clear();
            highestES.mustSeeContent.clear();
            highestES.absoluteClientControl = liveInput;
          }
          returnError[0] = VideoFrame.WATCH_FAILED_NO_ENCODERS_HAVE_STATION;
          return highestES;
        }
      }

      // New feature; allow for the sending of a single conflict resolution request with all
      // conflicts in the list.
      if(multiConflictCapable && !conflictMap.isEmpty() && tryUs.isEmpty()) {
        Object res = (requestor == null) ? null : requestor.processUIClientHook(recordIt ? "RecordRequestLiveMultiConflict" : "WatchRequestLiveMultiConflict",
            new Object[] { theAir, new Vector<Airing>(conflictMap.keySet())});
        if (Sage.DBG) System.out.println("Got an conflict answer of: " + res);
        if(res instanceof Airing) {
          Airing selected = (Airing)res;
          // NOTE: Some time may pass between calling processUIClientHook and now. We need to verify
          //       that the selected airing is still available.  If not, re-ask again.
          // NOTE: While there could be a free tuner avaible between asking and canceling; the user
          //       hit a button and expected a result. Go with the expected result and be happy.

          // Case 1: Airing has ended; return error letting them know they've missed the boat.
          if(selected.getSchedulingEnd() <= Sage.time()) {
            if (Sage.DBG) System.out.println("User didn't respond in time; watch request expired: " + theAir);
            returnError[0] = VideoFrame.WATCH_FAILED_AIRING_EXPIRED;
            return null;
          }

          // Case 2: Encoder is still valid for picking:
          //   a) tuner is free
          //   b) tuner is not under control by anyone and isn't recording a favorite/mr or its currently recording the approved conflict
          //   c) we have sole control and this isn't a record request (think: single tuner watching live, request to record another service from epg; fail.)
          EncoderState esToSteal = conflictMap.get(selected);
          returnError[0] = 0;
          Airing tempRecord = esToSteal.currRecord;
          ManualRecord mr = wiz.getManualRecord(tempRecord);
          if(tempRecord == null
              || (esToSteal.controllingClients.isEmpty() && (selected == tempRecord || !(tempRecord.isMustSee() || (mr != null && mr.getEndTime() > Sage.time()))))
              || !((esToSteal.controllingClients.size() > 1) || (!esToSteal.controllingClients.isEmpty() && (!esToSteal.controllingClients.contains(requestor) || recordIt))))
          {
            if(tempRecord != null) {
              if (mr != null && mr.getEndTime() > Sage.time()) {
                removeManualRecord(esToSteal, mr);
              } else if (tempRecord.isMustSee()) {
                removeMustSee(esToSteal, tempRecord);
              }
            }
            return esToSteal;
          }

          // Case 3: User needs to get more selections
          if (Sage.DBG) System.out.println("findBestEncoderForNow: MultiConflict response late/invalid. Trying again.");
          return findBestEncoderForNow(theAir, recordIt, requestor, returnError);
        } else {
          if (Sage.DBG) System.out.println("User didn't select a recording to cancel");
          returnError[0] = VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT;
          return null;
        }
      }

      // No encoders receive this station....this shouldn't really happen
      if (tryUs.isEmpty())
      {
        if (returnError[0] == 0)
          returnError[0] = VideoFrame.WATCH_FAILED_NO_ENCODERS_HAVE_STATION;
        return null;
      }

      // They tried to do a direct hardware control but rejected the conflict about what
      // it needed to kill for this to happen.
      if (iKnowIWantThisEncoder != null) return null;

      if (desireLevel >= 2)
        return null;

      desireLevel++;
    }
  }

  private EncoderState removeMustSee(EncoderState es, Airing tempCurrRecord) {
    // This used to be after this thing in the synchronized block....I'm not sure if there's
    // a solid reason or just paranoia about sync with the deniedMustSees Vector
    deniedMustSees.addElement(tempCurrRecord);
    if (Sage.DBG) System.out.println("foundBestEncoder2=" + es);
    return es;
  }

  private EncoderState removeManualRecord(EncoderState es, ManualRecord mr) {
    // It's OK if we remove a recurring recording here since it'll have at least another
    // one scheduled out later
    if (Sage.DBG) System.out.println("watch overlaps with an existing manual, removing " + mr);
    if (mr.recur != 0)
    {
      // Remove all recurring instances of this
      wiz.removeManualRecord(mr);
      ManualRecord[] allMRs = wiz.getManualRecords();
      for (int i = 0; i < allMRs.length; i++)
      {
        if (allMRs[i].stationID == mr.stationID && allMRs[i].duration == mr.duration &&
            allMRs[i].recur == mr.recur && allMRs[i].isSameRecurrence(mr.startTime))
        {
          if (wiz.getFileForAiring(allMRs[i].getContentAiring()) != null)
            allMRs[i].clearRecurrence();
          else
            wiz.removeManualRecord(allMRs[i]);
        }
      }
    }
    else
      wiz.removeManualRecord(mr);
    if (Sage.DBG) System.out.println("foundBestEncoder1=" + es);
    return es;
  }


  public void finishWatch(UIClient uiClient)
  {
    if (Sage.client)
    {
      if (NetworkClient.getSN() != null)
        NetworkClient.getSN().finishWatch(uiClient);
      return;
    }
    if (Sage.DBG) System.out.println("Seeker.finishWatch(" + uiClient + ")");


    boolean madeChanges = false;
    synchronized (this)
    {
      // Also release any encoders who currently are controlled by this client
      for (EncoderState tempES : encoderStateMap.values())
      {
        if (tempES.controllingClients.remove(uiClient))
        {
          if (tempES.controllingClients.isEmpty())
          {
            tempES.forceWatch = null;
            tempES.forceProcessed = false;
            tempES.absoluteClientControl = null;
            madeChanges = true;
          }
        }
      }
    }
    synchronized (clientWatchFileMap)
    {
      Iterator<Set<UIClient>> walker = clientWatchFileMap.values().iterator();
      while (walker.hasNext())
      {
        Set<UIClient> currSet = walker.next();
        if (currSet.remove(uiClient))
        {
          // This was the one to remove for this client
          if (currSet.isEmpty())
          {
            walker.remove();
            madeChanges = true;
          }
        }
      }
    }
    if (madeChanges)
    {
      kick();
      // With curr records getting inserted into the schedule, we need to evaluate it
      // on simple watch changes
      sched.kick(false); // this'll come back and kick us again, but that's OK
    }
  }

  public int forceChannelTune(String mmcInputName, String chanString, UIClient uiClient)
  {
    if (Sage.client)
    {
      return NetworkClient.getSN().forceChannelTune(uiClient, mmcInputName, chanString);
    }
    CaptureDeviceInput forcedInput = mmc.getCaptureDeviceInputNamed(mmcInputName);
    if (forcedInput == null)
      return VideoFrame.WATCH_FAILED_LIVE_STREAM_UKNOWN_INPUT;
    synchronized (this)
    {
      EncoderState forcedEncoder = encoderStateMap.get(forcedInput.getCaptureDevice());
      // It is possible that this encoder is not listed with the Seeker because it has not been configured
      if (forcedEncoder != null && !forcedEncoder.controllingClients.contains(uiClient))
        return VideoFrame.WATCH_FAILED_FORCE_REQUEST_WITHOUT_CONTROL;

      // Attempt to resolve this logical channel to a physical one
      chanString = EPG.getInstance().guessPhysicalChanFromLogicalChan(forcedInput.getProviderID(), chanString);
      forcedInput.tuneToChannel(chanString);
    }
    return VideoFrame.WATCH_OK;
  }

  public MediaFile requestWatch(MediaFile watchFile, int[] errorReturn, UIClient uiClient) {
    if (Sage.client)
    {
      if (watchFile.getGeneralType() == MediaFile.MEDIAFILE_LOCAL_PLAYBACK)
        return watchFile;
      else
        return NetworkClient.getSN().requestWatch(uiClient, watchFile, errorReturn);
    }

    if (!watchFile.verifyFiles(true))
    {
      if (Sage.DBG) System.out.println("Bad watchFile it's failed verification");
      errorReturn[0] = VideoFrame.WATCH_FAILED_FILES_NOT_ON_DISK; // vs Unknown?
      return null;
    }


    if (uiClient != null)
    {
      // If the client currently has live controll of an encoder, it could be watching a previously
      // ended event, in which case we do not want to release control.
      EncoderState es = getCurrEncoderForClient(uiClient);
      Airing watchAir = watchFile.getContentAiring();
      if (es != null && es.currRecord != null && watchAir != null
          && es.currRecord.getStationID() == watchAir.getStationID()
          && watchAir.getStationID() != 0) {
        if (Sage.DBG) System.out.println("Seeker.requestWatch(mf=" + watchFile
            + ") while we have an encoder{" + es + "}; not releasing live control");
        synchronized (clientWatchFileMap) {
          Iterator<Set<UIClient>> walker = clientWatchFileMap.values().iterator();
          while (walker.hasNext()) {
            Set<UIClient> currSet = walker.next();
            if (currSet.remove(uiClient)) {
              // Remove any this client has a hold on
              if (currSet.isEmpty()) walker.remove();
            }
          }
        }
      } else {
        finishWatch(uiClient);
      }
      synchronized (clientWatchFileMap)
      {
        Set<UIClient> currSet = clientWatchFileMap.get(watchFile);
        if (currSet == null)
          clientWatchFileMap.put(watchFile, currSet = new HashSet<UIClient>());
        currSet.add(uiClient);
      }
    }

    return watchFile;
  }


  private EncoderState getCurrEncoderForClient(UIClient uiClient) {
    if (Sage.client) return null;
    List<EncoderState> encStateVec = new ArrayList<EncoderState>(encoderStateMap.values());
    for (int i = 0; i < encStateVec.size(); i++)
    {
      if (encStateVec.get(i).controllingClients.contains(uiClient))
        return encStateVec.get(i);
    }
    return null;
  }

  public MediaFile requestWatch(Airing watchAir, int[] errorReturn, UIClient uiClient)
  {
    if (Sage.client)
      return NetworkClient.getSN().requestWatch(uiClient, watchAir, errorReturn);
    if (Sage.DBG) System.out.println("Called Seeker.requestWatch(" + watchAir + ")" + " hostname=" + uiClient);
    // Everything is a file watch. Sometimes the file won't exist yet and
    // we need to insert it into the schedule. We also need to check to see
    // if the Airing is the currRecord if it's not over yet before we bail.


    boolean alreadyStarted = (watchAir.getSchedulingStart() <= Sage.time()) || wiz.getFileForAiring(watchAir) != null;
    boolean airingDone = (watchAir.getSchedulingEnd() <= Sage.time()) || (watchAir instanceof MediaFile.FakeAiring);
    MediaFile livemf = wiz.getFileForAiring(watchAir);
    boolean liveStreaming = livemf != null && livemf.isAnyLiveStream();
    if (liveStreaming)
      airingDone = false;
    if (airingDone || !alreadyStarted)
    {
      errorReturn[0] = airingDone ? VideoFrame.WATCH_FAILED_AIRING_EXPIRED : VideoFrame.WATCH_FAILED_AIRING_NOT_STARTED;
      if (Sage.DBG) System.out.println("requestWatch denied for condition 1");
      return null;
    }

    // First thing we need to do is figure out which Encoder we want to try and watch this on,
    // we may need to prompt them with a choice to check more encoders if this channel is available
    // on more than one source and all those sources are doing must sees.
    // NOTE NOTE NOTE NOTE THIS IS FOR LATER TO DO There's also the case where
    // switching the encoder for something that's currently being recorded will allow us to complete
    // this request. i.e. it's recording Friends on the digital cable box, and we ask to watch something on HBO,
    // it should switch that recording over to the analog source and let us watch HBO...that's for later
    // The ordering for encoders is, with best first:
    // 1 - highest merit not recording a must see, equal merit than use lower WP of currRec or non-used source
    EncoderState es = null;
    MediaFile rv = null;
    while(true) {
      es = findBestEncoderForNow(watchAir, false, uiClient, errorReturn);
      if (es == null && (!liveStreaming || errorReturn[0] == VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT))
      {
        if (errorReturn[0] == 0)
          errorReturn[0] = VideoFrame.WATCH_FAILED_GENERAL_CANT_FIND_ENCODER;
        if (Sage.DBG) System.out.println("requestWatch can't find an encoder to use");
        return null;
      }
      rv = null;

      // NARFLEX - 9/7/2010 - Check if this is from a seamless file transition, and if so then we don't need to do the standard work() on that request. We'll know that's the
      // case if we are already a controlling client AND we are recording what has been requested to watch here. We still need to do some other maintenance,
      // but the main work cycle which requires the Seeker's sync lock can be avoided.
      if(es != null && es.controllingClients.contains(uiClient) && es.currRecord == watchAir) {
        rv = wiz.getFileForAiring(watchAir);
        break;
      }
      // Codefu - 2013-02-25 - Else we check against race conditions since we have multiple clients
      // trying to access the encoders at the same time.
      synchronized (this)
      {
        if (es != null) {
          // NOTE(codefu): findBestEncoderForNow() can race with other threads; this will verify that
          // we still have a valid *best* encoder.
          // Encoder validity:
          //   1) tuner is recording something, but its what we asked it to
          //   2) tuner has no controlling clients, in which case we've cancled the recording due to user conflict resolution
          //   3) we're the only controlling client
          Airing tempRecord = es == null ? null : es.currRecord;
          if (!((tempRecord != null && tempRecord.stationID == watchAir.stationID && watchAir.stationID != 0)
              || es.controllingClients.isEmpty()
              || (es.controllingClients.size() == 1 && es.controllingClients.contains(uiClient)))) {
            if (Sage.DBG) System.out.println("requestWatch(" + uiClient + ", " + watchAir
                + ") lost the race for es=" + es + " controlled now by: " + es.controllingClients
                + " with currRecord:" + es.currRecord);
            continue;
          }
          // NOTE: Because we raced, there's a chance that one of the encoders is actually 'better for
          // us' meaning the same program is now recording somewhere else. Use that one so we don't
          // record twice (if you've canceled something because you thought it was full, I feel bad
          // for you son, I've got 99 problems and your canceled recording ain't one)
          EncoderState sameChannelES = null;
          EncoderState sameFileES = null;
          MediaFile encoderMatchFile = Wizard.getInstance().getFileForAiring(watchAir);
          for(EncoderState searcher : encoderStateMap.values()) {
            if (searcher.currRecord != null && searcher.currRecord.stationID == watchAir.stationID
                && watchAir.stationID != 0) {
              if (encoderMatchFile == searcher.currRecordFile) {
                sameFileES = searcher;
                break;
              } else {
                sameChannelES = searcher;
              }
            }
          }
          if (sameFileES != null) {
            if (es != sameFileES) {
              if (Sage.DBG) System.out.println("Switching to the encoder that's recording the same file: " + sameFileES);
              es = sameFileES;
            }
          } else if (sameChannelES != null) {
            if (es != sameChannelES) {
              if (Sage.DBG) System.out.println("requestWatch(" + uiClient + ", " + watchAir
                  + ") won the race for es=" + es + " controlled now by: " + es.controllingClients
                  + " with currRecord:" + es.currRecord + " but a better encoder could be found at="
                  + sameChannelES + " with controlling=" + sameChannelES.controllingClients
                  + " with currRecord:" + sameChannelES.currRecord);
              es = sameChannelES;
            }
          }
          // If the tuner we are going to use is already recording the target channel; then change the
          // requested Airing to be what that tuner is recording. This will then end up playing the
          // requested channel; which is correct. If we don't do this; then we may end up flipping
          // the Airing that the tuner was using to an incorrect one (this can only happen during padding)
          // which doesn't end at the right time....or cause the start padding to be removed from the
          // other recording.
          if (es.currRecord != null && es.currRecord.stationID == watchAir.stationID && watchAir.stationID != 0) {
            Airing newWatchAir = es.currRecord;
            if (newWatchAir != watchAir) {
              System.out.println("Switching requested Airing to watch since that's what is currently recording:" + newWatchAir);
              watchAir = newWatchAir;
            }
          }
        }

        if (es == null || !es.controllingClients.contains(uiClient) || es.currRecord != watchAir || liveStreaming || !es.capDev.canEncode())
        {
          if (liveStreaming)
          {
            CaptureDeviceInput forcedInput = mmc.getCaptureDeviceInputNamed(livemf.encodedBy);
            if (forcedInput == null)
            {
              if (Sage.DBG) System.out.println("Seeker unable to resolve input name to connector:" +
                  livemf.encodedBy);
              errorReturn[0] = VideoFrame.WATCH_FAILED_LIVE_STREAM_UKNOWN_INPUT;
              return null;
            }
            if (es == null)
            {
              // The encoder wasn't in the map, but we can add it ourself now because
              // we want the Seeker to control it.
              CaptureDevice cDev = forcedInput.getCaptureDevice();
              if (Sage.DBG) System.out.println("Seeker adding new Encoder for absolute control: " + cDev);
              es = new EncoderState(cDev);
              encoderStateMap.put(cDev, es);
              es.schedule = new Vector<Airing>();
              es.mustSee = new Vector<Airing>();
              es.scheduleContent.clear();
              es.mustSeeContent.clear();
            }
            // We need this either way so that its locked to this input
            es.absoluteClientControl = forcedInput;
          }
          else if (!es.capDev.canEncode())
          {
            // We need to use the live preview file for the non-encoding case
            rv = MediaFile.getLiveMediaFileForInput(es.absoluteClientControl);
          }
          // This can happen because findBestEncoderForNow is not synced with the main Seeker loop
          if (!encoderStateMap.values().contains(es))
          {
            if (Sage.DBG) System.out.println("Seeker encoder was dropped since we found the best one, redo it now");
            continue;
          }
          ManualRecord mr = wiz.getManualRecord(watchAir);
          if (mr != null && mr.getEndTime() > Sage.time())
            es.forceWatch = mr.getSchedulingAiring();
          else
            es.forceWatch = watchAir;
          es.forceProcessed = false;
          es.controllingClients.add(uiClient);
          // Also release any encoders who currently are controlled by this client
          for (EncoderState tempES : encoderStateMap.values())
          {
            if (tempES != es && tempES.controllingClients.remove(uiClient))
            {
              if (tempES.controllingClients.isEmpty())
              {
                tempES.forceWatch = null;
                tempES.forceProcessed = false;
                tempES.absoluteClientControl = null;
              }
            }
          }
          work();
          if (rv == null)
            rv = wiz.getFileForAiring(watchAir);
        }
        else
        {
          rv = wiz.getFileForAiring(watchAir);
        }
        if (rv == null && es != null)
        {
          if (Sage.DBG) System.out.println("MediaFile for requestWatch is null...check for the tuner recording the same channel");
          if (es.currRecord != null && es.currRecord.stationID == watchAir.stationID && es.currRecord != watchAir)
          {
            rv = es.currRecordFile;
            if (Sage.DBG) System.out.println("Encoder is recording same channel..return the file for what its recording now:" + rv);
          }
        }
      }
      break;
    }
    if (rv != null)
    {
      synchronized (clientWatchFileMap)
      {
        Iterator<Set<UIClient>> walker = clientWatchFileMap.values().iterator();
        while (walker.hasNext())
        {
          Set<UIClient> currSet = walker.next();
          if (currSet.remove(uiClient))
          {
            // Remove any this client has a hold on
            if (currSet.isEmpty())
              walker.remove();
          }
        }
        Set<UIClient> oldSet = clientWatchFileMap.get(rv);
        if (oldSet == null)
          clientWatchFileMap.put(rv, oldSet = new HashSet<UIClient>());
        oldSet.add(uiClient);
      }
    }
    // With curr records getting inserted into the schedule, we need to evaluate it
    // on simple watch changes
    sched.kick(true);
    if (rv == null)
    {
      if (Sage.DBG) System.out.println("requestWatch returning null for unknown reason");
      errorReturn[0] = VideoFrame.WATCH_FAILED_GENERAL_SEEKER;
    }

    return rv;
  }

  /*
   *NOTE NOTE NOTE NOTE
   * We don't account for the following situation in a multi-user environemnt:
   * 1. User A is watching a 10 hour show on one of the tuners, nothing else is scheduled
   * 2. User B schedules recordings using all of the tuners an hour from now.
   * Result - User A's forceWatch is going to cause an Exception in the Scheduler because
   * it'll be unable to create the complete schedule.
   * Update 11/6/03: I think this is only true if user A is watching a Favorite, and then
   * it'd be in the denieds because of the conflict, so this shouldn't happen....not sure
   * if this is a real problem or not, mainly because it doesn't matter who schedule's manual records and
   * then this would cause havoc in the single tuner situation.
   */
  public int record(final Airing watchAir, UIClient uiClient)
  {
    if (Sage.client)
      return NetworkClient.getSN().requestRecord(uiClient, watchAir);
    if (Sage.DBG) System.out.println("Called Seeker.record(" + watchAir + ") hostname=" + uiClient);
    boolean airingDone = (watchAir.getEndTime() <= Sage.time());

    // There needs to be some way to clear something from the denied must sees...and this is it.
    deniedMustSees.remove(watchAir);

    ManualRecord mr = wiz.getManualRecord(watchAir);
    if (mr != null) return VideoFrame.WATCH_OK;

    if (airingDone)
    {
      // Expired and unset, only valid for still existing and complete files
      MediaFile mf = wiz.getFileForAiring(watchAir);
      if (mf != null/* && mf.isCompleteRecording()*/)
      {
        wiz.addManualRecord(watchAir);
        mf.setAcquisitionTech(MediaFile.ACQUISITION_MANUAL);
        return VideoFrame.WATCH_OK;
      }
      return VideoFrame.WATCH_FAILED_AIRING_EXPIRED;
    }
    else
    {
      int rv = addManualRecord(watchAir, uiClient);
      if (rv == 0)
        sched.kick(false);
      return rv;
    }
  }

  public void cancelRecord(final Airing watchAir, UIClient uiClient)
  {
    if (watchAir == null) return;
    if (Sage.client)
    {
      NetworkClient.getSN().requestCancelRecord(uiClient, watchAir);
      return;
    }
    if (Sage.DBG) System.out.println("Called Seeker.cancelRecord(" + watchAir + ") hostname=" + uiClient);
    ManualRecord mr = wiz.getManualRecord(watchAir);
    if (mr != null)
    {
      // Figure this out now since it'll change if we cancel the MR and there was pading
      boolean stillAiring = watchAir.getSchedulingEnd() > Sage.time();
      if (mr.recur != 0)
      {
        // Remove all recurring instances of this
        wiz.removeManualRecord(mr);
        ManualRecord[] allMRs = wiz.getManualRecords();
        for (int i = 0; i < allMRs.length; i++)
        {
          if (allMRs[i].stationID == mr.stationID && allMRs[i].duration == mr.duration &&
              allMRs[i].recur == mr.recur && allMRs[i].isSameRecurrence(mr.startTime))
          {
            if (wiz.getFileForAiring(allMRs[i].getContentAiring()) != null)
              allMRs[i].clearRecurrence();
            else
              wiz.removeManualRecord(allMRs[i]);
          }
        }
      }
      else
        wiz.removeManualRecord(mr);
      // Change on 11/6/03 - it used to be <=, but that seems wrong
      if (stillAiring)
      {
        PluginEventManager.postEvent(PluginEventManager.MANUAL_RECORD_REMOVED,
            new Object[] { PluginEventManager.VAR_AIRING, mr.getSchedulingAiring() });
        sched.kick(false);
      }
    }
    else
    {
      deniedMustSees.add(watchAir);
      sched.kick(false);
    }
  }

  public int modifyRecord(long startTimeModify, long endTimeModify, ManualRecord orgMR, UIClient uiClient)
  {
    if (Sage.client)
      return NetworkClient.getSN().requestModifyRecord(uiClient, startTimeModify, endTimeModify, orgMR);
    if (Sage.DBG) System.out.println("Called Seeker.modifyRecord(startTimeMod=" + Sage.durFormat(startTimeModify) +
        " endTimeMod=" + Sage.durFormat(endTimeModify) + " orgMR=" + orgMR + " hostname=" + uiClient + ']');
    // I used to constrain the parameters with respect to the current time, but that's not right
    long startTime = orgMR.getStartTime() + startTimeModify;
    long endTime = orgMR.getEndTime() + endTimeModify;

    if (orgMR.recur != 0)
    {
      // Can't modify recurring recordings
      return VideoFrame.WATCH_FAILED_GENERAL_SEEKER;
    }

    Vector<Airing> lastParallel = null;
    Vector<Airing> parallelRecords = new Vector<Airing>();
    do
    {
      parallelRecords.clear();
      ManualRecord[] manualMustSee = wiz.getManualRecords();
      long maxOverlapTime = 0;
      Vector<ManualRecord> parallelRecurs = new Vector<ManualRecord>();
      for (int i = 0; i < manualMustSee.length; i++)
      {
        ManualRecord currRec = manualMustSee[i];
        if (currRec == orgMR) continue;
        if (currRec.getEndTime() <= Sage.time()) continue;
        if (currRec.doRecurrencesOverlap(startTime, endTime - startTime, orgMR.recur))
        {
          parallelRecords.addElement(currRec.getSchedulingAiring());
          if (currRec.recur != 0)
            parallelRecurs.add(currRec);
          else
            parallelRecurs.add(null);
        }
      }

      if (parallelRecords.isEmpty()) break;

      Airing fakeAir = new Airing(0);
      boolean[] illegalSchedule = new boolean[1];
      illegalSchedule[0] = false;
      fakeAir.time = startTime;
      fakeAir.duration = endTime - startTime;
      fakeAir.stationID = orgMR.stationID;
      parallelRecords.addElement(fakeAir);
      parallelRecurs.add(null);
      illegalSchedule[0] = !sched.testMultiTunerSchedulingPermutation(parallelRecords);

      // Remove any recurrence duplicates from the parallel list that is presented to the user
      for (int i = 0; i < parallelRecurs.size(); i++)
      {
        ManualRecord currRecur = parallelRecurs.get(i);
        if (currRecur == null) continue;
        for (int j = 0; j < parallelRecords.size(); j++)
        {
          if (i == j || parallelRecurs.get(j) == null) continue;

          ManualRecord otherRecur = parallelRecurs.get(j);
          if (currRecur.stationID == otherRecur.stationID && currRecur.duration == otherRecur.duration &&
              currRecur.recur == otherRecur.recur && currRecur.isSameRecurrence(otherRecur.startTime))
          {
            parallelRecurs.remove(j);
            parallelRecords.remove(j);
            j--;
          }
        }
      }

      if (!illegalSchedule[0])
        break;
      else
      {
        // Conflict exists, we need to kill a recording that's on an encoder that's capable
        // of recording this
        // Conflict resolution, ask about what you're going to kill
        parallelRecords.remove(fakeAir);
        if (lastParallel != null && parallelRecords.equals(lastParallel))
          return VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT;
        Object hookRes = (uiClient == null) ? null : uiClient.processUIClientHook("RecordRequestScheduleConflict", new Object[] { orgMR.getSchedulingAiring(),
            parallelRecords });
        if (!(hookRes instanceof Boolean) || !(((Boolean) hookRes)))
          return VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT;
        lastParallel = new Vector<Airing>(parallelRecords);
      }
    } while (true);
    wiz.modifyManualRecord(startTimeModify, endTimeModify, orgMR);
    PluginEventManager.postEvent(PluginEventManager.MANUAL_RECORD_MODIFIED,
        new Object[] { PluginEventManager.VAR_AIRING, orgMR.getSchedulingAiring() });
    sched.kick(false);
    return VideoFrame.WATCH_OK;
  }

  private int addManualRecord(Airing recAir, UIClient uiClient)
  {
    // Check to make sure we have an encoder that can receive this station
    Set<EncoderState> tryUs = new HashSet<EncoderState>(encoderStateMap.values());
    Iterator<EncoderState> walker = tryUs.iterator();
    // We only need to worry about conflicts with other recordings that occur within the same set of stations. If
    // encoder A has no intersection with the stations on encoder B; then there's no reason to prompt about conflicts from
    // that tuner since it won't help resolve scheduling issues. So this set will be all the stations that either directly or
    // indirectly could resolve a conflict with the new recording.
    // Due to the indirect nature of this; we have to keep checking through the encoders until this set stops growing in size
    Set<Integer> unifiedStationSet = new HashSet<Integer>();
    boolean encoderExists = false;
    while (walker.hasNext())
    {
      EncoderState es = walker.next();
      synchronized (es.stationSet) {
        if (es.stationSet.contains(recAir.stationID))
        {
          encoderExists = true;
          unifiedStationSet.addAll(es.stationSet);
          walker.remove(); // to avoid redundant checking below
          break;
        }
      }
    }
    if (!encoderExists)
      return VideoFrame.WATCH_FAILED_NO_ENCODERS_HAVE_STATION;

    int lastSetSize;
    do
    {
      lastSetSize = unifiedStationSet.size();
      walker = tryUs.iterator();
      while (walker.hasNext())
      {
        EncoderState es = walker.next();
        synchronized (es.stationSet) {
          if (unifiedStationSet.removeAll(es.stationSet))
          {
            // There was an intersection, so use all of these stations, then ignore this one for later
            unifiedStationSet.addAll(es.stationSet);
            walker.remove();
          }
        }
      }

    } while (lastSetSize != unifiedStationSet.size() && !tryUs.isEmpty());

    long defaultStartPadding = Sage.getLong("default_mr_start_padding", 0);
    long defaultStopPadding = Sage.getLong("default_mr_stop_padding", 0);
    long requestedStart = recAir.getStartTime() - defaultStartPadding;
    long requestedStop = recAir.getEndTime() + defaultStopPadding;
    long requestedDuration = requestedStop - requestedStart;

    Airing schedAir = recAir;
    if (defaultStartPadding != 0 || defaultStopPadding != 0)
    {
      schedAir = new Airing(0);
      schedAir.time = requestedStart;
      schedAir.duration = requestedDuration;
      schedAir.stationID = recAir.stationID;
      schedAir.showID = recAir.showID;
    }
    Vector<Airing> parallelRecords = new Vector<Airing>();
    Vector<Airing> lastParallel = null;
    do
    {
      parallelRecords.clear();
      ManualRecord[] manualMustSee = wiz.getManualRecordsSortedByTime();
      Vector<ManualRecord> parallelRecurs = new Vector<ManualRecord>();
      for (int i = 0; i < manualMustSee.length; i++)
      {
        ManualRecord currRec = manualMustSee[i];
        if (currRec.getContentAiring() == recAir)
          return VideoFrame.WATCH_OK;
        if (currRec.getEndTime() <= Sage.time()) continue;
        if (currRec.doRecurrencesOverlap(requestedStart, requestedDuration, 0))
        {
          parallelRecords.addElement(manualMustSee[i].getSchedulingAiring());
          if (currRec.recur != 0)
            parallelRecurs.add(currRec);
          else
            parallelRecurs.add(null);
        }
      }

      if (parallelRecords.isEmpty()) break;

      parallelRecords.addElement(schedAir);
      parallelRecurs.add(null);
      if (sched.testMultiTunerSchedulingPermutation(parallelRecords))
        break;
      // Remove any recurrence duplicates from the parallel list that is presented to the user
      for (int i = 0; i < parallelRecurs.size(); i++)
      {
        ManualRecord currRecur = parallelRecurs.get(i);
        if (currRecur == null) continue;
        for (int j = 0; j < parallelRecords.size(); j++)
        {
          if (i == j || parallelRecurs.get(j) == null) continue;

          ManualRecord otherRecur = parallelRecurs.get(j);
          if (currRecur.stationID == otherRecur.stationID && currRecur.duration == otherRecur.duration &&
              currRecur.recur == otherRecur.recur && currRecur.isSameRecurrence(otherRecur.startTime))
          {
            parallelRecurs.remove(j);
            parallelRecords.remove(j);
            j--;
          }
        }
      }

      // Conflict exists, we need to kill a recording that's on an encoder that's capable
      // of recording this
      // Conflict resolution, ask about what you're going to kill
      parallelRecords.remove(schedAir);

      // Remove any items from the conflict options that would not end up in station set overlap either directly or indirectly
      for (int i = 0; i < parallelRecords.size(); i++)
        if (!unifiedStationSet.contains(parallelRecords.get(i).stationID))
          parallelRecords.remove(i--);

      // If we have the same conflicts as when we just checked, then bail. Most likely they
      // aren't processing the Hook correctly and we'll be in an infinite loop.
      if (lastParallel != null && parallelRecords.equals(lastParallel))
        return VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT;
      Object hookRes = (uiClient == null) ? null : uiClient.processUIClientHook("RecordRequestScheduleConflict", new Object[] { recAir, parallelRecords });
      if (!(hookRes instanceof Boolean) || !((Boolean) hookRes))
        return VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT;
      lastParallel = new Vector<Airing>(parallelRecords);
    } while (true);

    ManualRecord newMR;
    if (schedAir.getStartTime() < Sage.time())
    {
      int[] errorReturn = new int[1];
      EncoderState es = findBestEncoderForNow(schedAir, true, uiClient, errorReturn);
      if (es == null)
      {
        if (errorReturn[0] == 0)
          errorReturn[0] = VideoFrame.WATCH_FAILED_GENERAL_CANT_FIND_ENCODER;
        return errorReturn[0];
      }
      synchronized (this)
      {
        es = checkForFoundBestEncoderNowRecordSwitch(es, recAir);
        // Set the acquisition state to manual if it has already started recording
        MediaFile mf = wiz.getFileForAiring(recAir);
        if (mf != null)
          mf.setAcquisitionTech(MediaFile.ACQUISITION_MANUAL);
        newMR = wiz.addManualRecord(requestedStart, requestedDuration, 0, recAir.stationID,
            "", "", recAir.id, 0);
        es.forceWatch = newMR.getSchedulingAiring();
        es.forceProcessed = false;
        work();
      }
    }
    else
      newMR = wiz.addManualRecord(requestedStart, requestedDuration, 0, recAir.stationID,
          "", "", recAir.id, 0);
    PluginEventManager.postEvent(PluginEventManager.MANUAL_RECORD_ADDED,
        new Object[] { PluginEventManager.VAR_AIRING, newMR.getSchedulingAiring() });
    return VideoFrame.WATCH_OK;
  }

  private EncoderState checkForFoundBestEncoderNowRecordSwitch(EncoderState es, Airing recAir)
  {
    // We grabbed the encoder outside of the Seeker lock, so we need to verify that
    // another encoder is not recording the exact same program now...it is OK if its recording
    // the same stationID though due to possible MR overlap. If it is, then we just switch ourselves
    // over to that encoder. This won't address all sync cases here...but it'll fix the one I saw actually
    // happen on a customer system.
    for(EncoderState searcher : encoderStateMap.values()) {
      if (searcher == es) continue;
      if (searcher.currRecord != null && searcher.currRecord.stationID == recAir.stationID)
      {
        Airing compAir = searcher.currRecord;
        if (searcher.currRecord instanceof ManualRecord.FakeAiring)
          compAir = ((ManualRecord.FakeAiring) searcher.currRecord).getManualRecord().getContentAiring();
        if (compAir == recAir)
        {
          if (Sage.DBG) System.out.println("Switching encoder for add/modifyManualRecord from " + es + " to " + searcher +
              " for " + recAir + " because the other encoder is already recording that program");
          return searcher;
        }
      }
    }
    return es;
  }
  // This is for NEW time-based recordings. Modifications should call the modify function instead.
  public int timedRecord(long startTime, long stopTime, int stationID, int recurrence, Airing recAir, UIClient uiClient)
  {
    if (Sage.client)
      return NetworkClient.getSN().requestTimedRecord(uiClient, startTime, stopTime, stationID, recurrence, recAir);
    if (Sage.DBG) System.out.println("Called Seeker.timedRecord(startTime=" + Sage.df(startTime) +
        " stopTime=" + Sage.df(stopTime) + " stationID=" + stationID + " recur=" + recurrence + " recAir=" + recAir + " hostname=" + uiClient);
    Vector<Airing> parallelRecords = new Vector<Airing>();
    Vector<Airing> lastParallel = null;
    Airing fakeAir = new Airing(0);
    boolean[] illegalSchedule = new boolean[1];
    fakeAir.time = startTime;
    fakeAir.duration = stopTime - startTime;
    fakeAir.stationID = stationID;
    fakeAir.showID = (recAir == null) ? 0 : recAir.showID;
    do
    {
      parallelRecords.clear();
      ManualRecord[] manualMustSee = wiz.getManualRecords();
      Vector<ManualRecord> parallelRecurs = new Vector<ManualRecord>();
      for (int i = 0; i < manualMustSee.length; i++)
      {
        ManualRecord currRec = manualMustSee[i];
        if (currRec.getContentAiring() == recAir)
          return VideoFrame.WATCH_FAILED_GENERAL_SEEKER; // this shouldn't happen, this function shouldn't be called in this state
        if (currRec.getEndTime() <= Sage.time()) continue;
        if (currRec.doRecurrencesOverlap(startTime, stopTime - startTime, recurrence))
        {
          parallelRecords.addElement(manualMustSee[i].getSchedulingAiring());
          if (currRec.recur != 0)
            parallelRecurs.add(currRec);
          else
            parallelRecurs.add(null);
        }
      }

      if (parallelRecords.isEmpty()) break;

      parallelRecords.addElement(fakeAir);
      parallelRecurs.add(null);
      int extraRecurs = 0;
      if (recurrence != 0)
      {
        long[] allRecurs = ManualRecord.getRecurringStartTimes(startTime, stopTime - startTime, recurrence, Sage.MILLIS_PER_WEEK);
        for (int i = 0; i < allRecurs.length; i++)
        {
          if (allRecurs[i] != startTime)
          {
            Airing recurAir = new Airing(0);
            recurAir.time = allRecurs[i];
            recurAir.duration = fakeAir.duration;
            recurAir.stationID = stationID;
            parallelRecords.add(recurAir);
            extraRecurs++;
          }
        }
      }
      illegalSchedule[0] = false;
      illegalSchedule[0] = !sched.testMultiTunerSchedulingPermutation(parallelRecords);
      while (extraRecurs > 0)
      {
        extraRecurs--;
        parallelRecords.remove(parallelRecords.size() - 1);
      }
      // Remove any recurrence duplicates from the parallel list that is presented to the user
      for (int i = 0; i < parallelRecurs.size(); i++)
      {
        ManualRecord currRecur = parallelRecurs.get(i);
        if (currRecur == null) continue;
        for (int j = 0; j < parallelRecords.size(); j++)
        {
          if (i == j || parallelRecurs.get(j) == null) continue;

          ManualRecord otherRecur = parallelRecurs.get(j);
          if (currRecur.stationID == otherRecur.stationID && currRecur.duration == otherRecur.duration &&
              currRecur.recur == otherRecur.recur && currRecur.isSameRecurrence(otherRecur.startTime))
          {
            parallelRecurs.remove(j);
            parallelRecords.remove(j);
            j--;
          }
        }
      }

      if (!illegalSchedule[0])
        break;
      else
      {
        // Conflict exists, we need to kill a recording that's on an encoder that's capable
        // of recording this
        // Conflict resolution, ask about what you're going to kill
        parallelRecords.remove(fakeAir);
        if (lastParallel != null && parallelRecords.equals(lastParallel))
          return VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT;
        Object hookRes = (uiClient == null) ? null : uiClient.processUIClientHook("RecordRequestScheduleConflict", new Object[] {
            (recAir == null) ? fakeAir : recAir, parallelRecords });
        if (!(hookRes instanceof Boolean) || !((Boolean) hookRes))
          return VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT;
        lastParallel = new Vector<Airing>(parallelRecords);
      }
    } while (true);

    if (startTime < Sage.time())
    {
      int[] errorReturn = new int[1];
      EncoderState es = findBestEncoderForNow(fakeAir, true, uiClient, errorReturn);
      if (es == null)
      {
        if (errorReturn[0] == 0)
          errorReturn[0] = VideoFrame.WATCH_FAILED_GENERAL_CANT_FIND_ENCODER;
        return errorReturn[0];
      }
      synchronized (this)
      {
        if (recAir != null)
          es = checkForFoundBestEncoderNowRecordSwitch(es, recAir);
        ManualRecord newMR = wiz.addManualRecord(startTime, stopTime - startTime, 0, stationID, "", "",
            (recAir == null) ? 0 : recAir.id, recurrence);
        es.forceWatch = newMR.getSchedulingAiring();
        es.forceProcessed = false;
        work();
      }
    }
    else
      wiz.addManualRecord(startTime, stopTime - startTime, 0, stationID, "", "", (recAir == null) ? 0 : recAir.id,
          recurrence);
    sched.kick(false);
    return VideoFrame.WATCH_OK;
  }

  private synchronized int countScheduleRedundancy(Airing testMe, MediaFile[] mfsCache)
  {
    /*
     * We want to distribute this count over each of the
     * redundant airings. The count increases going backwards
     * in time from the present and then forward from the
     * present.
     */
    long currTime = Sage.time();
    boolean futuristic = testMe.getStartTime() > currTime;
    int count = 0;
    boolean isFile = false;
    Agent testMeAgent = god.getCauseAgent(testMe);
    for (int i = 0; i < mfsCache.length; i++)
    {
      if (mfsCache[i] == null) continue;
      //if (!((MediaFile)mfs[i]).isCompleteRecording()) continue;
      Airing fileAir = mfsCache[i].getContentAiring();
      if (fileAir == null) continue;
      if (fileAir == testMe) isFile = true;
      if (fileAir != testMe &&
          (futuristic || fileAir.time > testMe.time) &&
          god.getCauseAgent(fileAir) == testMeAgent &&
          !BigBrother.isWatched(fileAir))
        count++;
    }
    // Files don't get affected by the schedule, only other files
    if (isFile) return count;
    if (Sage.client)
    {
      // NOTE THIS ISN'T DONE YET, calculate schedule redundancy off GUI schedule data
    }
    else
    {
      for (EncoderState es : encoderStateMap.values())
      {
        if (es.currRecord != null && es.currRecord != testMe &&
            god.getCauseAgent(es.currRecord) == testMeAgent)
          count++;
        int i = 0;
        if (!es.schedule.isEmpty() && es.schedule.firstElement() == es.currRecord) i++;
        for (; i < es.scheduleContent.size(); i++)
        {
          Airing schedAir = es.scheduleContent.elementAt(i);
          if (schedAir == testMe || schedAir.time > testMe.time) break;
          if (god.getCauseAgent(schedAir) == testMeAgent)
            count++;
        }
      }
    }
    return count;
  }

  // This takes the 0-based numRedos and returns the scalar to multiply
  // the watch by.
  private float redScaler(float baseWatch, Airing theAir, MediaFile[] mfsCache)
  {
    int numRedos = countScheduleRedundancy(theAir, mfsCache);
    if (EXPONENTIAL_RED)
      return (numRedos == 0) ? baseWatch : (float)Math.pow(baseWatch, numRedos + 1);
    else
      return (numRedos <= 1) ? 1 : 1/((numRedos - 1)*2.0f);
  }

  // NARFLEX - 1/27/10 - I removed the synchronization from this method since it doesn't deal with any data
  // structures in the Seeker at all
  private float calcW(Airing air)
  {
    MediaFile[] mfsCache = wiz.getFiles();
    for (int i = 0; i < mfsCache.length; i++)
      if (!mfsCache[i].isCompleteRecording() || mfsCache[i].archive || !mfsCache[i].isTV())
        mfsCache[i] = null;
    return calcW(air, mfsCache);
  }

  // NARFLEX - 1/27/10 - I removed the synchronization from this method since it doesn't deal with any data
  // structures in the Seeker at all
  private float calcW(Airing air, MediaFile[] mfsCache)
  {
    float f = god.getWP(air);
    // NOTE: We do this extra check in here to account for the latency of the Profiler. What can happen is
    // a user can watch a Favorite and then the file is over and it gets marked watched. Then the profiler runs
    // and updates the WP Map so it has a 0.0 WP.  Then the user unmarks the watch for that airing. At that point
    // the airing is no longer 'safe' even though it was just viewed. With the WP at zero it'll fall to the bottom
    // of the list and can get deleted if the cleanup runs before the Profiler updates the WP again. By checking
    // for this case here we can avoid any catastrophic deletions that would occur due to this latency.
    // Narflex 12/18/06
    if (f == 0 && god.isLoveAir(air) && wiz.getWastedForAiring(air) == null && wiz.getWatch(air) == null)
      return 1.0f;
    if (Profiler.isMustSee(air) || mfsCache == null)
      return f;

    f = redScaler(f, air, mfsCache);
    return f;
  }

  private Airing[] convertToAiringObjects(Vector<Airing> airVec)
  {
    Airing[] rv = airVec.toArray(Pooler.EMPTY_AIRING_ARRAY);
    for (int i = 0; i < rv.length; i++)
    {
      if (rv[i] instanceof ManualRecord.FakeAiring)
      {
        rv[i] = ((ManualRecord.FakeAiring) rv[i]).getManualRecord().getContentAiring();
      }
    }
    return rv;
  }

  // Gets all of the intelligent recordings that would be scheduled if IR was enabled
  public synchronized Airing[] getIRScheduledAirings()
  {
    Set<Airing> rv = new HashSet<Airing>();
    for (EncoderState es : encoderStateMap.values())
    {
      rv.addAll(sched.getSchedule(es.capDev));
      rv.removeAll(sched.getMustSee(es.capDev));
    }
    Airing[] airRv = rv.toArray(Pooler.EMPTY_AIRING_ARRAY);
    Arrays.sort(airRv, AirStartTimeSorter);
    return airRv;
  }

  // return is a Object[] of Object[2]{String, Airing[]}
  public synchronized Object[] getScheduledAirings()
  {
    if (Sage.client)
    {
      return NetworkClient.getSN().getScheduledAirings();
    }
    else
    {
      Vector<Object[]> rv = new Vector<Object[]>();
      for (EncoderState es : encoderStateMap.values())
      {
        if ((es.currRecord != null) &&
            (es.schedule.isEmpty() || es.currRecord != es.schedule.firstElement()))
        {
          Vector<Airing> currSched = new Vector<Airing>();
          currSched.add(es.currRecord);
          for (int i = 0; i < es.schedule.size(); i++)
          {
            Airing theAir = es.schedule.elementAt(i);
            if (!theAir.doesSchedulingOverlap(es.currRecord) || es.mustSee.contains(theAir))
              currSched.add(theAir);
          }

          rv.add(new Object[] { es.capDev.getName(), convertToAiringObjects(currSched) });
        }
        else
          rv.add(new Object[] { es.capDev.getName(), convertToAiringObjects(es.schedule) });
      }
      return rv.toArray();
    }
  }

  public Airing[] getInterleavedScheduledAirings()
  {
    Object[] multiSched = getScheduledAirings();
    List<Airing> schedAirs = new ArrayList<Airing>();
    for (int i = 0; i < multiSched.length; i++)
      schedAirs.addAll(Arrays.asList((Airing[])((Object[])multiSched[i])[1]));
    Airing[] theAirs = schedAirs.toArray(Pooler.EMPTY_AIRING_ARRAY);
    Arrays.sort(theAirs, AirStartTimeSorter);
    return theAirs;
  }

  public Airing[] getScheduledAiringsForSource(String sourceName)
  {
    Object[] multiSched = getScheduledAirings();
    for (int i = 0; i < multiSched.length; i++)
      if (((Object[]) multiSched[i])[0].toString().equals(sourceName))
        return (Airing[]) ((Object[]) multiSched[i])[1];
    return Pooler.EMPTY_AIRING_ARRAY;
  }

  public Airing[] getInterleavedScheduledAirings(long startTime, long stopTime)
  {
    Object[] multiSched = getScheduledAirings();
    List<Airing> schedAirs = new ArrayList<Airing>();
    for (int i = 0; i < multiSched.length; i++)
    {
      Airing[] fooAirs = (Airing[]) ((Object[])multiSched[i])[1];
      for (int j = 0; j < fooAirs.length; j++)
      {
        ManualRecord mr = wiz.getManualRecord(fooAirs[j]);
        if ((mr != null && mr.doesOverlap(startTime, stopTime)) ||
            (mr == null && fooAirs[j].doesSchedulingOverlap(startTime, stopTime)))
          schedAirs.add(fooAirs[j]);
      }
    }
    Airing[] theAirs = schedAirs.toArray(Pooler.EMPTY_AIRING_ARRAY);
    Arrays.sort(theAirs, AirStartTimeSorter);
    return theAirs;
  }

  public Airing[] getScheduledAiringsForSource(String sourceName, long startTime, long stopTime)
  {
    Object[] multiSched = getScheduledAirings();
    List<Airing> schedAirs = new ArrayList<Airing>();
    for (int i = 0; i < multiSched.length; i++)
      if (((Object[]) multiSched[i])[0].toString().equals(sourceName))
      {
        Airing[] fooAirs = (Airing[]) ((Object[])multiSched[i])[1];
        for (int j = 0; j < fooAirs.length; j++)
        {
          ManualRecord mr = wiz.getManualRecord(fooAirs[j]);
          if ((mr != null && mr.doesOverlap(startTime, stopTime)) ||
              (mr == null && fooAirs[j].doesSchedulingOverlap(startTime, stopTime)))
            schedAirs.add(fooAirs[j]);
        }
        return schedAirs.toArray(Pooler.EMPTY_AIRING_ARRAY);
      }
    return Pooler.EMPTY_AIRING_ARRAY;
  }

  public Airing[] getRecentWatches(long time)
  {
    Watched[] wizWatches = wiz.getWatches(time);
    // Reverse the order of this array..
    Collections.reverse(Arrays.asList(wizWatches));

    Set<MediaFile> vfFiles;
    synchronized (clientWatchFileMap) {
      vfFiles = new HashSet<MediaFile>(clientWatchFileMap.keySet());
    }
    Airing[] rv;
    int x = 0;
    if (!vfFiles.isEmpty())
    {
      rv = new Airing[wizWatches.length + vfFiles.size()];
      Iterator<MediaFile> walker = vfFiles.iterator();
      while (walker.hasNext())
        rv[x++] = walker.next().getContentAiring();
    }
    else
      rv = new Airing[wizWatches.length];

    for (int i = 0; i < wizWatches.length; i++)
      rv[i + x] = wizWatches[i].getAiring();

    // Deal with redundancy of Airings in multiple watches & nulls
    HashSet<Airing> hash = new HashSet<Airing>(Arrays.asList(rv));
    hash.remove(null);
    if (hash.size() != rv.length)
    {
      Airing[] rv2 = new Airing[hash.size()];
      for (int i = 0, j = 0; i < rv.length; i++)
      {
        if (hash.remove(rv[i]))
          rv2[j++] = rv[i];
      }
      return rv2;
    }
    return rv;
  }

  public String getDefaultQuality() { return defaultQuality; }
  public void setDefaultQuality(String newQuality)
  {
    Sage.put(prefs + DEFAULT_RECORDING_QUALITY, newQuality);
    defaultQuality = newQuality;
  }

  public boolean getDisableProfilerRecording() { return disableProfilerRecs; }
  public void setDisableProfilerRecording(boolean x)
  {
    if (x != disableProfilerRecs)
    {
      Sage.putBoolean(prefs + DISABLE_PROFILER_RECORDING, disableProfilerRecs = x);
      synchronized (this)
      {
        notifyAll();
      }
    }
  }

  public void addIgnoreFile(File ignoreMe) { ignoreFiles.add(ignoreMe.getAbsolutePath()); }
  public void removeIgnoreFile(File ignoreMe) { ignoreFiles.remove(ignoreMe.getAbsolutePath()); }

  private void writeStoragePrefs()
  {
    writeMountProps();
    StringBuffer sb = new StringBuffer();
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.elementAt(i);
        sb.append(vs.prefString());
        sb.append(';');
      }
    }
    Sage.put(prefs + VIDEO_STORAGE, sb.toString());
  }

  private File[] listAllFilesInVideoStorageDirs()
  {
    List<File> rv = new ArrayList<File>();
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.elementAt(i);
        File[] newList = vs.videoDir.listFiles();
        if (newList != null)
          rv.addAll(Arrays.asList(newList));
      }
    }
    return rv.toArray(new File[0]);
  }

  private String[] listAllFilesInVideoStorageDirsAsStrings()
  {
    List<String> rv = new ArrayList<String>();
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.elementAt(i);
        String[] newList = vs.videoDir.list();
        if (newList != null)
          rv.addAll(Arrays.asList(newList));
      }
    }
    return rv.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public VideoStorage[] getVideoStores()
  {
    VideoStorage[] rv = videoStore.toArray(new VideoStorage[0]);
    for (int i = 0; i < rv.length; i++)
      rv[i] = (VideoStorage) rv[i].clone();
    return rv;
  }

  public File[] getVideoStoreDirectories()
  {
    synchronized (videoStore)
    {
      File[] rv = new File[videoStore.size()];
      for (int i = 0; i < rv.length; i++)
        rv[i] = videoStore.get(i).videoDir;
      return rv;
    }
  }

  public String getRuleForDirectory(File testDir)
  {
    testDir = testDir.getAbsoluteFile();
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.get(i);
        if (testDir.equals(vs.videoDir))
        {
          return vs.getRuleName();
        }
      }
    }
    return "";
  }

  public long getRuleSizeForDirectory(File testDir)
  {
    testDir = testDir.getAbsoluteFile();
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.get(i);
        if (testDir.equals(vs.videoDir))
        {
          return vs.videoDiskspace;
        }
      }
    }
    return 0;
  }

  public void removeVideoDirectory(File testDir)
  {
    testDir = testDir.getAbsoluteFile();
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.get(i);
        if (testDir.equals(vs.videoDir))
        {
          videoStore.removeElementAt(i);
          writeStoragePrefs();
          return;
        }
      }
    }
  }

  public void changeVideoDirectory(File oldDir, File testDir, String rule, long size)
  {
    oldDir = oldDir.getAbsoluteFile();
    testDir = testDir.getAbsoluteFile();
    synchronized (videoStore)
    {
      if (oldDir != null && !oldDir.equals(testDir))
        removeVideoDirectory(oldDir);
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.get(i);
        if (testDir.equals(vs.videoDir))
        {
          vs.videoDiskspaceRule = getRuleForName(rule);
          vs.videoDiskspace = size;
          writeStoragePrefs();
          return;
        }
      }
      videoStore.add(new VideoStorage(testDir.toString(), size, getRuleForName(rule)));
      writeStoragePrefs();
    }
  }

  public void addVideoDirectory(String testDir, String rule, long size)
  {
    File f;
    if (Sage.LINUX_OS || Sage.MAC_OS_X)
    {
      if (testDir.startsWith("smb://"))
      {
        String smbPrefix = Sage.get("linux/smb_mount_root", "/tmp/sagetv_shares/");
        testDir = testDir.substring("smb://".length());
        // Now modify the paths so it's only the share, not the subdirectory too
        int s1 = testDir.indexOf('/');
        int s2 = testDir.indexOf('/', s1 + 1);
        String smbPath = "//" + testDir.substring(0, s2).toLowerCase();
        String localPath = smbPrefix + testDir.substring(0, s2).toLowerCase();
        f = new File(localPath + testDir.substring(s2));
        smbMountMap.put(smbPath, localPath);
        establishMountPoints();
      }
      else if (testDir.startsWith("nfs://"))
      {
        String nfsPrefix = Sage.get("linux/nfs_mount_root", "/tmp/sagetv_shares/");
        testDir = testDir.substring("nfs://".length());
        int sidx = testDir.indexOf('/');
        String localPath = nfsPrefix + testDir;
        String nfsPath = testDir.substring(0, sidx) + ":" + testDir.substring(sidx);
        nfsMountMap.put(nfsPath, localPath);
        f = new File(localPath);
        establishMountPoints();
      }
      else
        f = new File(testDir);
    }
    else
    {
      if (testDir.startsWith("smb://"))
        testDir = IOUtils.convertSMBURLToUNCPath(testDir);
      f = new File(testDir);
    }
    f = f.getAbsoluteFile();
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.get(i);
        if (f.equals(vs.videoDir))
        {
          vs.videoDiskspaceRule = getRuleForName(rule);
          vs.videoDiskspace = size;
          writeStoragePrefs();
          return;
        }
      }
      videoStore.add(new VideoStorage(f.toString(), size, getRuleForName(rule)));
      writeStoragePrefs();
    }
  }

  public static int getRuleForName(String s)
  {
    if (Sage.rez("Diskspace_Use_Only").equalsIgnoreCase(s))
      return VIDEO_DISKSPACE_USE_ONLY;
    else if (Sage.rez("Diskspace_Use_All").equalsIgnoreCase(s))
      return VIDEO_DISKSPACE_USE_ALL;
    else if (Sage.rez("Diskspace_Leave_Free").equalsIgnoreCase(s))
      return VIDEO_DISKSPACE_LEAVE_FREE;
    else
      throw new IllegalArgumentException("Invalid name \"" + s + "\" for diskspace rule");
  }

  // Preserve the file streaming data for each store when changing this
  public void setVideoStores(VideoStorage[] newStores)
  {
    if (newStores.length == 0)
      throw new IllegalArgumentException("Cannot have 0 length video stores");
    synchronized (videoStore)
    {
      Map<File, VideoStorage> oldFileStoreMap = new HashMap<File, VideoStorage>();
      for (int i = 0; i < videoStore.size(); i++)
        oldFileStoreMap.put(videoStore.elementAt(i).videoDir, videoStore.elementAt(i));
      videoStore.removeAllElements();
      for (int i = 0; i < newStores.length; i++)
      {
        VideoStorage oldStore = oldFileStoreMap.get(newStores[i].videoDir);
        if (oldStore != null)
          newStores[i].fileSpaceReserves = oldStore.fileSpaceReserves;
      }
      videoStore.addAll(Arrays.asList(newStores));
      writeStoragePrefs();
    }
  }

  public boolean getUseDTVMajorMinorChans()
  {
    return dtvMajorMinorChans;
  }

  public void setUseDTVMajorMinorChans(boolean x)
  {
    Sage.putBoolean(prefs + USE_DTV_MAJOR_MINOR_CHANNELS, dtvMajorMinorChans = x);
  }

  private void postScheduleChange()
  {
    // This one has special distribution since it's in the network protocol
    Catbert.distributeHookToLocalUIs("RecordingScheduleChanged", null);
    NetworkClient.distributeScheduleChangedAsync();
  }

  public CaptureDeviceInput getInputForCurrRecordingFile(MediaFile mf)
  {
    EncoderState[] es = encoderStateMap.values().toArray(new EncoderState[0]);
    for (int i = 0; i < es.length; i++)
    {
      if (es[i].currRecordFile == mf)
      {
        return es[i].capDev.getActiveInput();
      }
    }
    return null;
  }

  public void requestReboot()
  {
    if (Sage.DBG) System.out.println("Reboot Requested....");
    rebootMe = true;
    kick();
  }

  private void establishMountPoints()
  {
    if (!Sage.LINUX_OS && !Sage.MAC_OS_X) return;
    // Save the mount map too
    String mapStr = "";
    for (Map.Entry<String, String> ent : smbMountMap.entrySet())
    {
      String smbPath = ent.getKey();
      String localPath = ent.getValue();
      // Even if it fails we keep it in our map
      int mountRes = IOUtils.doSMBMount(smbPath, localPath);
      if (mountRes == IOUtils.SMB_MOUNT_FAILED)
        failedMounts.add(new File(localPath).toString());
      else
        failedMounts.remove(new File(localPath).toString());
      mapStr += smbPath + "," + localPath + ";";
    }
    Sage.put("linux/smb_mounts", mapStr);
    // Save the mount map too
    mapStr = "";
    for (Map.Entry<String, String> ent : nfsMountMap.entrySet())
    {
      String nfsPath = ent.getKey();
      String localPath = ent.getValue();
      // Even if it fails we keep it in our map
      int mountRes = IOUtils.doNFSMount(nfsPath, localPath);
      if (mountRes == IOUtils.NFS_MOUNT_FAILED)
        failedMounts.add(new File(localPath).toString());
      else
        failedMounts.remove(new File(localPath).toString());
      mapStr += nfsPath + "," + localPath + ";";
    }
    Sage.put("linux/nfs_mounts", mapStr);
  }

  private void unmountPaths(Collection<String> removeUs)
  {
    if (Sage.LINUX_OS || Sage.MAC_OS_X)
    {
      for (String currPath : removeUs)
      {
        if (IOUtils.undoMount(currPath))
        {
          if (Sage.DBG) System.out.println("Successfully unmounted:" + currPath);
        }
        else
        {
          if (Sage.DBG) System.out.println("FAILED unmounting:" + currPath);
        }
      }
    }
  }

  public boolean isPathInManagedStorage(File f)
  {
    String s = f.getAbsolutePath();
    if (Sage.WINDOWS_OS) s = s.toLowerCase();
    synchronized (videoStore)
    {
      for (int i = 0; i < videoStore.size(); i++)
      {
        VideoStorage vs = videoStore.elementAt(i);
        if (s.startsWith(Sage.WINDOWS_OS ? vs.videoDir.getAbsolutePath().toLowerCase() :
          vs.videoDir.getAbsolutePath()))
          return true;
      }
    }
    return false;
  }

  public Set<String> getFailedNetworkMounts()
  {
    return failedMounts;
  }

  public void requestDeviceReset(CaptureDevice capDev)
  {
    EncoderState encState = encoderStateMap.get(capDev);
    if (encState != null)
    {
      if (Sage.DBG) System.out.println("Seeker is setting the reset flag for " + capDev);
      encState.resetRequested = true;
      kick();
      return;
    }
  }

  private void launchDirMonitor()
  {
    if (Sage.DBG) System.out.println("Starting the library import directory monitor process");
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        // Let it restart if it dies
        while (alive)
        {
          try
          {
            final Process procy = Runtime.getRuntime().exec(
                new String[] { "/app/sage/dir-monitor-sagetv", "/var/media/pictures", "/var/media/music", "/var/media/videos" },
                null, null);
            final long[] lastUpdateTime = new long[1];
            lastUpdateTime[0] = 0;
            Thread the = new Thread("InputStreamConsumer")
            {
              public void run()
              {
                try
                {
                  java.io.InputStream buf = procy.getInputStream();
                  do
                  {
                    int c = buf.read();
                    if (c == -1)
                      break;
                    lastUpdateTime[0] = Sage.eventTime();
                  }while (true);
                  buf.close();
                }
                catch (Exception e){}
              }
            };
            the.setDaemon(true);
            the.start();
            Thread the2 = new Thread("ErrorStreamConsumer")
            {
              public void run()
              {
                try
                {
                  java.io.InputStream buf = procy.getErrorStream();
                  String s;
                  do
                  {
                    int c = buf.read();
                    if (c == -1)
                      break;
                    lastUpdateTime[0] = Sage.eventTime();
                  }while (true);
                  buf.close();
                }
                catch (Exception e){}
              }
            };
            the2.setDaemon(true);
            the2.start();
            while (true)
            {
              // Check to make sure dir-monitor is still running
              try
              {
                // This should throw an IllegalStateException since it should still be running
                procy.exitValue();
                break;
              }
              catch (IllegalThreadStateException itse){}
              if (Sage.eventTime() - lastUpdateTime[0] > 15000 && lastUpdateTime[0] != 0)
              {
                lastUpdateTime[0] = 0;
                if (Sage.DBG) System.out.println("Dir Monitor noticed a change, and then idle for at least 15 seconds, trigger import scan");
                scanLibrary(true);
              }
              try{Thread.sleep(10000);}catch(Exception e){}
            }
            the.join(1000);
            the2.join(1000);
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR executing dir-monitor of:" + e);
            e.printStackTrace();
          }
        }
      }
    }, "DirMonitor", Thread.MIN_PRIORITY);
  }

  private MMC mmc;
  private EPG epg;
  private Wizard wiz;
  private Carny god;
  private Scheduler sched;
  private String prefs;

  private boolean fastMuxSwitch;

  private volatile boolean rebootMe;

  private long chanWaitAskAdv;

  private boolean dtvMajorMinorChans;

  private boolean alive;

  private boolean canRecord;
  private Vector<Airing> deniedMustSees;
  private Vector<Airing> completeChanChangeAsks;
  private boolean disableProfilerRecs;
  private long leadTime;

  private Set<Airing> currRecords;
  private Set<MediaFile> currRecordFiles;

  private Thread seekerThread;

  private String defaultQuality;

  private Vector<VideoStorage> videoStore;
  private File[] archiveDirs;
  private int[] archiveDirMasks;

  private boolean mmcPresent;

  private boolean wasKicked;

  private Set<String> ignoreFiles;

  private Map<CaptureDevice, EncoderState> encoderStateMap;

  private String currQuality;

  private Map<MediaFile,Set<UIClient>> clientWatchFileMap;

  private boolean currentlyImporting = false;
  private boolean disableLibraryScanning;
  private boolean needsAnImport = false;
  private Object importLock = new Object();
  private Set<String> picLibFileExts;
  private Set<String> musicLibFileExts;
  private Set<String> playlistFileExts;
  private Set<String> vidLibFileExts;

  private boolean prepped;

  private boolean doThoroughLibScan = true;

  private Vector<FileExportPlugin> exportPlugins;

  private Map<String, String> smbMountMap;
  private Map<String, String> nfsMountMap;

  private String[] validRootImportPaths;

  private boolean performFullContentReindex;
  private boolean performPicLibReindex;
  private long lastThroughLibScanTime;

  private Set<String> failedMounts; // paths that we expect to be mounted, but failed

  private Set<String> autoImportIgnorePaths;
  private Set<String> autoImportAddedNetPaths;

  private long minPicImportSize;
  private long minJpgImportSize;
  private long minMusicImportSize;

  private long delayForNextLibScan;

  private long lastLibScanTime;
  private long libScanPeriod;

  private long lastExternalProcessCheck;
  private boolean externalProcessesNeedCpu;

  private long lastSeekerWakeupTime;
  private boolean didIndexVerifyAndMetadataCheck;

  private class EncoderState
  {
    // One of these is needed for each Encoder to track what it's up to
    public EncoderState(CaptureDevice inCapDev)
    {
      capDev = inCapDev;
      mustSee = new Vector<Airing>();
      schedule = new Vector<Airing>();
      mustSeeContent = new Vector<Airing>();
      scheduleContent = new Vector<Airing>();
      lastStationID = Sage.getInt(prefs + LAST_STATION_ID + '/' + capDev.getName(), 0);
      // Multiple threads can access these sets; so they need to be synchronized
      stationSet = Collections.synchronizedSet(new HashSet<Integer>());
      controllingClients = Collections.synchronizedSet(new HashSet<UIClient>());
    }
    public String toString()
    {
      return super.toString() + "[" + capDev + "]";
    }
    private long lastCheckedSize;
    private long lastSizeCheckTime;
    private Vector<Airing> schedule;
    private Vector<Airing> mustSee;
    private Vector<Airing> scheduleContent;
    private Vector<Airing> mustSeeContent;
    private Airing currRecord;
    private MediaFile currRecordFile;
    private Airing switcherAir;
    private MediaFile switcherFile;
    private int resetCount;
    private String myQuality;
    private CaptureDevice capDev;
    private int lastStationID;
    private Set<Integer> stationSet;
    private Airing forceWatch;
    private Set<UIClient> controllingClients;
    private boolean forceProcessed;
    private CaptureDeviceInput absoluteClientControl;
    private boolean resetRequested;
    private boolean doingStartup;
  }

  // Sorts in order from least deletable to most deletable, with the newer
  // ones coming before older ones
  public Comparator<DBObject> getMediaFileComparator()
  {
    return new MediaFileComparator(false);
  }

  public Comparator<DBObject> getMediaFileComparator(boolean fast)
  {
    return new MediaFileComparator(fast);
  }

  public void disableLibraryScanning()
  {
    disableLibraryScanning = true;
    kick();
    synchronized (importLock)
    {
      while (currentlyImporting)
      {
        try { importLock.wait(5000);}catch(InterruptedException e){}
      }
    }
  }

  private class MediaFileComparator implements Comparator<DBObject>
  {
    public MediaFileComparator(boolean ignoreRed)
    {
      this.ignoreRed = ignoreRed;
      wMap = new HashMap<Airing, Float>();
    }
    private int longComp(long l1, long l2)
    {
      return (l1 == l2) ? 0 : (l1 < l2 ? -1 : 1);
    }
    public int compare(DBObject o1, DBObject o2)
    {
      if (o1 == o2) return 0;
      else if (o1 == null) return 1;
      else if (o2 == null) return -1;
      Airing a1 = null;
      Airing a2 = null;
      MediaFile m1 = null;
      MediaFile m2 = null;
      if (o1 instanceof MediaFile)
      {
        m1 = (MediaFile) o1;
        a1 = m1.getContentAiring();
      }
      else
      {
        a1 = (Airing) o1;
        m1 = wiz.getFileForAiring(a1);
      }
      if (o2 instanceof MediaFile)
      {
        m2 = (MediaFile) o2;
        a2 = m2.getContentAiring();
      }
      else
      {
        a2 = (Airing) o2;
        m2 = wiz.getFileForAiring(a2);
      }
      // Sort hiearchy is from the most important to least
      // Current Recordings
      // ManualRecord
      // Complete recording
      // Wasted
      // Watched
      // Love
      // ScaledAge-wp
      boolean currRec1 = (m1 != null) && m1.isRecording();
      boolean currRec2 = (m2 != null) && m2.isRecording();
      if (currRec1 && currRec2)
        return longComp(m2 == null ? 0 : m2.getRecordTime(), m1 == null ? 0 : m1.getRecordTime());
      else if (currRec1) return -1;
      else if (currRec2) return 1;
      boolean man1 = wiz.getManualRecord(a1) != null || a1 == null;
      boolean man2 = wiz.getManualRecord(a2) != null || a2 == null;
      // Switched the order of manual records so newer are on top 4/2/03
      if (man1 && man2)
        return longComp(m2 == null ? 0 : m2.getRecordTime(), m1 == null ? 0 : m1.getRecordTime());
      else if (man1) return -1;
      else if (man2) return 1;
      boolean partial1 = (m1 == null ? false : !m1.isCompleteRecording());
      boolean partial2 = (m2 == null ? false : !m2.isCompleteRecording());
      if (partial1 && partial2)
        return longComp(m2 == null ? 0 : m2.getRecordTime(), m1 == null ? 0 : m1.getRecordTime());
      else if (partial1) return 1;
      else if (partial2) return -1;
      boolean waste1 = wiz.getWastedForAiring(a1) != null;
      boolean waste2 = wiz.getWastedForAiring(a2) != null;
      if (waste1 && waste2)
        return longComp(m2 == null ? 0 : m2.getRecordTime(), m1 == null ? 0 : m1.getRecordTime());
      else if (waste1) return 1;
      else if (waste2) return -1;
      // The BEST are at the front of the list.
      if (mfsCache == null && !ignoreRed)
      {
        mfsCache = wiz.getFiles();
        for (int i = 0; i < mfsCache.length; i++)
          if (!mfsCache[i].isCompleteRecording() || mfsCache[i].archive || !mfsCache[i].isTV())
            mfsCache[i] = null;
      }
      float w1;
      if (a1.isWatched())
        w1 = 0;
      else
      {
        Float fw = wMap.get(a1);
        if (fw != null)
          w1 = fw;
        else
        {
          w1 = calcW(a1, ignoreRed ? null : mfsCache);
          wMap.put(a1, w1);
        }
      }
      float w2;
      if (a2.isWatched())
        w2 = 0;
      else
      {
        Float fw = wMap.get(a2);
        if (fw != null)
          w2 = fw;
        else
        {
          w2 = calcW(a2, ignoreRed ? null : mfsCache);
          wMap.put(a2, w2);
        }
      }
      if ((w1 == 0) && (w2 == 0))
        return longComp(m2 == null ? a2.getStartTime() : m2.getRecordTime(), m1 == null ? a1.getStartTime() : m1.getRecordTime());
      if (w1 == 0)
        return 1;
      if (w2 == 0)
        return -1;
      boolean love1 = god.isLoveAir(a1);
      boolean love2 = god.isLoveAir(a2);
      // Switched the order of favorites so newer are no top 4/2/03
      if (love1 && love2)
        return longComp(m2 == null ? 0 : m2.getRecordTime(), m1 == null ? 0 : m1.getRecordTime());
      else if (love1) return -1;
      else if (love2) return 1;
      float currCount = god.getWatchCount() + 1;
      float x1 = (currCount - (m1 == null ? 0 : m1.getCreateWatchCount())) / w1;
      float x2 = (currCount - (m2 == null ? 0 : m2.getCreateWatchCount())) / w2;
      if (x1 == x2) return 0;
      if (x1 < x2) return -1;
      return 1;
    }
    private MediaFile[] mfsCache;
    private boolean ignoreRed;
    private HashMap<Airing, Float> wMap;
  }

  protected static final Comparator<Airing> AirStartTimeSorter =
      new Comparator<Airing>()
  {
    public int compare(Airing a1, Airing a2)
    {
      if (a1 == a2)
        return 0;
      else if (a1 == null)
        return 1;
      else if (a2 == null)
        return -1;

      long timeDiff = a1.time - a2.time;
      if (timeDiff == 0)
        return a1.id - a2.id;
      else
        return (timeDiff < 0) ? -1 : 1;
    }
  };

  public static class VideoStorage implements Cloneable
  {
    public VideoStorage(String inVideoDir)
    {
      this(inVideoDir, 20000000000L, VIDEO_DISKSPACE_USE_ALL);
    }
    public VideoStorage(String inVideoDir, long size, int rule)
    {
      videoDir = new File(inVideoDir).getAbsoluteFile();
      boolean diskOK = true;
      // Be CAREFUL to not create directories in /tmp/external for offline USB drives or we'll crash the system by filling up RAM w/ a recording!
      if (diskOK)
        IOUtils.safemkdirs(videoDir);
      videoDiskspace = size;
      videoDiskspaceRule = rule;
      fileSpaceReserves = new HashMap<Object, Long>();
    }

    public Object clone()
    {
      try
      {
        return super.clone();
      }
      catch (Exception e){ throw new InternalError("CLONE MISTAKE"); }
    }

    String prefString()
    {
      return videoDir.getAbsolutePath() + "," + videoDiskspace + "," + videoDiskspaceRule;
    }

    public long getAvailVideoDiskspace()
    {
      if (videoDiskspaceRule == VIDEO_DISKSPACE_USE_ONLY)
      {
        return videoDiskspace - getUsedVideoDiskspace();
      }
      else if (videoDiskspaceRule == VIDEO_DISKSPACE_LEAVE_FREE)
      {
        return FSManager.getInstance().getDiskFreeSpace(videoDir.toString()) - videoDiskspace;
      }
      else
        return FSManager.getInstance().getDiskFreeSpace(videoDir.toString());
    }

    public long getUnreservedVideoDiskspace()
    {
      long rv = getAvailVideoDiskspace();
      synchronized (fileSpaceReserves)
      {
        for (long val : fileSpaceReserves.values())
        {
          rv -= val;
        }
      }
      return rv;
    }

    public long getUsedVideoDiskspace()
    {
      MediaFile[] mFiles = Wizard.getInstance().getFiles();
      long totalSize = 0;
      for (int k = 0; k < mFiles.length; k++)
      {
        if (mFiles[k].videoDirectory != null &&
            videoDir.equals(new File(mFiles[k].videoDirectory)))
          totalSize += mFiles[k].getSize();
      }
      return totalSize;
    }

    public String toString()
    {
      return videoDir.getAbsolutePath() + " - " + (videoDiskspaceRule == VIDEO_DISKSPACE_USE_ALL ?
          Sage.rez("Diskspace_Use_All") : ((videoDiskspaceRule == VIDEO_DISKSPACE_USE_ONLY ?
              Sage.rez("Diskspace_Use_Only") : Sage.rez("Diskspace_Leave_Free")) + " " +
              (videoDiskspace/1e9) + " GB"));
    }

    public String getRuleName()
    {
      if (videoDiskspaceRule == VIDEO_DISKSPACE_USE_ONLY)
        return Sage.rez("Diskspace_Use_Only");
      else if (videoDiskspaceRule == VIDEO_DISKSPACE_USE_ALL)
        return Sage.rez("Diskspace_Use_All");
      else if (videoDiskspaceRule == VIDEO_DISKSPACE_LEAVE_FREE)
        return Sage.rez("Diskspace_Leave_Free");
      else
        return "";
    }

    // This is to optimize this statistic so getUsedVideoDiskspace is only called once
    public void addUsedTotalVideoDiskspace(long[] usedTotalRV)
    {
      long currUsed = getUsedVideoDiskspace();
      usedTotalRV[0] += currUsed;
      if (videoDiskspaceRule == VIDEO_DISKSPACE_USE_ONLY)
      {
        usedTotalRV[1] += videoDiskspace;
      }
      else if (videoDiskspaceRule == VIDEO_DISKSPACE_LEAVE_FREE)
      {
        usedTotalRV[1] += currUsed + FSManager.getInstance().getDiskFreeSpace(videoDir.toString()) - videoDiskspace;
      }
      else
        usedTotalRV[1] += currUsed + FSManager.getInstance().getDiskFreeSpace(videoDir.toString());
    }

    public File videoDir;
    public long videoDiskspace;
    public int videoDiskspaceRule;
    // This is a map of either EncoderState->Long or File->Long. Both are used for
    // files that are currently being written, the second one is not due to encoder control but something else
    Map<Object, Long> fileSpaceReserves;
    // If we've detected that this path is offline
    public boolean offline = false;
  }
}
