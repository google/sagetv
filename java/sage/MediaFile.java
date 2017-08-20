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

import sage.media.bluray.BluRayParser;
import sage.media.format.AudioFormat;
import sage.media.format.BitstreamFormat;
import sage.media.format.ContainerFormat;
import sage.media.format.FormatParser;
import sage.media.format.ID3Parser;
import sage.media.format.MPEGParser;
import sage.media.format.MediaFormat;
import sage.media.format.SubpictureFormat;
import sage.media.format.VideoFormat;
import sage.media.image.ImageLoader;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.WeakHashMap;
/*
 * AiringID of 0 is now OK. It means it's not linked to an Airing.
 */
public class MediaFile extends DBObject implements SegmentedFile
{
  private static final boolean OPTIMIZE_METADATA_MEM_USAGE = Sage.getBoolean("optimize_metadata_mem_usage", true);
  private static final long LOSS_FOR_COMPLETE = 600000L; // 10 minutes
  private static final long MINIMUM_TV_FILE_SIZE = Sage.getLong("minimum_tv_file_size", 500000);
  private static final long MINIMUM_TV_AUDIO_FILE_SIZE = Sage.getLong("minimum_tv_audio_file_size", 50000);
  private static final boolean VIDEO_THUMBNAIL_FILE_GEN = Sage.getBoolean("video_thumbnail_generation", true);
  private static final String THUMB_FOLDER_NAME = Sage.get("ui/thumbnail_folder", "GeneratedThumbnails");
  public static final File THUMB_FOLDER = new File(Sage.getPath("cache"), THUMB_FOLDER_NAME);
  public static final String LEGAL_FILE_NAME_CHARACTERS = " `!@#$%^&()-_+={}[];',";		// Jeff Harrison - 09/10/2016

  // Ensure this directory is created, we rely on this in a few places
  static
  {
    if (!THUMB_FOLDER.isDirectory()) {
      THUMB_FOLDER.mkdirs();
      if(Sage.MAC_OS_X) {
        try {
          IOUtils.exec(new String[] {"/bin/chmod", "777", THUMB_FOLDER.toString()});
        } catch (Throwable t) {
          System.out.println("Exception while setting permissions: " + t);
        }
      }
    }
  }

  private static final boolean BUILD_FAKE_MEDIA_FILE_SYSTEM = "true".equals(Sage.get("build_fake_mediafile_system", null));
  private static final boolean REPAIR_MISMATCHED_MEDIA_FILENAMES = Sage.getBoolean("repair_mismatched_media_filenames", false);
  // Temporary property for BluRay testing
  public static final boolean INCLUDE_BLURAYS_AS_DVDS = Sage.getBoolean("include_blurays_as_dvds", true);

  // NOTE: All of these SHOULD BE masks are mutually exclusive for each type. They're only
  // masks to facilitate faster searching of the media files by type
  public static final byte MEDIATYPE_NULL = 0;
  public static final byte MEDIATYPE_VIDEO = 1;
  public static final byte MEDIATYPE_AUDIO = 2;
  public static final byte MEDIATYPE_PICTURE = 3;
  public static final byte MEDIATYPE_DVD = 4;
  public static final byte MEDIATYPE_BLURAY = 4;

  public static final byte MEDIASUBTYPE_NULL = 30;
  public static final byte MEDIASUBTYPE_MPEG2_PS = 31;
  public static final byte MEDIASUBTYPE_MPEG2_TS = 32;
  public static final byte MEDIASUBTYPE_MP3 = 33;
  public static final byte MEDIASUBTYPE_JPEG = 34;
  public static final byte MEDIASUBTYPE_GIF = 35;
  public static final byte MEDIASUBTYPE_MPEG1_PS = 36;
  public static final byte MEDIASUBTYPE_PNG = 37;
  public static final byte MEDIASUBTYPE_WAV = 38;
  public static final byte MEDIASUBTYPE_AVI = 39;
  public static final byte MEDIASUBTYPE_MPEG2_PS_MPEG4_VIDEO = 40;
  public static final byte MEDIASUBTYPE_MPEG2_PS_DIVX_VIDEO = 41;
  public static final byte MEDIASUBTYPE_MPEG2_PES = 42;
  public static final byte MEDIASUBTYPE_WMA = 43;
  public static boolean isMPEG2LegacySubtype(byte test)
  {
    return (test == MEDIASUBTYPE_MPEG2_PS) || (test == MEDIASUBTYPE_MPEG2_TS) ||
        (test == MEDIASUBTYPE_MPEG2_PS_MPEG4_VIDEO) || (test == MEDIASUBTYPE_MPEG2_PS_DIVX_VIDEO) ||
        (test == MEDIASUBTYPE_MPEG2_PES);
  }

  public static final byte MEDIAFILE_NULL = 60;
  public static final byte MEDIAFILE_TV = 61;
  public static final byte MEDIAFILE_IMPORTED = 62;
  public static final byte MEDIAFILE_DEFAULT_DVD_DRIVE = 63;
  public static final byte MEDIAFILE_LIVE_STREAM = 64;
  public static final byte MEDIAFILE_LIVE_BUFFERED_STREAM = 65;
  public static final byte MEDIAFILE_THUMBNAIL = 66;
  public static final byte MEDIAFILE_LOCAL_PLAYBACK = 67;

  // manual, favorite, etc. are for when it was FIRST recorded,
  // they are not updated as time goes on
  public static final byte ACQUISITION_NULL = 90;
  public static final byte ACQUISITION_MANUAL = 91;
  public static final byte ACQUISITION_FAVORITE = 92;
  public static final byte ACQUISITION_INTELLIGENT = 93;
  public static final byte ACQUISITION_AUTOMATIC_BY_IMPORT_PATH = 94;
  public static final byte ACQUISITION_SYSTEM = 95;
  public static final byte ACQUISITION_WATCH_BUFFER = 96;
  public static final byte ACQUISITION_RECOVERED = 97;
  public static final byte ACQUISITION_AUTOMATIC_BY_VIDEO_STORAGE_PATH = 98;

  static boolean MAKE_ALL_MEDIAFILES_LOCAL = false;
  static final String MAKE_ALL_MEDIAFILES_LOCAL_PROP = "make_all_mediafiles_local";

  static final Map<CaptureDeviceInput, MediaFile> liveFileMap =
      new HashMap<CaptureDeviceInput, MediaFile>();
  static final Map<CaptureDeviceInput, MediaFile> liveBuffFileMap =
      new HashMap<CaptureDeviceInput, MediaFile>();
  private static String[] retainedMetadataProperties = null;

  public static MediaFile getLiveMediaFileForInput(CaptureDeviceInput cdi)
  {
    return liveFileMap.get(cdi);
  }

  static MediaFile putLiveMediaFileForInput(CaptureDeviceInput cdi, MediaFile file)
  {
    return liveFileMap.put(cdi, file);
  }

  public static MediaFile getLiveBufferedMediaFileForInput(CaptureDeviceInput cdi)
  {
    return liveBuffFileMap.get(cdi);
  }

  static MediaFile putLiveBufferedMediaFileForInput(CaptureDeviceInput cdi, MediaFile file)
  {
    return liveBuffFileMap.put(cdi, file);
  }

  protected MediaFile(int inFileID)
  {
    super(inFileID);
    times = Pooler.EMPTY_LONG_ARRAY;
    files = new Vector<File>();
    name = "";
    videoDirectory = "";
    host = SageTV.hostname;
    encodedBy = "";
    //mediaType = MEDIATYPE_NULL;
    //mediaSubtype = MEDIASUBTYPE_NULL;
    generalType = MEDIAFILE_NULL;
    acquisitionTech = ACQUISITION_NULL;
  }

  MediaFile(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    times = Pooler.EMPTY_LONG_ARRAY;
    files = new Vector<File>();
    int oldAiringID = 0;
    if (ver < 0x23)
      oldAiringID = readID(in, idMap); // airingID
    createWatchCount = in.readInt();
    int numSegs = in.readInt();
    times = new long[numSegs * 2];
    byte[] fakeBuf = null;
    boolean buildFakeFile = false;
    for (int i = 0; i < numSegs; i++)
    {
      String str = in.readUTF();
      files.addElement(new File((Sage.client && !Sage.WINDOWS_OS) ? IOUtils.convertPlatformPathChars(str) : str));
      if (BUILD_FAKE_MEDIA_FILE_SYSTEM)
      {
        File lastFile = files.lastElement();
        if (!lastFile.exists())
        {
          buildFakeFile = true;
        }
      }
      times[i*2] = in.readLong();
      times[i*2 + 1] = in.readLong();
    }
    if (ver <= 0x4C)
    {
      int numDeadAirs = in.readInt();
      for (int i = 0; i < numDeadAirs; i++)
      {
        in.readLong(); // Dead air time
        in.readLong(); // Dead air end
      }
    }
    videoDirectory = in.readUTF().intern();
    // Repair the videoDirectory since it could be wrong if the files moved directory
    if (files.size() > 0)
    {
      File parentFile = files.get(0).getParentFile();
      if (parentFile != null)
        videoDirectory = parentFile.toString().intern();
    }
    name = in.readUTF();
    archive = in.readBoolean();
    if (ver >= 0x2F)
      in.readBoolean(); // padding to help avoid reversing db encryption algorithms
    infoAiringID = readID(in, idMap);
    if (infoAiringID == 0)
      infoAiringID = oldAiringID;
    if (infoAiringID == 0)
      infoAiringID = id;
    if (ver > 0x1F)
      host = in.readUTF();
    else
      host = SageTV.hostname;
    if (MAKE_ALL_MEDIAFILES_LOCAL)
      host = SageTV.hostname;
    if (ver > 0x20)
    {
      if (ver <= 0x4C)
        in.readLong(); // Provider ID
      encodedBy = in.readUTF();
    }
    else if (ver >= 0x47)
      encodedBy = in.readUTF();
    else
      encodedBy = "";
    if (ver >= 0x30)
    {
      byte oldType = in.readByte();
      in.readByte(); // Media subtype
      generalType = in.readByte();
      if (Wizard.GENERATE_MEDIA_MASK)
      {
        if (oldType == MEDIATYPE_AUDIO)
          setMediaMask(MEDIA_MASK_MUSIC);
        else if (oldType == MEDIATYPE_PICTURE)
          setMediaMask(MEDIA_MASK_PICTURE);
        else if (oldType == MEDIATYPE_VIDEO)
          setMediaMask(MEDIA_MASK_VIDEO);
        else if (oldType == MEDIATYPE_DVD)
          setMediaMask(MEDIA_MASK_DVD);
        else if (oldType == MEDIATYPE_BLURAY)
          setMediaMask(MEDIA_MASK_BLURAY);
        if (generalType == MEDIAFILE_TV)
          addMediaMask(MEDIA_MASK_TV);
      }
      acquisitionTech = in.readByte();
      forcedComplete = in.readBoolean();
    }
    else if (ver > 0x23)
    {
      int oldMediaType = in.readInt();
      switch (oldMediaType)
      {
        case 1: // MPEG2_MEDIATYPE
          setMediaMask(MEDIA_MASK_VIDEO | MEDIA_MASK_TV);
          generalType = MEDIAFILE_TV;
          acquisitionTech = ACQUISITION_NULL;
          break;
        case 2: // OTHER_MEDIATYPE
          setMediaMask(MEDIA_MASK_VIDEO);
          generalType = MEDIAFILE_IMPORTED;
          acquisitionTech = ACQUISITION_AUTOMATIC_BY_IMPORT_PATH;
          break;
        case 3: // DVD_MEDIATYPE
          setMediaMask(MEDIA_MASK_DVD);
          generalType = MEDIAFILE_IMPORTED;
          acquisitionTech = ACQUISITION_AUTOMATIC_BY_IMPORT_PATH;
          break;
        case 4: // PICTURE_MEDIATYPE
          setMediaMask(MEDIA_MASK_PICTURE);
          generalType = MEDIAFILE_IMPORTED;
          acquisitionTech = ACQUISITION_AUTOMATIC_BY_IMPORT_PATH;
          break;
        case 5: // AUDIO_MEDIATYPE
          setMediaMask(MEDIA_MASK_MUSIC);
          generalType = MEDIAFILE_IMPORTED;
          acquisitionTech = ACQUISITION_AUTOMATIC_BY_IMPORT_PATH;
          break;
        default:
          setMediaMask(MEDIA_MASK_VIDEO);
          generalType = MEDIAFILE_IMPORTED;
          acquisitionTech = ACQUISITION_AUTOMATIC_BY_IMPORT_PATH;
          break;
      }
      String fname = (files.isEmpty() ? "" : files.firstElement().toString().toLowerCase());
      forcedComplete = in.readBoolean();
    }
    else
    {
      setMediaMask(MEDIA_MASK_VIDEO | MEDIA_MASK_TV);
      generalType = MEDIAFILE_TV;
      acquisitionTech = ACQUISITION_NULL;
    }
    if (ver >= 0x27)
    {
      String thumbStr = in.readUTF();
      if (thumbStr.length() != 0)
      {
        thumbnailFile = new File((Sage.client && !Sage.WINDOWS_OS) ? IOUtils.convertPlatformPathChars(thumbStr) : thumbStr);
        if (!files.isEmpty() && thumbnailFile.equals(files.get(0)))
          thumbnailFile = files.get(0);
      }
      thumbnailOffset = in.readLong();

      thumbnailSize = in.readInt();

      // These should be autogenerated thumbnails instead
      if (ver < 0x31 && isPicture())
      {
        thumbnailFile = null;
        thumbnailOffset = 0;
        thumbnailSize = 0;
      }
    }
    if (ver >= 0x33 && ver <= 0x4C)
      in.readLong(); // Stream buffer size
    if (ver >= 0x36)
    {
      String ff = in.readUTF();
      if (ff.length() > 0)
        fileFormat = ContainerFormat.buildFormatFromString(ff);
    }

    if (!Sage.client && fileFormat == null && !hasMediaMask(MEDIA_MASK_DVD) && !isAnyLiveStream() &&
        generalType != MEDIAFILE_LOCAL_PLAYBACK && !files.isEmpty())
    {
      // Don't do the file format detection here; it can cause major delays in startup for a few bad files
    }
    else if (fileFormat == null && hasMediaMask(MEDIA_MASK_DVD))
    {
      fileFormat = new ContainerFormat();
      fileFormat.setFormatName("DVD");
    }
    Carny.getInstance().notifyOfWatchCount(createWatchCount);
    if (buildFakeFile)
    {
      for (int i = 0; i < files.size(); i++)
      {
        File lastFile = files.get(i);
        System.out.println("GENERATING FAKE MEDIAFILE TO: " + lastFile);
        IOUtils.safemkdirs(lastFile.getParentFile());
        if (isDVD() || isBluRay())
        {
          lastFile.mkdir();
        }
        else
        {
          try
          {
            FileOutputStream fos = new FileOutputStream(lastFile);
            if (fakeBuf == null)
              fakeBuf = new byte[32768];
            for (int j = 0; j < (isTV() ? 16 : 1); j++)
              fos.write(fakeBuf);
            fos.close();
          }
          catch (Exception e)
          {
            System.out.println("ERROR:" + e);
            e.printStackTrace();
          }
        }
      }
    }
  }

  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    out.writeInt(createWatchCount);
    out.writeInt(files.size());
    for (int i = 0; i < files.size(); i++)
    {
      File currFile = files.elementAt(i);
      if (isTV() && REPAIR_MISMATCHED_MEDIA_FILENAMES && currFile.isFile())
      {
        String currName = currFile.getName();
        File targetFile = getDesiredSegmentFile(i, null, false);
        if (!targetFile.getName().equals(currName))
        {
          System.out.println("Repairing mismatched MediaFile - old name=" + currName + " new name=" + targetFile.getName());
          if (!currFile.renameTo(targetFile))
          {
            System.out.println("FAILED renaming to repair mismatch of file:" + currFile);
          }
          else
          {
            currFile = targetFile;
            files.setElementAt(currFile, i);
          }
        }
      }
      out.writeUTF(currFile.toString());
      out.writeLong(times[2*i]);
      out.writeLong(times[2*i + 1]);
    }
    out.writeUTF(videoDirectory);
    out.writeUTF(name);
    out.writeBoolean(archive);
    out.writeBoolean(true); // padding to help avoid reversing db encryption algorithms
    out.writeInt(infoAiringID);
    out.writeUTF(host);
    out.writeUTF(encodedBy);
    out.writeByte((byte)0); // Media type
    out.writeByte((byte)0); // Media subtype
    out.writeByte(generalType);
    out.writeByte(acquisitionTech);
    out.writeBoolean(forcedComplete);
    if (thumbnailFile == null)
      out.writeUTF("");
    else
      out.writeUTF(thumbnailFile.toString());
    out.writeLong(thumbnailOffset);
    out.writeInt(thumbnailSize);
    out.writeUTF((fileFormat == null) ? "" : fileFormat.getFullPropertyString());
  }

  void initializeRecovery(File recoveredFile)
  {
    acquisitionTech = MediaFile.ACQUISITION_RECOVERED;
    addTimes(recoveredFile.lastModified() -
        FormatParser.getFileDuration(recoveredFile), recoveredFile.lastModified());
    files.addElement(recoveredFile);
    fileFormat = FormatParser.getFileFormat(recoveredFile);

    // The special case we have to deal with is when we're using episodes in filenames
    // and then the episode name changes. This will cause an error when doing the recovery, so
    // we need to be smart about this
    String recoverName = recoveredFile.getName();
    int lastDashIndex = recoverName.lastIndexOf('-', recoverName.lastIndexOf('-') - 1);
    String usedName = recoverName.substring(0, lastDashIndex);
    recoveredFile = getNextSegment(true, usedName, false);
    while (recoveredFile.isFile())
    {
      if (recoveredFile.length() > 0)
      {
        addTimes(recoveredFile.lastModified() -
            FormatParser.getFileDuration(recoveredFile), recoveredFile.lastModified());
        files.addElement(recoveredFile);
        if (fileFormat == null)
          fileFormat = FormatParser.getFileFormat(recoveredFile);
      }
      else if (!deleteFileAndIndex(recoveredFile))
        break; // we need to exit the loop here or we'll be in an infinite loop
      recoveredFile = getNextSegment(true, usedName, false);
    }
    createWatchCount = Carny.getInstance().getWatchCount();
  }

  private boolean deleteFileAndIndex(File f)
  {
    boolean rv = f.delete();
    return rv;
  }

  void initialize(long recStart)
  {
    // We also do media file recovery here
    File recoveredFile = getNextSegment(true);
    while (recoveredFile.isFile())
    {
      if (recoveredFile.length() > 0)
      {
        addTimes(recoveredFile.lastModified() -
            FormatParser.getFileDuration(recoveredFile), recoveredFile.lastModified());
        files.addElement(recoveredFile);
        if (fileFormat == null)
          fileFormat = FormatParser.getFileFormat(recoveredFile);
      }
      else if (!deleteFileAndIndex(recoveredFile))
        break; // we need to exit the loop here or we'll be in an infinite loop
      recoveredFile = getNextSegment(true);
    }
    if (recStart > 0)
    {
      addTimes(recStart, 0);
      files.addElement(getNextSegment(false));
    }
    createWatchCount = Carny.getInstance().getWatchCount();
  }

  boolean initialize(File rawFile, String namePrefix, ContainerFormat inFileFormat)
  {
    return initialize(rawFile, namePrefix, false, inFileFormat);
  }
  private boolean initialize(File rawFile, String namePrefix, boolean redo, ContainerFormat inFileFormat)
  {
    boolean rv = true;
    files.addElement(rawFile);
    if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
      generalType = MEDIAFILE_IMPORTED;
    Airing metaAir = null;
    // Temporarily guess the media type based off the filename extension that's mapped for import (really only used for detecting DVDs)
    String nameLC = rawFile.getName().toLowerCase();
    setMediaMask(SeekerSelector.getInstance().guessImportedMediaMask(rawFile.getAbsolutePath()));

    long rawFileLastMod = rawFile.lastModified();
    if (rawFileLastMod == 0)
      rawFileLastMod = Sage.time();

    if (getMediaMask() == DBObject.MEDIA_MASK_DVD ||
        (rawFile.isDirectory() && new File(rawFile, "VIDEO_TS.IFO").isFile()))
    {
      // DVDs are special...
      setMediaMask(DBObject.MEDIA_MASK_DVD);
      addTimes(rawFileLastMod - 1, rawFileLastMod);
      fileFormat = new ContainerFormat();
      fileFormat.setFormatName("DVD");
      File metaFile = getMetadataPropertiesFile();
      Map dvdMeta = FormatParser.extractMetadataProperties(rawFile, metaFile, true);
      if (dvdMeta != null)
        fileFormat.addMetadata(dvdMeta);
    }
    else if (getMediaMask() == DBObject.MEDIA_MASK_BLURAY ||
        (rawFile.isDirectory() && new File(rawFile, "index.bdmv").isFile() && new File(rawFile, "MovieObject.bdmv").isFile()))
    {
      // BluRays are special...
      setMediaMask(DBObject.MEDIA_MASK_BLURAY);
      fileFormat = (inFileFormat != null) ? inFileFormat : FormatParser.getFileFormat(rawFile);
      long theDur = 1;
      if (fileFormat != null)
      {
        File metaFile = getMetadataPropertiesFile();
        Map dvdMeta = FormatParser.extractMetadataProperties(rawFile, metaFile, true);
        if (dvdMeta != null)
          fileFormat.addMetadata(dvdMeta);
        theDur = Math.max(1, fileFormat.getDuration());
      }
      else
      {
        if (Sage.DBG) System.out.println("ERROR detecting BluRay file format for " + rawFile);
      }
      addTimes(rawFileLastMod - theDur, rawFileLastMod);
    }
    else
    {
      fileFormat = (inFileFormat != null) ? inFileFormat : FormatParser.getFileFormat(rawFile);

      if (fileFormat == null)
      {
        // We couldn't figure out what kind of file this is....
        addTimes(rawFileLastMod - 1, rawFileLastMod);
      }
      else
      {
        String containerType = fileFormat.getFormatName();
        if (MediaFormat.BMP.equals(containerType) || MediaFormat.JPEG.equals(containerType) ||
            MediaFormat.GIF.equals(containerType) || MediaFormat.PNG.equals(containerType) ||
            MediaFormat.TIFF.equals(containerType))
        {
          setMediaMask(MEDIA_MASK_PICTURE);
          addTimes(rawFileLastMod - 1, rawFileLastMod);
        }
        else
        {
          // Find out what kind of streams are in it and base it off that
          int numStreams = fileFormat.getNumberOfStreams();
          for (int i = 0; i < numStreams; i++)
          {
            BitstreamFormat bf = fileFormat.getStreamFormat(i);
            if (bf instanceof VideoFormat)
            {
              setMediaMask(MEDIA_MASK_VIDEO);
              break;
            }
            else if (bf instanceof AudioFormat)
            {
              setMediaMask(MEDIA_MASK_MUSIC);
            }
          }

          // Use the duration to setup the media times for the file
          // We use the metadata duration value ahead of anything else if it's non-zero
          // Do NOT do this for FLV files since FFMPEG will report their duration in seconds through metadata
          long theDur = 0;
          if (fileFormat.hasMetadata(ContainerFormat.META_DURATION) && !MediaFormat.FLASH_VIDEO.equals(fileFormat.getFormatName()))
          {
            try
            {
              theDur = Long.parseLong(fileFormat.getMetadataProperty(ContainerFormat.META_DURATION));
            }
            catch (NumberFormatException e)
            {
              System.out.println("ERROR parsing duration string of:" + e);
            }
          }
          if (theDur == 0)
          {
            // Now use the value from the format parser
            theDur = fileFormat.getDuration();
          }
          if (theDur <= 0)
          {
            // Just use one millisecond as it's safest that way
            theDur = 1;
          }
          if (rawFileLastMod - theDur <= 0)
          {
            rawFileLastMod += -1 * (rawFileLastMod - theDur) + 1;
          }
          addTimes(rawFileLastMod - theDur, rawFileLastMod);
        }
      }
    }

    // NOTE: .tivo files don't parse correctly so set their media mask forcibly
    if (nameLC.endsWith(".tivo"))
      setMediaMask(MEDIA_MASK_VIDEO);

    // If there's metadata for this file that wasn't automatically created by us
    // then it should be left alone. All of our created Shows have an 'MF' prefix on their external ID
    boolean preserveMetadata = false;
    if (myAiring == null)
    {
      if (infoAiringID != id && infoAiringID != 0)
      {
        Airing testAir = Wizard.getInstance().getAiringForID(infoAiringID, false, true);
        if (testAir != null)
        {
          Show s = testAir.getShow();
          if (s != null)
            preserveMetadata = !s.getExternalID().startsWith("MF");
        }
      }
    }
    else
    {
      Show s = myAiring.getShow();
      if (s != null)
        preserveMetadata = !s.getExternalID().startsWith("MF");
    }

    if (!preserveMetadata || !redo)
    {
      if (!redo)
      {
        archive = true;
        createWatchCount = Carny.getInstance().getWatchCount();
      }
      name = namePrefix + rawFile.getName();

      if (isPicture())
      {
        String dbName = rawFile.getName();
        String dbEpisode = dbName;
        if (dbEpisode.lastIndexOf('.') != -1)
        {
          dbEpisode = dbEpisode.substring(0, dbEpisode.lastIndexOf('.'));
          dbName = dbEpisode;
        }
        byte orientation = 0;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_TRACK))
        {
          try
          {
            orientation = Byte.parseByte(fileFormat.getMetadataProperty(MediaFormat.META_TRACK));
          }
          catch (Exception e)
          {
            System.out.println("ERROR parsing orientation of:" + e);
          }
        }
        long airTime = getRecordTime();
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_AIRING_TIME))
        {
          try
          {
            airTime = Long.parseLong(fileFormat.getMetadataProperty(MediaFormat.META_AIRING_TIME));
          }
          catch (NumberFormatException nfe)
          {
            System.out.println("ERROR parsing long from exif metadata:" + nfe);
          }
        }
        myAiring = Wizard.getInstance().addAiring(Wizard.getInstance().addShow(dbName, null, dbEpisode,
            (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_DESCRIPTION) : ""), 0,
            null, Pooler.EMPTY_STRING_ARRAY, Pooler.EMPTY_BYTE_ARRAY, null, null,
            "", null, null, "MF" + Integer.toString(id == 0 ? System.identityHashCode(this) : id), "English", 0, DBObject.MEDIA_MASK_PICTURE, (short)0, (short)0, false,
            (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0), 0/*1000000 + id*/,
            airTime, getRecordDuration(), orientation, (byte)0, (byte)0,
            DBObject.MEDIA_MASK_PICTURE);
        infoAiringID = myAiring.id;
      }
      else if (isMusic())
      {
        String dbEpisode = rawFile.getName();
        if (dbEpisode.lastIndexOf('.') != -1)
        {
          dbEpisode = dbEpisode.substring(0, dbEpisode.lastIndexOf('.'));
        }
        String metaTitle = (fileFormat != null) ? fileFormat.getMetadataProperty(MediaFormat.META_TITLE) : null;
        if (metaTitle == null || metaTitle.length() == 0)
          metaTitle = dbEpisode;
        String metaAlbum = (fileFormat != null) ? fileFormat.getMetadataProperty(MediaFormat.META_ALBUM) : null;
        if (metaAlbum == null || metaAlbum.length() == 0)
          metaAlbum = dbEpisode;
        String metaCategory = (fileFormat != null) ? fileFormat.getMetadataProperty(MediaFormat.META_GENRE) : null;
        String[] categories = null;
        if (metaCategory != null && metaCategory.length() > 0)
        {
          StringTokenizer toker = new StringTokenizer(metaCategory, ";/");
          categories = new String[toker.countTokens()];
          int catIndex = 0;
          while (toker.hasMoreTokens())
            categories[catIndex++] = toker.nextToken().trim();
        }
        if ((metaCategory == null || metaCategory.length() == 0) && fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_GENRE_ID))
        {
          try
          {
            int genreID = Integer.parseInt(fileFormat.getMetadataProperty(MediaFormat.META_GENRE_ID));
            if (genreID >= 0 && genreID < ID3Parser.ID3V1_GENRES.length)
              categories = new String[] { ID3Parser.ID3V1_GENRES[genreID] };
          }
          catch (Exception e)
          {
            System.out.println("ERROR parsing genre ID of:" + e);
          }
        }
        byte track = 0;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_TRACK))
        {
          try
          {
            track = (byte)(Integer.parseInt(fileFormat.getMetadataProperty(MediaFormat.META_TRACK)) & 0xFF);
          }
          catch (Exception e)
          {
            System.out.println("ERROR parsing track of:" + e);
          }
        }
        List<String> peoples = new ArrayList<String>();
        List<Byte> roles = new ArrayList<Byte>();
        if (fileFormat != null)
        {
          String metaArtist = fileFormat.getMetadataProperty(MediaFormat.META_ARTIST);
          String metaAlbumArtist = fileFormat.getMetadataProperty(MediaFormat.META_ALBUM_ARTIST);
          String metaComposers = fileFormat.getMetadataProperty(MediaFormat.META_COMPOSER);
          if (metaArtist != null && metaArtist.length() > 0)
          {
            // What about artist's names that have a '/' character in them??
            if ("AC/DC".equals(metaArtist))
            {
              peoples.add(metaArtist);
              roles.add(Show.ARTIST_ROLE);
            }
            else
            {
              StringTokenizer toker = new StringTokenizer(metaArtist, "/;");
              while (toker.hasMoreTokens())
              {
                peoples.add(toker.nextToken().trim());
                roles.add(Show.ARTIST_ROLE);
              }
            }
          }
          if (metaAlbumArtist != null && metaAlbumArtist.length() > 0)
          {
            // What about artist's names that have a '/' character in them??
            if ("AC/DC".equals(metaAlbumArtist))
            {
              peoples.add(metaAlbumArtist);
              roles.add(new Byte(Show.ALBUM_ARTIST_ROLE));
              if (metaArtist == null || metaArtist.length() == 0)
              {
                peoples.add(metaAlbumArtist);
                roles.add(new Byte(Show.ARTIST_ROLE));
              }
            }
            else
            {
              StringTokenizer toker = new StringTokenizer(metaAlbumArtist, "/;");
              while (toker.hasMoreTokens())
              {
                peoples.add(toker.nextToken().trim());
                roles.add(new Byte(Show.ALBUM_ARTIST_ROLE));
                if (metaArtist == null || metaArtist.length() == 0)
                {
                  peoples.add(peoples.get(peoples.size() - 1));
                  roles.add(new Byte(Show.ARTIST_ROLE));
                }
              }
            }
          }
          if (metaComposers != null && metaComposers.length() > 0)
          {
            // What about artist's names that have a '/' character in them??
            if ("AC/DC".equals(metaComposers))
            {
              peoples.add(metaComposers);
              roles.add(new Byte(Show.COMPOSER_ROLE));
            }
            else
            {
              StringTokenizer toker = new StringTokenizer(metaComposers, "/;");
              while (toker.hasMoreTokens())
              {
                peoples.add(toker.nextToken().trim());
                roles.add(new Byte(Show.COMPOSER_ROLE));
              }
            }
          }
        }
        String[] peopleArray = peoples.toArray(Pooler.EMPTY_STRING_ARRAY);
        byte[] rolesArray = new byte[roles.size()];
        for (int i = 0; i < roles.size(); i++)
          rolesArray[i] = roles.get(i).byteValue();
        myAiring = Wizard.getInstance().addAiring(Wizard.getInstance().addShow(metaAlbum, null, metaTitle,
            (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_DESCRIPTION) : ""), 0,
            categories, peopleArray, rolesArray, null, null,
            (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_YEAR) : ""),
            null, null, "MF" + Integer.toString(id == 0 ? System.identityHashCode(this) : id),
            (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_LANGUAGE) : "English"), 0,
            DBObject.MEDIA_MASK_MUSIC, (short)0, (short)0, false, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0),
            0/*1000000 + id*/, getRecordTime(), getRecordDuration(), track, (byte)0, (byte)0,
            DBObject.MEDIA_MASK_MUSIC);
        infoAiringID = myAiring.id;
      }
      else //if (isVideo()) or DVD or BluRay
      {
        // The title has the prefix on it
        String dbName = rawFile.getName();
        String dbEpisode = dbName;
        if (isDVD())
        {
          if (dbEpisode.equalsIgnoreCase(Seeker.DVD_VOLUME_SECRET) && rawFile.getParentFile() != null)
            dbName = dbEpisode = rawFile.getParentFile().getName();
        }
        else if (isBluRay())
        {
          if (dbEpisode.equalsIgnoreCase(Seeker.BLURAY_VOLUME_SECRET) && rawFile.getParentFile() != null)
            dbName = dbEpisode = rawFile.getParentFile().getName();
        }
        else if (dbEpisode.lastIndexOf('.') != -1)
        {
          dbEpisode = dbEpisode.substring(0, dbEpisode.lastIndexOf('.'));
          dbName = dbEpisode;
        }
        String extID = "MF" + Integer.toString(id == 0 ? System.identityHashCode(this) : id);
        if (fileFormat != null && fileFormat.hasMetadata(ContainerFormat.META_EXTERNAL_ID))
        {
          String newExtID = fileFormat.getMetadataProperty(ContainerFormat.META_EXTERNAL_ID);
          if (newExtID != null && newExtID.length() > 0)
          {
            checkForTVFileConversion(extID = newExtID);
          }
        }
        if (fileFormat != null)
        {
          if (fileFormat.hasMetadata(ContainerFormat.META_TITLE) && fileFormat.getMetadataProperty(ContainerFormat.META_TITLE).length() > 0)
            dbEpisode = dbName = fileFormat.getMetadataProperty(ContainerFormat.META_TITLE);
          if (fileFormat.hasMetadata(ContainerFormat.META_EPISODE_NAME) && fileFormat.getMetadataProperty(ContainerFormat.META_EPISODE_NAME).length() > 0)
            dbEpisode = fileFormat.getMetadataProperty(ContainerFormat.META_EPISODE_NAME);
          else if (isTV())
            dbEpisode = "";
        }
        String[] myPeeps;
        byte[] myRoles;
        if (fileFormat != null)
        {
          List<String> peepVec = null;
          List<Byte> roleVec = null;
          // the first role name is empty
          for (int i = 1; i < Show.ROLE_NAMES.length; i++)
          {
            if (fileFormat.hasMetadata(Show.ROLE_NAMES[i]))
            {
              if (peepVec == null)
              {
                peepVec = new ArrayList<String>();
                roleVec = new ArrayList<Byte>();
              }
              StringTokenizer toker = new StringTokenizer(fileFormat.getMetadataProperty(Show.ROLE_NAMES[i]), ";/");
              while (toker.hasMoreTokens())
              {
                peepVec.add(toker.nextToken().trim());
                roleVec.add(new Byte((byte)i));
              }
            }
          }
          if (peepVec == null || peepVec.isEmpty())
          {
            myPeeps = Pooler.EMPTY_STRING_ARRAY;
            myRoles = Pooler.EMPTY_BYTE_ARRAY;
          }
          else
          {
            myPeeps = peepVec.toArray(Pooler.EMPTY_STRING_ARRAY);
            myRoles = new byte[roleVec.size()];
            for (int i = 0; i < myRoles.length; i++)
              myRoles[i] = roleVec.get(i).byteValue();
          }
        }
        else
        {
          myPeeps = Pooler.EMPTY_STRING_ARRAY;
          myRoles = Pooler.EMPTY_BYTE_ARRAY;
        }
        int seriesID = 0;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_SERIES_ID))
        {
          try
          {
            seriesID = Integer.parseInt(fileFormat.getMetadataProperty(MediaFormat.META_SERIES_ID));
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
          }

        }
        int showcardID = 0;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_SHOWCARD_ID))
        {
          try
          {
            showcardID = Integer.parseInt(fileFormat.getMetadataProperty(MediaFormat.META_SHOWCARD_ID));
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
          }

        }
        long runtime = 0;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_RUNNING_TIME))
        {
          try
          {
            runtime = Long.parseLong(fileFormat.getMetadataProperty(MediaFormat.META_RUNNING_TIME));
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
          }
        }
        long orgAirDate = 0;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_ORIGINAL_AIR_DATE))
        {
          try
          {
            orgAirDate = parseMetaDate(fileFormat.getMetadataProperty(MediaFormat.META_ORIGINAL_AIR_DATE));
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
          }
        }
        String[] ers = null;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_EXTENDED_RATINGS))
        {
          StringTokenizer toker = new StringTokenizer(fileFormat.getMetadataProperty(MediaFormat.META_EXTENDED_RATINGS), ";,");
          ers = new String[toker.countTokens()];
          for (int i = 0; i < ers.length; i++)
            ers[i] = toker.nextToken().trim();
        }
        String[] misc = null;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_MISC))
        {
          StringTokenizer toker = new StringTokenizer(fileFormat.getMetadataProperty(MediaFormat.META_MISC), ";,");
          misc = new String[toker.countTokens()];
          for (int i = 0; i < misc.length; i++)
            misc[i] = toker.nextToken().trim();
        }
        byte partsB = 0;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_PART_NUMBER))
        {
          try
          {
            partsB = (byte)(((Integer.parseInt(fileFormat.getMetadataProperty(MediaFormat.META_PART_NUMBER)) & 0xF) << 4) & 0xFF);
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
          }
        }
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_TOTAL_PARTS))
        {
          try
          {
            partsB = (byte)((partsB | (byte)(Integer.parseInt(fileFormat.getMetadataProperty(MediaFormat.META_TOTAL_PARTS)) & 0xF)) & 0xFF);
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
          }
        }
        int miscB = 0;
        if (fileFormat != null && !isTV())
        {
          VideoFormat vidForm = fileFormat.getVideoFormat();
          if (vidForm != null && (vidForm.getHeight() > 700 || vidForm.getWidth() > 1200))
            miscB = (miscB | Airing.HDTV_MASK);
        }
        if (fileFormat != null)
        {
          if (fileFormat.hasMetadata(MediaFormat.META_HDTV) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_HDTV)))
            miscB = (miscB | Airing.HDTV_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_CC) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_CC)))
            miscB = (miscB | Airing.CC_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_STEREO) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_STEREO)))
            miscB = (miscB | Airing.STEREO_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_SUBTITLED) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_SUBTITLED)))
            miscB = (miscB | Airing.SUBTITLE_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_PREMIERE) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_PREMIERE)))
            miscB = (miscB | Airing.PREMIERE_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_SEASON_PREMIERE) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_SEASON_PREMIERE)))
            miscB = (miscB | Airing.SEASON_PREMIERE_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_SERIES_PREMIERE) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_SERIES_PREMIERE)))
            miscB = (miscB | Airing.SERIES_PREMIERE_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_CHANNEL_PREMIERE) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_CHANNEL_PREMIERE)))
            miscB = (miscB | Airing.CHANNEL_PREMIERE_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_SEASON_FINALE) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_SEASON_FINALE)))
            miscB = (miscB | Airing.SEASON_FINALE_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_SERIES_FINALE) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_SERIES_FINALE)))
            miscB = (miscB | Airing.SERIES_FINALE_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_SAP) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_SAP)))
            miscB = (miscB | Airing.SAP_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_3D) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_3D)))
            miscB = (miscB | Airing.THREED_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_DD51) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_DD51)))
            miscB = (miscB | Airing.DD51_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_DOLBY) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_DOLBY)))
            miscB = (miscB | Airing.DOLBY_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_LETTERBOX) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_LETTERBOX)))
            miscB = (miscB | Airing.LETTERBOX_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_LIVE) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_LIVE)))
            miscB = (miscB | Airing.LIVE_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_NEW) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_NEW)))
            miscB = (miscB | Airing.NEW_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_WIDESCREEN) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_WIDESCREEN)))
            miscB = (miscB | Airing.WIDESCREEN_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_SURROUND) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_SURROUND)))
            miscB = (miscB | Airing.SURROUND_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_DUBBED) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_DUBBED)))
            miscB = (miscB | Airing.DUBBED_MASK);
          if (fileFormat.hasMetadata(MediaFormat.META_TAPED) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_TAPED)))
            miscB = (miscB | Airing.TAPE_MASK);
        }
        byte prB = 0;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_PARENTAL_RATING))
        {
          String prName = fileFormat.getMetadataProperty(MediaFormat.META_PARENTAL_RATING);
          for (int i = 1; i < Airing.PR_NAMES.length; i++)
            if (prName.equals(Airing.PR_NAMES[i]))
            {
              prB = (byte) i;
              break;
            }
        }
        String metaCategory = (fileFormat != null) ? fileFormat.getMetadataProperty(MediaFormat.META_GENRE) : null;
        String[] categories = null;
        if (metaCategory != null && metaCategory.length() > 0)
        {
          StringTokenizer toker = new StringTokenizer(metaCategory, ";/");
          categories = new String[toker.countTokens()];
          int catIndex = 0;
          while (toker.hasMoreTokens())
            categories[catIndex++] = toker.nextToken().trim();
        }
        int seasonNumber = 0;
        int episodeNumber = 0;
        int altEpisodeNumber = 0;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_SEASON_NUMBER))
        {
          try
          {
            seasonNumber = Integer.parseInt(fileFormat.getMetadataProperty(MediaFormat.META_SEASON_NUMBER));
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
          }
          try
          {
            episodeNumber = Integer.parseInt(fileFormat.getMetadataProperty(MediaFormat.META_EPISODE_NUMBER));
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
          }
          if (fileFormat.hasMetadata(MediaFormat.META_ALT_EPISODE_NUMBER))
          {
            try
            {
              altEpisodeNumber = Integer.parseInt(fileFormat.getMetadataProperty(MediaFormat.META_ALT_EPISODE_NUMBER));
            }
            catch (Exception e)
            {
              if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
            }
          }
        }
        boolean forcedUnique = (fileFormat != null) && "true".equalsIgnoreCase(fileFormat.getMetadataProperty(MediaFormat.META_FORCED_UNIQUE));
        long coreImageBits = 0;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_CORE_IMAGERY))
        {
          try
          {
            coreImageBits = Long.parseLong(fileFormat.getMetadataProperty(MediaFormat.META_CORE_IMAGERY), 16);
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
          }
        }
        short[] imageIDs = null;
        if (fileFormat != null && fileFormat.hasMetadata(MediaFormat.META_CORE_IMAGERY2))
        {
          String imageStr = fileFormat.getMetadataProperty(MediaFormat.META_CORE_IMAGERY2);
          if (imageStr != null && imageStr.length() > 0)
          {
            StringTokenizer toker = new StringTokenizer(imageStr, ",");
            imageIDs = new short[toker.countTokens()];
            try
            {
              int i = 0;
              while (toker.hasMoreTokens())
              {
                imageIDs[i++] = Short.parseShort(toker.nextToken());
              }
            }
            catch (Exception e)
            {
              if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
              imageIDs = null;
            }
          }
        }
        long airTime = getRecordTime();
        long airDur = getRecordDuration();
        if (fileFormat != null && isTV() && fileFormat.hasMetadata(MediaFormat.META_AIRING_TIME) &&
            fileFormat.hasMetadata(MediaFormat.META_AIRING_DURATION))
        {
          try
          {
            long newAirTime = Long.parseLong(fileFormat.getMetadataProperty(MediaFormat.META_AIRING_TIME));
            long newAirDur = Long.parseLong(fileFormat.getMetadataProperty(MediaFormat.META_AIRING_DURATION));
            // We need to make sure the file's timestamp is correct for this; and if it doesn't align in some way then
            // we correct it so that it starts at the same time as the Airing did. Its valid if the file's start time or end time
            // is within the airing start/stop; or if both the file's start & end time are each before and after the airing's start
            // and end time respectively.
            if (!((airTime >= newAirTime && airTime <= newAirTime + newAirDur) ||
                (airTime + airDur >= newAirTime && airTime + airDur <= newAirTime + newAirDur) ||
                (airTime < newAirTime && airTime + airDur >= newAirTime + newAirDur)))
            {
              if (Sage.DBG) System.out.println("Correcting file timestamp to match Airing time window filetime=" + new Date(airTime) + " airingTime=" +
                  new Date(newAirTime));
              if (rawFile.setLastModified(newAirTime + airDur))
              {
                times[times.length - 2] = newAirTime;
                times[times.length - 1] = newAirTime + airDur;
                airTime = newAirTime;
                airDur = newAirDur;
              }
              else
              {
                if (Sage.DBG) System.out.println("File timestamp correction failed! Use existing Airing values instead.");
              }
            }
            else
            {
              // File timestamps match the airing; so use the metadata airing values
              airTime = newAirTime;
              airDur = newAirDur;
            }
          }
          catch (NumberFormatException nfe)
          {
            System.out.println("ERROR parsing long from MediaFile metadata:" + nfe);
          }
        }

        Show newShow = null;
        if (seriesID == 0 && showcardID == 0 && imageIDs == null)
        {
          // legacy or imported metadata, use old addShow call
          newShow = Wizard.getInstance().addShow(dbName, null, dbEpisode,
              (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_DESCRIPTION) : ""), runtime,
              categories, myPeeps, myRoles,
              (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_RATED) : null),
              ers,
              (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_YEAR) : ""),
              null, misc, extID,
              (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_LANGUAGE) : "English"), orgAirDate,
              getMediaMask(), (short)seasonNumber, (short)episodeNumber, forcedUnique, (byte)((coreImageBits >> 56) & 0xFF),
              (byte)((coreImageBits >> 48) & 0xFF), (byte)((coreImageBits >> 40) & 0xFF), (byte)((coreImageBits >> 32) & 0xFF),
              (byte)((coreImageBits >> 24) & 0xFF), (byte)((coreImageBits >> 16) & 0xFF), (byte)((coreImageBits >> 8) & 0xFF),
              (byte)(coreImageBits & 0xFF));
        }
        else
        {
          newShow = Wizard.getInstance().addShow(dbName, dbEpisode,
              (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_DESCRIPTION) : ""), runtime,
              categories, Wizard.getInstance().getPeopleArray(myPeeps), myRoles,
              (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_RATED) : null),
              ers,
              (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_YEAR) : ""),
              null, misc, extID,
              (fileFormat != null ? fileFormat.getMetadataProperty(MediaFormat.META_LANGUAGE) : "English"), orgAirDate,
              getMediaMask(), (short)seasonNumber, (short)episodeNumber, (short)altEpisodeNumber, forcedUnique, showcardID, seriesID, imageIDs);
        }
        myAiring = Wizard.getInstance().addAiring(newShow, 0, airTime, airDur, partsB, miscB, prB, getMediaMask());
        infoAiringID = myAiring.id;
        // We don't mark TV recordings as 'TV' when they're imported on the embedded system because we have no
        // TV specific display menus...but we still want to handle multi-segment imports properly
        if (isTV())
        {
          // Attempt to recover other segments if this is a multisegment recording
          // The special case we have to deal with is when we're using episodes in filenames
          // and then the episode name changes. This will cause an error when doing the recovery, so
          // we need to be smart about this
          String recoverName = rawFile.getName();
          int idx1 = recoverName.lastIndexOf('-');
          if (idx1 != -1)
          {
            String usedName = recoverName.substring(0, idx1);
            File recoveredFile = getNextSegment(true, usedName, true);
            while (recoveredFile.isFile())
            {
              if (recoveredFile.length() > 0)
              {
                if (Sage.DBG) System.out.println("Found multi-segment file for importing at " + recoveredFile);
                // We may need to try to fix the timestamp on this file
                long fileTime = recoveredFile.lastModified();
                long fileDur = FormatParser.getFileDuration(recoveredFile);
                // Don't modify the timestamps on embedded systems since we won't be 'owning' the TV file
                if ((fileTime - fileDur) < getRecordEnd() || fileTime > myAiring.getEndTime())
                {
                  if (!recoveredFile.setLastModified(getRecordEnd() + 2000 + fileDur))
                  {
                    if (Sage.DBG) System.out.println("ERROR Could not set file modification time on " + recoveredFile + " for multi-segment import; skipping file");
                    break;
                  }
                }
                addTimes(recoveredFile.lastModified() - fileDur, recoveredFile.lastModified());
                files.addElement(recoveredFile);
                if (fileFormat == null)
                  fileFormat = FormatParser.getFileFormat(recoveredFile);
              }
              else if (!deleteFileAndIndex(recoveredFile))
                break; // we need to exit the loop here or we'll be in an infinite loop
              recoveredFile = getNextSegment(true, usedName, true);
            }
          }
        }
      }
    }
    else
    {
      Airing oldAiring = getContentAiring();
      if (oldAiring != null && (getRecordTime() != oldAiring.getStartTime() || getRecordDuration() != oldAiring.getDuration()))
      {
        // Be sure we update the times on the Airing object in case the times in our data have changed!
        myAiring = Wizard.getInstance().addAiring(oldAiring.getShow(), 0, getRecordTime(),
            getRecordDuration(), oldAiring.partsB, oldAiring.miscB, oldAiring.prB, getMediaMask());
        infoAiringID = myAiring.id;
      }
    }

    // This can probably be removed; but it's to ensure there's no side effects from the DVD metadata properties file parsing
    // NARFLEX - removed on 4/30/09
    //if (isDVD())
    //  fileFormat = null;

    // Check for a thumbnail
    checkForCorrespondingThumbFile(fileFormat);

    if (OPTIMIZE_METADATA_MEM_USAGE)
    {
      optimizeMetadataStorage();
    }

    return rv;
  }

  private static long parseMetaDate(String s)
  {
    try
    {
      return Long.parseLong(s);
    }
    catch (Exception e)
    {
    }
    // Check for YYYY-MM-DD format
    int idx1 = s.indexOf('-');
    if (idx1 == -1)
      idx1 = s.indexOf('.');
    if (idx1 != -1)
    {
      int idx2 = s.indexOf('-', idx1 + 1);
      if (idx2 == -1)
        idx2 = s.indexOf('.', idx1 + 1);
      if (idx2 != -1)
      {
        try
        {
          return new GregorianCalendar(Integer.parseInt(s.substring(0, idx1)),
              Integer.parseInt(s.substring(idx1 + 1, idx2)) - 1, Integer.parseInt(s.substring(idx2 + 1))).getTimeInMillis();
        }
        catch (Exception e)
        {
        }
      }
    }
    if (Sage.DBG) System.out.println("ERROR: Unable to parse metadata date format of: " + s);
    return 0;
  }

  private void optimizeMetadataStorage()
  {
    if (fileFormat != null)
    {
      if (retainedMetadataProperties == null)
      {
        String customNames = Sage.get("custom_metadata_properties", "");
        if (customNames.length() > 0)
        {
          StringTokenizer toker = new StringTokenizer(customNames, ",;");
          List<String> propList = new ArrayList<String>();
          while (toker.hasMoreTokens())
          {
            String custom = toker.nextToken();
            if (Arrays.binarySearch(MediaFormat.DB_META_PROPS, custom) < 0)
              propList.add(custom);
          }
          retainedMetadataProperties = propList.toArray(Pooler.EMPTY_STRING_ARRAY);
          Arrays.sort(retainedMetadataProperties);
        }
        else
          retainedMetadataProperties = Pooler.EMPTY_STRING_ARRAY;
      }
      if (retainedMetadataProperties.length == 0)
        fileFormat.clearMetadata();
      else
        fileFormat.clearMetadataExceptFor(retainedMetadataProperties);
    }
  }

  public String getMetadataProperty(String name)
  {
    if (name == null) return null;
    ContainerFormat cf = getFileFormat();
    if (cf == null) return null;
    String rv = cf.getMetadataProperty(name);
    if (rv != null && rv.length() > 0)
      return rv;
    String lcName = name.toLowerCase();
    // Check for the special case properties we can figure out here
    if (lcName.startsWith("format.") && fileFormat != null)
    {
      lcName = lcName.substring(7);
      if (lcName.startsWith("video."))
      {
        lcName = lcName.substring(6);
        if (lcName.equals("codec"))
          return fileFormat.getPrimaryVideoFormat();
        else
        {
          VideoFormat vf = fileFormat.getVideoFormat();
          if (vf != null)
          {
            if (lcName.equals("resolution"))
            {
              String prettyRez = vf.getPrettyResolution();
              if (prettyRez != null)
                return prettyRez;
              else
                return vf.getWidth() + "x" + vf.getHeight() + (vf.isInterlaced() ? "i" : "");
            }
            else if (lcName.equals("aspect"))
            {
              int arNum = vf.getArNum();
              int arDen = vf.getArDen();
              if (arNum > 0 && arDen > 0)
              {
                if (arNum == arDen)
                  return "1:1";
                else
                {
                  return arNum + ":" + arDen;
                }
              }
            }
            else if (lcName.equals("bitrate"))
            {
              return (vf.getBitrate() / (1024*1024)) + " Mbps";
            }
            else if (lcName.equals("height"))
              return Integer.toString(vf.getHeight());
            else if (lcName.equals("width"))
              return Integer.toString(vf.getWidth());
            else if (lcName.equals("fps"))
              return Float.toString(vf.getFps());
            else if (lcName.equals("interlaced"))
              return Boolean.toString(vf.isInterlaced());
            else if (lcName.equals("progressive"))
              return Boolean.toString(!vf.isInterlaced());
            else if (lcName.equals("index"))
              return Integer.toString(vf.getOrderIndex());
            else if (lcName.equals("id"))
              return vf.getId();
          }
        }
      }
      else if (lcName.startsWith("audio."))
      {
        lcName = lcName.substring(6);
        if (lcName.equals("numstreams"))
          return Integer.toString(fileFormat.getNumAudioStreams());
        int streamIdx = 0;
        int idx = lcName.indexOf('.');
        if (idx != -1)
        {
          try
          {
            streamIdx = Integer.parseInt(lcName.substring(0, idx));
            lcName = lcName.substring(idx + 1);
          }
          catch (NumberFormatException nfe)
          {
            if (Sage.DBG) System.out.println("Error parsing audio stream index from:" + lcName);
          }
        }
        AudioFormat af = (streamIdx == 0) ? fileFormat.getAudioFormat() :
          fileFormat.getAudioFormats()[streamIdx];
        if (lcName.equals("codec"))
          return af.getFormatName();
        if (af != null)
        {
          if (lcName.equals("channels"))
            return Integer.toString(af.getChannels());
          else if (lcName.equals("language"))
            return af.getLanguage();
          else if (lcName.equals("samplerate"))
            return Integer.toString(af.getSamplingRate());
          else if (lcName.equals("bitspersample"))
            return Integer.toString(af.getBitsPerSample());
          else if (lcName.equals("bitrate"))
            return Integer.toString(af.getBitrate()/1024) + " kbps";
          else if (lcName.equals("index"))
            return Integer.toString(af.getOrderIndex());
          else if (lcName.equals("id"))
            return af.getId();
        }
      }
      else if (lcName.startsWith("subtitle."))
      {
        lcName = lcName.substring(9);
        if (lcName.equals("numstreams"))
          return Integer.toString(fileFormat.getNumSubpictureStreams());
        int streamIdx = 0;
        int idx = lcName.indexOf('.');
        if (idx != -1)
        {
          try
          {
            streamIdx = Integer.parseInt(lcName.substring(0, idx));
            lcName = lcName.substring(idx + 1);
          }
          catch (NumberFormatException nfe)
          {
            if (Sage.DBG) System.out.println("Error parsing subtitle stream index from:" + lcName);
          }
        }
        SubpictureFormat[] sf = fileFormat.getSubpictureFormats();
        if (sf != null && sf.length > 0)
        {
          if (lcName.equals("language"))
            return sf[streamIdx].getLanguage();
          else if (lcName.equals("codec"))
            return sf[streamIdx].getFormatName();
          else if (lcName.equals("index"))
            return Integer.toString(sf[streamIdx].getOrderIndex());
          else if (lcName.equals("id"))
            return sf[streamIdx].getId();
        }
      }
      else if (lcName.equals("container"))
        return isBluRay() ? "BDMV" : fileFormat.getFormatName();
    }
    else if (isPicture() && lcName.equals("picture.resolution"))
    {
      // Check if its in the EXIF description
      Show s = getShow();
      if (s != null)
      {
        String desc = s.getDesc();
        int idx = desc.indexOf("Dimensions:");
        if (idx != -1)
        {
          int idx2 = desc.indexOf('\n', idx);
          if (idx2 == -1)
            return desc.substring(idx + 12).trim();
          else
            return desc.substring(idx + 12, idx2).trim();
        }
      }
      // Don't force an image load in order to get this information...that could cause lots of UI delays
      MetaImage mi = MetaImage.getMetaImageNoLoad(this);
      if (mi != null)
        return mi.getWidth() + "x" + mi.getHeight();
      else
        return "";
    }
    else if (name.equals(MediaFormat.META_TRACK) && isMusic())
    {
      return Integer.toString(getContentAiring().getTrack());
    }
    else if (name.equals(MediaFormat.META_EXTERNAL_ID))
    {
      Show currShow = getContentAiring().getShow();
      return (currShow != null) ? currShow.getExternalID() : "";
    }
    else if ((name.equals(MediaFormat.META_TITLE) && isMusic()) ||
        (name.equals(MediaFormat.META_EPISODE_NAME) && !isMusic()))
    {
      Show currShow = getContentAiring().getShow();
      return (currShow != null) ? currShow.getEpisodeName() : "";
    }
    else if ((name.equals(MediaFormat.META_ALBUM) && isMusic()) ||
        (name.equals(MediaFormat.META_TITLE) && !isMusic()))
    {
      return getContentAiring().getTitle();
    }
    else if (name.equals(MediaFormat.META_GENRE))
    {
      Show currShow = getContentAiring().getShow();
      if (currShow == null || currShow.categories.length == 0)
        return "";
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < currShow.categories.length; i++)
      {
        if (i != 0)
          sb.append('/');
        sb.append(currShow.categories[i].name);
      }
      return sb.toString();
    }
    else if (name.equals(MediaFormat.META_DESCRIPTION))
    {
      Show currShow = getContentAiring().getShow();
      return currShow != null ? currShow.getDesc() : "";
    }
    else if (name.equals(MediaFormat.META_YEAR))
    {
      Show currShow = getContentAiring().getShow();
      return currShow != null ? currShow.getYear() : "";
    }
    else if (name.equals(MediaFormat.META_LANGUAGE))
    {
      Show currShow = getContentAiring().getShow();
      return currShow != null ? currShow.getLanguage() : "";
    }
    else if (name.equals(MediaFormat.META_RATED))
    {
      Show currShow = getContentAiring().getShow();
      return currShow != null ? currShow.getRated() : "";
    }
    else if (name.equals(MediaFormat.META_RUNNING_TIME))
    {
      Show currShow = getContentAiring().getShow();
      return currShow != null ? Long.toString(currShow.getDuration()) : "";
    }
    else if (name.equals(MediaFormat.META_ORIGINAL_AIR_DATE))
    {
      Show currShow = getContentAiring().getShow();
      return currShow != null ? Long.toString(currShow.getOriginalAirDate()) : "";
    }
    else if (name.equals(MediaFormat.META_EXTENDED_RATINGS))
    {
      Show currShow = getContentAiring().getShow();
      return currShow != null ? currShow.getExpandedRatingsString() : "";
    }
    else if (name.equals(MediaFormat.META_MISC))
    {
      Show currShow = getContentAiring().getShow();
      return currShow != null ? currShow.getBonusesString() : "";
    }
    else if (name.equals(MediaFormat.META_PART_NUMBER))
    {
      return Integer.toString(getContentAiring().getPartNum());
    }
    else if (name.equals(MediaFormat.META_TOTAL_PARTS))
    {
      return Integer.toString(getContentAiring().getTotalParts());
    }
    else if (name.equals(MediaFormat.META_HDTV))
    {
      return getContentAiring().isHDTV() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_CC))
    {
      return getContentAiring().isCC() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_STEREO))
    {
      return getContentAiring().isStereo() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_SUBTITLED))
    {
      return getContentAiring().isSubtitled() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_PREMIERE))
    {
      return getContentAiring().isPremiere() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_SEASON_PREMIERE))
    {
      return getContentAiring().isSeasonPremiere() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_SERIES_PREMIERE))
    {
      return getContentAiring().isSeriesPremiere() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_CHANNEL_PREMIERE))
    {
      return getContentAiring().isChannelPremiere() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_SEASON_FINALE))
    {
      return getContentAiring().isSeasonFinale() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_SERIES_FINALE))
    {
      return getContentAiring().isSeriesFinale() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_SAP))
    {
      return getContentAiring().isSAP() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_3D))
    {
      return getContentAiring().is3D() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_DD51))
    {
      return getContentAiring().isDD51() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_DOLBY))
    {
      return getContentAiring().isDolby() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_LETTERBOX))
    {
      return getContentAiring().isLetterbox() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_LIVE))
    {
      return getContentAiring().isLive() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_NEW))
    {
      return getContentAiring().isNew() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_WIDESCREEN))
    {
      return getContentAiring().isWidescreen() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_SURROUND))
    {
      return getContentAiring().isSurround() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_DUBBED))
    {
      return getContentAiring().isDubbed() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_TAPED))
    {
      return getContentAiring().isTaped() ? "true" : "false";
    }
    else if (name.equals(MediaFormat.META_PARENTAL_RATING))
    {
      return getContentAiring().getParentalRating();
    }
    else if (name.equals(MediaFormat.META_SEASON_NUMBER))
    {
      Show currShow = getContentAiring().getShow();
      return currShow != null ? Integer.toString(currShow.getSeasonNumber()) : "0";
    }
    else if (name.equals(MediaFormat.META_EPISODE_NUMBER))
    {
      Show currShow = getContentAiring().getShow();
      return currShow != null ? Integer.toString(currShow.getEpisodeNumber()) : "0";
    }
    else
    {
      // Check through all the role names to see if any of them match
      byte roleB = 0;
      for (int i = 1; i < Show.ROLE_NAMES.length; i++)
      {
        if (name.equals(Show.ROLE_NAMES[i]))
        {
          roleB = (byte) i;
          break;
        }
      }
      if (roleB > 0)
      {
        Show currShow = getContentAiring().getShow();
        return (currShow != null) ? currShow.getPeopleString(roleB) : "";
      }
    }
    return null;
  }

  public void addMetadata(String name, String value)
  {
    if (name == null || name.length() == 0) return;
    if (value == null) value = "";
    // Check for special properties that are stored in an Airing/Show object
    // Airing objects can't be updated directly unless they have a non-zero station ID; so just always add a new one instead and use the MF method to relink it
    // Show objects CAN be updated directly so just do those on the DB
    boolean usedDBProperty = false;
    if (name.equals(MediaFormat.META_TRACK) && isMusic())
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      byte track = (byte)(Integer.parseInt(value) & 0xFF);
      if (track != currAir.getTrack())
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, track, currAir.miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_EXTERNAL_ID))
    {
      // This one will end up building a new Show object; so we need to relink it with the MediaFile as part of this.
      Show currShow = getContentAiring().getShow();
      if (!currShow.getExternalID().equals(value) && value.length() > 0)
        setShow(Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), value, currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(), currShow.seasonNum, currShow.episodeNum,
            currShow.altEpisodeNum,  currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs));
      else
        return;
      usedDBProperty = true;
    }
    else if ((name.equals(MediaFormat.META_TITLE) && isMusic()) ||
        (name.equals(MediaFormat.META_EPISODE_NAME) && !isMusic()))
    {
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (!value.equals(currShow.getEpisodeName()))
        Wizard.getInstance().addShow(currShow.getTitle(), value, currShow.getDesc(),
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if ((name.equals(MediaFormat.META_ALBUM) && isMusic()) ||
        (name.equals(MediaFormat.META_TITLE) && !isMusic()))
    {
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (!value.equals(currShow.getTitle()))
        Wizard.getInstance().addShow(value, currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_GENRE) ||
        name.equals(MediaFormat.META_GENRE_ID))
    {
      if (name.equals(MediaFormat.META_GENRE_ID))
      {
        try
        {
          int genreID = Integer.parseInt(fileFormat.getMetadataProperty(MediaFormat.META_GENRE_ID));
          if (genreID >= 0 && genreID < ID3Parser.ID3V1_GENRES.length)
            value = ID3Parser.ID3V1_GENRES[genreID];
        }
        catch (Exception e)
        {
          System.out.println("ERROR parsing genre ID of:" + e);
          return;
        }
      }
      StringTokenizer toker = new StringTokenizer(value, ";/");
      String[] categories = new String[toker.countTokens()];
      int catIndex = 0;
      while (toker.hasMoreTokens())
        categories[catIndex++] = toker.nextToken().trim();
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (!Arrays.equals(categories, currShow.getCategories()))
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, categories, currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_DESCRIPTION))
    {
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (!value.equals(currShow.getDesc()))
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), value,
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_YEAR))
    {
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (!value.equals(currShow.getYear()))
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), value, currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_LANGUAGE))
    {
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (!value.equals(currShow.getLanguage()))
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), value, currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_RATED))
    {
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (!value.equals(currShow.getRated()))
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, value, currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_RUNNING_TIME))
    {
      long runtime;
      try
      {
        runtime = Long.parseLong(value);
      }
      catch (Exception e)
      {
        if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
        return;
      }
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (runtime != currShow.getDuration())
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            runtime, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_SEASON_NUMBER))
    {
      short seasonNumber;
      try
      {
        seasonNumber = Short.parseShort(value);
      }
      catch (Exception e)
      {
        if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
        return;
      }
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (seasonNumber != currShow.getSeasonNumber())
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            seasonNumber, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_EPISODE_NUMBER))
    {
      short episodeNumber;
      try
      {
        episodeNumber = Short.parseShort(value);
      }
      catch (Exception e)
      {
        if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
        return;
      }
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (episodeNumber != currShow.getEpisodeNumber())
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, episodeNumber, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_ORIGINAL_AIR_DATE))
    {
      long orgAirDate;
      try
      {
        orgAirDate = Long.parseLong(value);
      }
      catch (Exception e)
      {
        if (Sage.DBG) System.out.println("ERROR parsing metadata number of:" + e);
        return;
      }
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (orgAirDate != currShow.getOriginalAirDate())
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), orgAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_EXTENDED_RATINGS))
    {
      StringTokenizer toker = new StringTokenizer(value, ";,");
      String[] ers = new String[toker.countTokens()];
      for (int i = 0; i < ers.length; i++)
        ers[i] = toker.nextToken().trim();
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (!Arrays.equals(ers, currShow.getExpandedRatings()))
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), ers, currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_MISC))
    {
      StringTokenizer toker = new StringTokenizer(value, ";,");
      String[] misc = new String[toker.countTokens()];
      for (int i = 0; i < misc.length; i++)
        misc[i] = toker.nextToken().trim();
      // Update the Show object
      Show currShow = getContentAiring().getShow();
      if (!Arrays.equals(misc, currShow.getBonuses()))
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, currShow.getCategories(), currShow.people,
            currShow.roles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            misc, currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_PART_NUMBER))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      byte partB = (byte)((((Integer.parseInt(value) & 0xF) << 4) | (currAir.partsB & 0xF)) & 0xFF);
      if (partB != currAir.getPartNum())
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, partB, currAir.miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_TOTAL_PARTS))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      byte partB = (byte)(((Integer.parseInt(value) & 0xF) | (currAir.partsB & 0xF0)) & 0xFF);
      if (partB != currAir.getTotalParts())
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, partB, currAir.miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_HDTV))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.HDTV_MASK);
      else
        miscB = (miscB & ~Airing.HDTV_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_CC))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.CC_MASK);
      else
        miscB = (miscB & ~Airing.CC_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_STEREO))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.STEREO_MASK);
      else
        miscB = (miscB & ~Airing.STEREO_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_SUBTITLED))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.SUBTITLE_MASK);
      else
        miscB = (miscB & ~Airing.SUBTITLE_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_PREMIERE))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.PREMIERE_MASK);
      else
        miscB = (miscB & ~Airing.PREMIERE_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_SEASON_PREMIERE))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.SEASON_PREMIERE_MASK);
      else
        miscB = (miscB & ~Airing.SEASON_PREMIERE_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_SERIES_PREMIERE))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.SERIES_PREMIERE_MASK);
      else
        miscB = (miscB & ~Airing.SERIES_PREMIERE_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_CHANNEL_PREMIERE))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.CHANNEL_PREMIERE_MASK);
      else
        miscB = (miscB & ~Airing.CHANNEL_PREMIERE_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_SEASON_FINALE))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.SEASON_FINALE_MASK);
      else
        miscB = (miscB & ~Airing.SEASON_FINALE_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_SERIES_FINALE))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.SERIES_FINALE_MASK);
      else
        miscB = (miscB & ~Airing.SERIES_FINALE_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_SAP))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.SAP_MASK);
      else
        miscB = (miscB & ~Airing.SAP_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_3D))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.THREED_MASK);
      else
        miscB = (miscB & ~Airing.THREED_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_DD51))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.DD51_MASK);
      else
        miscB = (miscB & ~Airing.DD51_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_DOLBY))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.DOLBY_MASK);
      else
        miscB = (miscB & ~Airing.DOLBY_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_LETTERBOX))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.LETTERBOX_MASK);
      else
        miscB = (miscB & ~Airing.LETTERBOX_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_LIVE))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.LIVE_MASK);
      else
        miscB = (miscB & ~Airing.LIVE_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_NEW))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.NEW_MASK);
      else
        miscB = (miscB & ~Airing.NEW_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_WIDESCREEN))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.WIDESCREEN_MASK);
      else
        miscB = (miscB & ~Airing.WIDESCREEN_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_SURROUND))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.SURROUND_MASK);
      else
        miscB = (miscB & ~Airing.SURROUND_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_DUBBED))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.DUBBED_MASK);
      else
        miscB = (miscB & ~Airing.DUBBED_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_TAPED))
    {
      // Update the airing object
      Airing currAir = getContentAiring();
      int miscB = currAir.miscB;
      if (Catbert.evalBool(value))
        miscB = (miscB | Airing.TAPE_MASK);
      else
        miscB = (miscB & ~Airing.TAPE_MASK);
      if (miscB != currAir.miscB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, miscB, currAir.prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else if (name.equals(MediaFormat.META_PARENTAL_RATING))
    {
      byte prB = 0;
      for (int i = 1; i < Airing.PR_NAMES.length; i++)
        if (value.equals(Airing.PR_NAMES[i]))
        {
          prB = (byte) i;
          break;
        }
      // Update the airing object
      Airing currAir = getContentAiring();
      if (prB != currAir.prB)
        setInfoAiring(Wizard.getInstance().addAiring(currAir.getShow(), currAir.stationID,
            currAir.time, currAir.duration, currAir.partsB, currAir.miscB, prB, currAir.getMediaMask()));
      else
        return;
      usedDBProperty = true;
    }
    else
    {
      // Check through all the role names to see if any of them match
      byte roleB = 0;
      for (int i = 1; i < Show.ROLE_NAMES.length; i++)
      {
        if (name.equals(Show.ROLE_NAMES[i]))
        {
          roleB = (byte) i;
          break;
        }
      }
      if (roleB > 0)
      {
        // Update the Show object
        // For roles, we need to maintain all the other existing ones and then remove anything that uses the same role specified here
        Show currShow = getContentAiring().getShow();
        List<Person> peeps = new ArrayList<Person>();
        List<Byte> roles = new ArrayList<Byte>();
        for (int i = 0; i < currShow.roles.length; i++)
        {
          if (currShow.roles[i] != roleB)
          {
            peeps.add(currShow.people[i]);
            roles.add(new Byte(currShow.roles[i]));
          }
        }
        if ("AC/DC".equals(value))
        {
          peeps.add(Wizard.getInstance().getPersonForName(value));
          roles.add(new Byte(roleB));
        }
        else
        {
          StringTokenizer toker = new StringTokenizer(value, ";/");
          while (toker.hasMoreTokens())
          {
            peeps.add(Wizard.getInstance().getPersonForName(toker.nextToken().trim()));
            roles.add(new Byte(roleB));
          }
        }
        Person[] myPeeps = peeps.toArray(Pooler.EMPTY_PERSON_ARRAY);
        byte[] myRoles = new byte[roles.size()];
        for (int i = 0; i < myRoles.length; i++)
          myRoles[i] = roles.get(i).byteValue();
        Wizard.getInstance().addShow(currShow.getTitle(), currShow.getEpisodeName(), currShow.getDesc(),
            currShow.duration, currShow.getCategories(), myPeeps,
            myRoles, currShow.getRated(), currShow.getExpandedRatings(), currShow.getYear(), currShow.getParentalRating(),
            currShow.getBonuses(), currShow.getExternalID(), currShow.getLanguage(), currShow.originalAirDate, currShow.getMediaMask(),
            currShow.seasonNum, currShow.episodeNum, currShow.altEpisodeNum,
            currShow.cachedUnique == Show.FORCED_UNIQUE, currShow.showcardID, currShow.seriesID, currShow.imageIDs);

        usedDBProperty = true;
      }
    }
    // NOTE: We better still update the external .properties file or we'll lose this data on a format re-detection which would be very bad
    if (fileFormat != null)
    {
      if (!usedDBProperty)
        fileFormat.addMetadata(name, value);
      // We also need to export this to the .properties file so we don't lose it upon reindex and also update
      // the custom_metadata_properties info as well so that it tracks this one.
      File metaFile = getMetadataPropertiesFile();
      if (metaFile != null && Sage.getBoolean("update_external_properties_files_on_metadata_changes", true))
      {
        BufferedWriter metaOut = null;
        try
        {
          metaOut = new BufferedWriter(new FileWriter(metaFile, true));
          metaOut.newLine(); // to be sure we're on a new line
          SageProperties.savePair(name, value, metaOut);
        }
        catch (IOException e)
        {
          if (Sage.DBG) System.out.println("ERROR creating .properties file after setting metadata of:" + e + " filepath=" + metaFile);
        }
        finally
        {
          if (metaOut != null)
          {
            try
            {
              metaOut.close();
            }
            catch (Exception e){}
          }
        }
      }
      if (!usedDBProperty && (retainedMetadataProperties == null || retainedMetadataProperties.length == 0 || Arrays.binarySearch(retainedMetadataProperties, name) < 0))
      {
        // property is not retained yet; add it into the list
        StringBuffer sb = new StringBuffer(name);
        sb.append(';');
        for (int i = 0; retainedMetadataProperties != null && i < retainedMetadataProperties.length; i++)
        {
          sb.append(retainedMetadataProperties[i]);
          sb.append(';');
        }
        Sage.put("custom_metadata_properties", sb.toString());
        // This'll cause the cached array to be reloaded
        retainedMetadataProperties = null;
        optimizeMetadataStorage();
      }
      if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
      {
        Wizard wiz = Wizard.getInstance();
        try {
          wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
          wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
        } finally {
          wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
        }
      }
    }
  }

  public void saveMetadataPropertiesFile()
  {
    File propFile = getMetadataPropertiesFile();
    Properties props = getMetadataProperties();
    OutputStream os = null;
    try
    {
      os = new BufferedOutputStream(new FileOutputStream(propFile));
      props.store(os, "SageTV Recording Metadata");
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("WARNING: Unable to write out metadata properties file to: " + propFile + " of:" + e);
    }
    finally
    {
      if (os != null)
      {
        try
        {
          os.close();
        }
        catch (Exception e){}
      }
    }
  }

  public Properties getMetadataProperties()
  {
    Properties rv = new Properties();
    Airing a = getContentAiring();
    Show s = a.getShow();
    if (s == null)
      return rv;
    if (isMusic())
    {
      rv.put(MediaFormat.META_TRACK, Integer.toString(a.getTrack()));
      rv.put(MediaFormat.META_TITLE, s.getEpisodeName());
      rv.put(MediaFormat.META_ALBUM, s.getTitle());
    }
    else
    {
      rv.put(MediaFormat.META_EPISODE_NAME, s.getEpisodeName());
      rv.put(MediaFormat.META_TITLE, s.getTitle());
    }
    rv.put(MediaFormat.META_EXTERNAL_ID, s.getExternalID());
    rv.put(MediaFormat.META_GENRE, s.getCategoriesString(";"));
    rv.put(MediaFormat.META_DESCRIPTION, s.getDesc());
    rv.put(MediaFormat.META_YEAR, s.getYear());
    rv.put(MediaFormat.META_LANGUAGE, s.getLanguage());

    if (isTV())
    {
      rv.put(MediaFormat.META_AIRING_DURATION, Long.toString(a.getDuration()));
      rv.put(MediaFormat.META_AIRING_TIME, Long.toString(a.getStartTime()));
    }

    if (!isMusic())
    {
      rv.put(MediaFormat.META_RATED, s.getRated());
      rv.put(MediaFormat.META_RUNNING_TIME, Long.toString(s.getDuration()));
      rv.put(MediaFormat.META_ORIGINAL_AIR_DATE, Long.toString(s.getOriginalAirDate()));
      rv.put(MediaFormat.META_EXTENDED_RATINGS, s.getExpandedRatingsString());
      rv.put(MediaFormat.META_MISC, s.getBonusesString());
      if (a.getTotalParts() > 1)
      {
        rv.put(MediaFormat.META_PART_NUMBER, Integer.toString(a.getPartNum()));
        rv.put(MediaFormat.META_TOTAL_PARTS, Integer.toString(a.getTotalParts()));
      }
      rv.put(MediaFormat.META_HDTV, a.isHDTV() ? "true" : "false");
      rv.put(MediaFormat.META_CC, a.isCC() ? "true" : "false");
      rv.put(MediaFormat.META_STEREO, a.isStereo() ? "true" : "false");
      rv.put(MediaFormat.META_SUBTITLED, a.isSubtitled() ? "true" : "false");
      rv.put(MediaFormat.META_PREMIERE, a.isPremiere() ? "true" : "false");
      rv.put(MediaFormat.META_SEASON_PREMIERE, a.isSeasonPremiere() ? "true" : "false");
      rv.put(MediaFormat.META_SERIES_PREMIERE, a.isSeriesPremiere() ? "true" : "false");
      rv.put(MediaFormat.META_CHANNEL_PREMIERE, a.isChannelPremiere() ? "true" : "false");
      rv.put(MediaFormat.META_SEASON_FINALE, a.isSeasonFinale() ? "true" : "false");
      rv.put(MediaFormat.META_SERIES_FINALE, a.isSeriesFinale() ? "true" : "false");
      rv.put(MediaFormat.META_SAP, a.isSAP() ? "true" : "false");
      rv.put(MediaFormat.META_3D, a.is3D() ? "true" : "false");
      rv.put(MediaFormat.META_DD51, a.isDD51() ? "true" : "false");
      rv.put(MediaFormat.META_DOLBY, a.isDolby() ? "true" : "false");
      rv.put(MediaFormat.META_LETTERBOX, a.isLetterbox() ? "true" : "false");
      rv.put(MediaFormat.META_LIVE, a.isLive() ? "true" : "false");
      rv.put(MediaFormat.META_NEW, a.isNew() ? "true" : "false");
      rv.put(MediaFormat.META_WIDESCREEN, a.isWidescreen() ? "true" : "false");
      rv.put(MediaFormat.META_SURROUND, a.isSurround() ? "true" : "false");
      rv.put(MediaFormat.META_DUBBED, a.isDubbed() ? "true" : "false");
      rv.put(MediaFormat.META_TAPED, a.isTaped() ? "true" : "false");
      rv.put(MediaFormat.META_PARENTAL_RATING, a.getParentalRating());
      rv.put(MediaFormat.META_SEASON_NUMBER, Integer.toString(s.getSeasonNumber()));
      rv.put(MediaFormat.META_EPISODE_NUMBER, Integer.toString(s.getEpisodeNumber()));
      rv.put(MediaFormat.META_ALT_EPISODE_NUMBER, Integer.toString(s.getAltEpisodeNumber()));
      rv.put(Show.getRoleString(Show.ACTOR_ROLE), s.getPeopleString(Show.ACTOR_ROLE, ";"));
      rv.put(Show.getRoleString(Show.GUEST_ROLE), s.getPeopleString(Show.GUEST_ROLE, ";"));
      rv.put(Show.getRoleString(Show.GUEST_STAR_ROLE), s.getPeopleString(Show.GUEST_STAR_ROLE, ";"));
      rv.put(Show.getRoleString(Show.DIRECTOR_ROLE), s.getPeopleString(Show.DIRECTOR_ROLE, ";"));
      rv.put(Show.getRoleString(Show.PRODUCER_ROLE), s.getPeopleString(Show.PRODUCER_ROLE, ";"));
      rv.put(Show.getRoleString(Show.WRITER_ROLE), s.getPeopleString(Show.WRITER_ROLE, ";"));
      rv.put(Show.getRoleString(Show.CHOREOGRAPHER_ROLE), s.getPeopleString(Show.CHOREOGRAPHER_ROLE, ";"));
      rv.put(Show.getRoleString(Show.HOST_ROLE), s.getPeopleString(Show.HOST_ROLE, ";"));
      rv.put(Show.getRoleString(Show.EXECUTIVE_PRODUCER_ROLE), s.getPeopleString(Show.EXECUTIVE_PRODUCER_ROLE, ";"));
      rv.put(Show.getRoleString(Show.COMPOSER_ROLE), s.getPeopleString(Show.COMPOSER_ROLE, ";"));
      rv.put(Show.getRoleString(Show.JUDGE_ROLE), s.getPeopleString(Show.JUDGE_ROLE, ";"));
      rv.put(Show.getRoleString(Show.NARRATOR_ROLE), s.getPeopleString(Show.NARRATOR_ROLE, ";"));
      rv.put(Show.getRoleString(Show.CONTESTANT_ROLE), s.getPeopleString(Show.CONTESTANT_ROLE, ";"));
      rv.put(Show.getRoleString(Show.CORRESPONDENT_ROLE), s.getPeopleString(Show.CORRESPONDENT_ROLE, ";"));
      rv.put(Show.getRoleString(Show.TEAM_ROLE), s.getPeopleString(Show.TEAM_ROLE, ";"));
      rv.put(Show.getRoleString(Show.GUEST_VOICE_ROLE), s.getPeopleString(Show.GUEST_VOICE_ROLE, ";"));
      rv.put(Show.getRoleString(Show.ANCHOR_ROLE), s.getPeopleString(Show.ANCHOR_ROLE, ";"));
      rv.put(Show.getRoleString(Show.VOICE_ROLE), s.getPeopleString(Show.VOICE_ROLE, ";"));
      rv.put(Show.getRoleString(Show.MUSICAL_GUEST_ROLE), s.getPeopleString(Show.MUSICAL_GUEST_ROLE, ";"));
    }
    else
    {
      rv.put(Show.getRoleString(Show.ALBUM_ARTIST_ROLE), s.getPeopleString(Show.ALBUM_ARTIST_ROLE, ";"));
      rv.put(Show.getRoleString(Show.ARTIST_ROLE), s.getPeopleString(Show.ARTIST_ROLE, ";"));
      rv.put(Show.getRoleString(Show.COMPOSER_ROLE), s.getPeopleString(Show.COMPOSER_ROLE, ";"));
    }
    getFileFormat();
    if (fileFormat != null && fileFormat.getMetadata() != null)
      rv.putAll(fileFormat.getMetadata());
    return rv;
  }

  private File getMetadataPropertiesFile()
  {
    File rawFile = getFile(0);
    if (rawFile == null) return null;
    File metaFile = null;
    if (isDVD())
    {
      if (rawFile.getName().equalsIgnoreCase(Seeker.DVD_VOLUME_SECRET))
      {
        File parentFile = rawFile.getParentFile();
        if (parentFile != null)
        {
          File parent2 = parentFile.getParentFile();
          if (parent2 != null)
            metaFile = new File(parent2, parentFile.getName() + ".properties");
        }
      }
      else if (rawFile.isDirectory())
      {
        File parentFile = rawFile.getParentFile();
        if (parentFile != null)
          metaFile = new File(parentFile, rawFile.getName() + ".properties");
      }
      else
        metaFile = new File(rawFile.getAbsolutePath() + ".properties");
    }
    else if (isBluRay())
    {
      if (rawFile.getName().equalsIgnoreCase(Seeker.BLURAY_VOLUME_SECRET))
      {
        File parentFile = rawFile.getParentFile();
        if (parentFile != null)
        {
          File parent2 = parentFile.getParentFile();
          if (parent2 != null)
            metaFile = new File(parent2, parentFile.getName() + ".properties");
        }
      }
      else if (rawFile.isDirectory())
      {
        File parentFile = rawFile.getParentFile();
        if (parentFile != null)
          metaFile = new File(parentFile, rawFile.getName() + ".properties");
      }
      else
        metaFile = new File(rawFile.getAbsolutePath() + ".properties");
    }
    else
    {
      metaFile = new File(rawFile.getAbsolutePath() + ".properties");
    }
    return metaFile;
  }

  private void checkForCorrespondingThumbFile(ContainerFormat detailedFileFormat)
  {
    if (files.size() > 0)
    {
      // NARFLEX: I think we want to clear this so it gets reset. This is why we have problems with thumbnail
      // refreshing in the libraries.
      if (detailedFileFormat == null && thumbnailFile != null && thumbnailSize > 0)
      {
        // We already have thumbnail information; if it's embedded info then leave it since we won't
        // have it in the file format....also skip thumbnails that come from within a BDMV folder structure.
        if (thumbnailFile.equals(files.firstElement()) || (isBluRay() && thumbnailFile.toString().startsWith(files.firstElement().toString())))
        {
          return;
        }
      }
      thumbnailFile = null;
      // Check the file format information first for it
      if (detailedFileFormat != null && detailedFileFormat.hasMetadata(ContainerFormat.META_THUMBNAIL_OFFSET) &&
          detailedFileFormat.hasMetadata(ContainerFormat.META_THUMBNAIL_SIZE))
      {
        try
        {
          thumbnailFile = files.firstElement();
          thumbnailOffset = Integer.parseInt(detailedFileFormat.getMetadataProperty(ContainerFormat.META_THUMBNAIL_OFFSET));
          thumbnailSize = Integer.parseInt(detailedFileFormat.getMetadataProperty(ContainerFormat.META_THUMBNAIL_SIZE));
          return;
        }
        catch (Exception e)
        {
          if (Sage.DBG) System.out.println("ERROR parsing thumbnail info of:" + e);
        }
      }
      if (detailedFileFormat != null && detailedFileFormat.hasMetadata(ContainerFormat.META_THUMBNAIL_FILE))
      {
        thumbnailFile = new File(detailedFileFormat.getMetadataProperty(ContainerFormat.META_THUMBNAIL_FILE));
        thumbnailSize = (int)thumbnailFile.length();
        return;
      }
      if (isPicture())
        return; // we don't check for matching files for images
      // Check for the same filename, but different extension
      File rawFile = files.firstElement();
      String testFilename = ((isDVD() && rawFile.getName().equalsIgnoreCase(Seeker.DVD_VOLUME_SECRET)) ||
        (isBluRay() && rawFile.getName().equalsIgnoreCase(Seeker.BLURAY_VOLUME_SECRET))) ?
          rawFile.getParentFile().toString() : rawFile.toString();
      if (testFilename.endsWith(File.separator))
        testFilename = testFilename.substring(0, testFilename.length() - 1);
      int lastDot = testFilename.lastIndexOf('.');
      String[] testExts = { ".jpg", ".jpeg", ".jpe", ".gif", ".png" };
      if (lastDot != -1 && !isDVD() && !isBluRay())
        testFilename = testFilename.substring(0, lastDot);
      StringBuffer sb = Pooler.getPooledSB();
      sb.setLength(0);
      sb.append(testFilename);
      for (int i = 0; i < testExts.length; i++)
      {
        if (i != 0)
          sb.setLength(testFilename.length());
        sb.append(testExts[i]);
        File f = new File(sb.toString());
        if (f.isFile())
        {
          thumbnailFile = f;
          break;
        }
      }
      if (thumbnailFile == null)
      {
        // Check for a folder image
        if (isDVD() || isBluRay())
        {
          sb.setLength(0);
          sb.append("folder");
          for (int i = 0; i < testExts.length; i++)
          {
            if (i != 0)
              sb.setLength(6);
            sb.append(testExts[i]);
            File f = new File(rawFile, sb.toString());
            if (f.isFile())
            {
              thumbnailFile = f;
              break;
            }
          }
        }
        if (thumbnailFile == null)
        {
          File baseParent = rawFile.getParentFile();
          sb.setLength(0);
          sb.append("folder");
          for (int i = 0; i < testExts.length; i++)
          {
            if (i != 0)
              sb.setLength(6);
            sb.append(testExts[i]);
            File f = new File(baseParent, sb.toString());
            if (f.isFile())
            {
              thumbnailFile = f;
              break;
            }
          }
        }
      }
      Pooler.returnPooledSB(sb);
      if (thumbnailFile != null)
      {
        thumbnailOffset = 0;
        thumbnailSize = (int)thumbnailFile.length();
      }
    }
  }

  public boolean reinitializeMetadata(boolean thorough, boolean forcedReindex, String namePrefix)
  {
    if (generalType == MEDIAFILE_IMPORTED && isLocalFile())
    {
      boolean reinit = false;
      if (times.length > 0 && files.size() > 0)
      {
        long lastModTime = times[1];
        File theFile = files.get(0);
        long currModTime = theFile.lastModified();
        if (currModTime != 0 && Math.abs(currModTime - lastModTime) > 2500)
        {
          reinit = true;
          if (Sage.DBG) System.out.println("Detected changed file timestamp old=" + Sage.df(lastModTime) + " curr=" + Sage.df(theFile.lastModified()));
        }
      }
      if (!reinit && thorough)
      {
        // If this changes anything, save it
        long oldSize = thumbnailSize;
        long oldOffset = thumbnailOffset;
        File oldFile = thumbnailFile;
        checkForCorrespondingThumbFile(null);
        if ((oldSize != thumbnailSize || oldOffset != thumbnailOffset ||
            (oldFile != thumbnailFile && (oldFile == null || !oldFile.equals(thumbnailFile)))) &&
            generalType != MEDIAFILE_LOCAL_PLAYBACK)
        {
          Wizard wiz = Wizard.getInstance();
          try {
            wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
            wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
          } finally {
            wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
          }
          if (Sage.DBG) System.out.println("Detected thumbnail change so re-import the metadata as well for " + this);
          reinit = true;
        }
      }
      if (reinit || forcedReindex)
      {
        if (Sage.DBG) System.out.println("Reimporting metadata for mediafile because it changed:" + this);
        thumbnailLoadState = 0; // reset this so we re-generate if needed for picture modifications
        // Detect the file format outside the mediafile lock
        ContainerFormat newFormat = FormatParser.getFileFormat(files.get(0));
        checkForFormatDebugChange(newFormat);
        if (newFormat == null)
          newFormat = fileFormat; // don't change it if we can't parse it
        //Preserve any watched information
        Wizard wiz = Wizard.getInstance();
        Watched watchy = wiz.getWatch(getContentAiring());
        try {
          wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
          File theFile = files.get(0);
          times = Pooler.EMPTY_LONG_ARRAY;
          files.clear();
          initialize(theFile, namePrefix, true, newFormat);
          if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
            wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
          // This'll affect the sorting of the secondary index so we need to re-sort this in the DB
          wiz.check(Wizard.MEDIAFILE_CODE);
        } finally {
          wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
        }
        if (watchy != null)
          wiz.addWatched(getContentAiring(), watchy.getWatchStart(), watchy.getWatchEnd(),
              watchy.getRealWatchStart(), watchy.getRealWatchEnd(), watchy.getTitleNum());
        // This should be true, but then it'll cause a DB maintenance operation which seems
        // like way too big a side effect.
        // NARFLEX - 8/10/10 - We no longer look at the return value from this method; so it's not going to
        // cause a DB maintenance operation anymore. But what we do want it to do is file a MediaFileImported event
        // since metadata may need to be updated for this.
        return true;
      }
    }
    else if (generalType == MEDIAFILE_TV)
    {
      // We just do format redetection here; don't screw with the DB!
      boolean reinit = false;
      if (times.length > 0 && files.size() > 0)
      {
        long lastModTime = times[1];
        File theFile = files.get(0);
        if (Math.abs(theFile.lastModified() - lastModTime) > 2500)
        {
          reinit = true;
        }
      }
      if (reinit || forcedReindex)
      {
        if (Sage.DBG) System.out.println("Redetecting format for TV file because it changed:" + this);
        // Detect the file format outside the mediafile lock
        ContainerFormat newFormat = FormatParser.getFileFormat(files.get(0));
        checkForFormatDebugChange(newFormat);
        if (newFormat != null)
        {
          fileFormat = newFormat;
          Wizard wiz = Wizard.getInstance();
          try {
            wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
            if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
              wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
            // This'll affect the sorting of the secondary index so we need to re-sort this in the DB
            wiz.check(Wizard.MEDIAFILE_CODE);
          } finally {
            wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
          }
        }
        // This should be true, but then it'll cause a DB maintenance operation which seems
        // like way too big a side effect.
        // NARFLEX - 8/10/10 - We no longer look at the return value from this method; so it's not going to
        // cause a DB maintenance operation anymore. But what we do want it to do is file a MediaFileImported event
        // since metadata may need to be updated for this.
        return true;
      }
    }
    return false;
  }

  private void checkForFormatDebugChange(ContainerFormat newFormat)
  {
    // Don't bother comparing DVDs or BluRays since FFMPEG isn't involved in that
    if (Sage.DBG && Sage.getBoolean("debug_file_format_changes", false) && fileFormat != null && !isDVD() && !isBluRay())
    {
      // NOTE: You also need to set this and reindex w/ the old transcoder first: OPTIMIZE_METADATA_MEM_USAGE=false
      boolean metaDataChanged = false;
      boolean debugMetaData = Sage.getBoolean("debug_metadata_changes", false);

      // Force checking metadata if debugging
      if (debugMetaData && newFormat != null) {
        metaDataChanged = !newFormat.compareMetadata(fileFormat.getMetadata());
      }

      String diffDetails = "";
      if (newFormat == null)
      {
        diffDetails += "NULL format for new type!";
      }
      else if (fileFormat == null)
      {
        diffDetails += "NULL format for old type!";
      }
      else
      {
        // If the old dur was zero, and it's not anymore, then that's good so ignore that change
        // NOTE: YOU MAY WANT TO REMOVE THIS ASF DURATION CHECK FOR NEXT TIME....but for now it seems they've corrected many of the ASF duration inaccuracies
        if (!MediaFormat.ASF.equals(fileFormat.getFormatName()) && fileFormat.getDuration() != 0 && newFormat.getDuration() != fileFormat.getDuration() &&
            (((float)Math.abs(newFormat.getDuration() - fileFormat.getDuration()))/newFormat.getDuration()) > 0.04f)
        {
          diffDetails += " OldDur=" + fileFormat.getDuration() + " NewDur=" + newFormat.getDuration();
        }
        if (newFormat.isDRMProtected() != fileFormat.isDRMProtected())
        {
          diffDetails += " OldDRM=" + fileFormat.isDRMProtected() + " NewDRM=" + newFormat.isDRMProtected();
        }
        if (!newFormat.getFormatName().equals(fileFormat.getFormatName()))
        {
          diffDetails += " OldContainer=" + fileFormat.getFormatName() + " NewContainer=" + newFormat.getFormatName();
        }
        if (metaDataChanged)
        {
          diffDetails += " MetadataChanged--details above";
        }
        if (newFormat.getNumberOfStreams() != fileFormat.getNumberOfStreams())
        {
          diffDetails += " #StreamsChanged";
        }
        else
        {
          // Check the individual streams
          VideoFormat[] oldVFs = fileFormat.getVideoFormats();
          VideoFormat[] newVFs = newFormat.getVideoFormats();
          if (oldVFs.length != newVFs.length)
          {
            diffDetails += " #VideoStreamsChanged";
          }
          else
          {
            for (int i = 0; i < oldVFs.length; i++)
            {
              if (!oldVFs[i].getFormatName().equalsIgnoreCase(newVFs[i].getFormatName()))
                diffDetails += " Video#" + i + "FormatOld=" + oldVFs[i].getFormatName() + " FormatNew=" + newVFs[i].getFormatName();
              // ignore bitrate changes here
              if (Math.abs(oldVFs[i].getFps() - newVFs[i].getFps()) > 0.011) // ignore rounding differences
                diffDetails += " Video#" + i + "FPSOld=" + oldVFs[i].getFps() + " FPSNew=" + newVFs[i].getFps();
              if (oldVFs[i].getAspectRatio() != newVFs[i].getAspectRatio())
                diffDetails += " Video#" + i + "AROld=" + oldVFs[i].getAspectRatio() + " ARNew=" + newVFs[i].getAspectRatio();
              if (oldVFs[i].isInterlaced() != newVFs[i].isInterlaced())
                diffDetails += " Video#" + i + "InterlacedOld=" + oldVFs[i].isInterlaced() + " InterlacedNew=" + newVFs[i].isInterlaced();
              if (oldVFs[i].getWidth() != newVFs[i].getWidth())
                diffDetails += " Video#" + i + "WidthOld=" + oldVFs[i].getWidth() + " WidthNew=" + newVFs[i].getWidth();
              if (oldVFs[i].getHeight() != newVFs[i].getHeight())
                diffDetails += " Video#" + i + "HeightOld=" + oldVFs[i].getHeight() + " HeightNew=" + newVFs[i].getHeight();
            }
          }
          AudioFormat[] oldAFs = fileFormat.getAudioFormats();
          AudioFormat[] newAFs = newFormat.getAudioFormats();
          if (oldAFs.length != newAFs.length)
          {
            diffDetails += " #AudioStreamsChanged";
          }
          else
          {
            for (int i = 0; i < oldAFs.length; i++)
            {
              if (!oldAFs[i].getFormatName().equalsIgnoreCase(newAFs[i].getFormatName()))
                diffDetails += " Audio#" + i + "FormatOld=" + oldAFs[i].getFormatName() + " FormatNew=" + newAFs[i].getFormatName();
              // ignore bitrate changes here
              if (oldAFs[i].getSamplingRate() != newAFs[i].getSamplingRate())
                diffDetails += " Audio#" + i + "SROld=" + oldAFs[i].getSamplingRate() + " SRNew=" + newAFs[i].getSamplingRate();
              if (oldAFs[i].getBitsPerSample() != newAFs[i].getBitsPerSample())
                diffDetails += " Audio#" + i + "BPSOld=" + oldAFs[i].getBitsPerSample() + " BPSNew=" + newAFs[i].getBitsPerSample();
              if (oldAFs[i].getChannels() != newAFs[i].getChannels())
                diffDetails += " Audio#" + i + "ChOld=" + oldAFs[i].getChannels() + " ChNew=" + newAFs[i].getChannels();
              if (oldAFs[i].getLanguage() != null && oldAFs[i].getLanguage().length() > 0 && !oldAFs[i].getLanguage().equals(newAFs[i].getLanguage()))
                diffDetails += " Audio#" + i + "LangOld=" + oldAFs[i].getLanguage() + " LangNew=" + newAFs[i].getLanguage();
              if (oldAFs[i].getId() != null && oldAFs[i].getId().length() > 0 && !oldAFs[i].getId().equals(newAFs[i].getId()))
                diffDetails += " Audio#" + i + "IDOld=" + oldAFs[i].getId() + " IDNew=" + newAFs[i].getId();
            }
          }
          SubpictureFormat[] oldSFs = fileFormat.getSubpictureFormats();
          SubpictureFormat[] newSFs = newFormat.getSubpictureFormats();
          if (oldSFs.length != newSFs.length)
          {
            diffDetails += " #SubpicStreamsChanged";
          }
          else
          {
            for (int i = 0; i < oldSFs.length; i++)
            {
              if (!oldSFs[i].getFormatName().equalsIgnoreCase(newSFs[i].getFormatName()))
                diffDetails += " Subpic#" + i + "FormatOld=" + oldSFs[i].getFormatName() + " FormatNew=" + newSFs[i].getFormatName();
              if (oldSFs[i].getLanguage() != null && oldSFs[i].getLanguage().length() > 0 && !oldSFs[i].getLanguage().equals(newSFs[i].getLanguage()))
                diffDetails += " Subpic#" + i + "LangOld=" + oldSFs[i].getLanguage() + " LangNew=" + newSFs[i].getLanguage();
              if (oldSFs[i].getId() != null && oldSFs[i].getId().length() > 0 && !oldSFs[i].getId().equals(newSFs[i].getId()))
                diffDetails += " Subpic#" + i + "IDOld=" + oldSFs[i].getId() + " IDNew=" + newSFs[i].getId();
            }
          }
        }
      }
      if (diffDetails.length() > 0)
      {
        System.out.println("ERROR ERROR ERROR: File formats do NOT match for:" + this);
        System.out.println("Details=" + diffDetails);
        System.out.println("oldFormat=" + fileFormat.getFullPropertyString());
        System.out.println("newFormat=" + (newFormat == null ? (String)null : newFormat.getFullPropertyString()));
      }
    }
  }

  public byte getAcquistionTech()
  {
    return acquisitionTech;
  }

  public void setAcquisitionTech(byte at)
  {
    if (acquisitionTech == at) return;
    Wizard wiz = Wizard.getInstance();
    try {
      wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
      acquisitionTech = at;
      if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
        wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
    }
  }

  public boolean isImported()
  {
    return generalType == MEDIAFILE_IMPORTED;
  }

  public boolean hasVideoContent()
  {
    return (isBluRay() || isDVD() || isVideo()) && !isAudioOnlyTVFile();//mediaType == MEDIATYPE_DVD || mediaType == MEDIATYPE_VIDEO/* || isAnyLiveStream()*/;
  }

  public boolean isLiveStream()
  {
    return generalType == MEDIAFILE_LIVE_STREAM;
  }

  public boolean isLiveBufferedStream()
  {
    return generalType == MEDIAFILE_LIVE_BUFFERED_STREAM;
  }

  public boolean isAnyLiveStream()
  {
    return generalType == MEDIAFILE_LIVE_STREAM || generalType == MEDIAFILE_LIVE_BUFFERED_STREAM;
  }

  public boolean isLiveNetworkStream()
  {
    if (!isLiveStream())
      return false;
    CaptureDevice capDev = guessCaptureDeviceFromEncoding();
    if (capDev != null)
      return capDev.isNetworkEncoder();
    return false;
  }

  public byte getGeneralType()
  {
    return generalType;
  }

  int getInfoAiringID()
  {
    return infoAiringID;
  }

  boolean validate()
  {
    if (getContentAiring() == null)
    {
      if (Sage.DBG) System.out.println("NULL Airing.");
      return false;
    }

    // Fix any season/episode numbers that were set in the format information since those would get cleared now.
    if (fileFormat != null)
    {
      Show s = getShow();
      if (s != null && fileFormat.hasMetadata(MediaFormat.META_SEASON_NUMBER))
      {
        String str = fileFormat.getMetadataProperty(MediaFormat.META_SEASON_NUMBER);
        if (str != null && str.length() > 0)
        {
          try
          {
            s.seasonNum = Short.parseShort(str);
          }
          catch (NumberFormatException nfe)
          {
            if (Sage.DBG) System.out.println("Bad formatting for season number of: " + nfe);
          }
        }
      }
      if (s != null && fileFormat.hasMetadata(MediaFormat.META_EPISODE_NUMBER))
      {
        String str = fileFormat.getMetadataProperty(MediaFormat.META_EPISODE_NUMBER);
        if (str != null && str.length() > 0)
        {
          try
          {
            s.episodeNum = Short.parseShort(str);
          }
          catch (NumberFormatException nfe)
          {
            if (Sage.DBG) System.out.println("Bad formatting for episode number of: " + nfe);
          }
        }
      }
    }
    if (!Sage.client && OPTIMIZE_METADATA_MEM_USAGE)
    {
      optimizeMetadataStorage();
    }

    // Times can be incomplete initially, it's on the update they become clean.
    // Therefore we can't fail a validation in that condition.
    if (Wizard.GENERATE_MEDIA_MASK)
    {
      // Propogate our media mask information down the DB
      // NOTE: We don't have to log the Airing object's update here because it'll
      // get done when the DB file is saved after the load is complete since it's a DB version change
      Airing air = getContentAiring();
      air.setMediaMask(getMediaMask());
      Show s = air.getShow();
      if (s != null && !Wizard.getInstance().isNoShow(s))
        s.addMediaMaskRecursive(getMediaMask());
    }
    if (isTV())
    {
      // Make sure we have persistence enabled for the Airing linked to this MediaFile in case
      // it's needed for recovery in the future
      Airing myAir = getContentAiring();
      myAir.setPersist(Airing.PERSIST_TV_MEDIAFILE_LINK);
    }

    // Fix the title/episode for imported picture and video files
    if (!isMusic() && !isTV())
    {
      Show s = getShow();
      if (s != null && !s.getTitle().equals(s.getEpisodeName()))
      {
        int lastTitSlash = s.getTitle().lastIndexOf('/');
        String titSub = (lastTitSlash == -1) ? s.getTitle() : s.getTitle().substring(0, lastTitSlash);
        int lastNameSlash = name.lastIndexOf('/');
        String nameSub = (lastNameSlash == -1) ? name : name.substring(0, lastNameSlash);
        if (s.getTitle().startsWith(nameSub) && (s.getTitle().endsWith(s.getEpisodeName()) || titSub.endsWith(s.getEpisodeName())))
        {
          s.title = Wizard.getInstance().getTitleForName(s.getEpisodeName(), getMediaMask());
        }
      }
    }

    return true;
  }

  public synchronized void setHostname(String newHost)
  {
    Wizard wiz = Wizard.getInstance();
    try {
      wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
      host = newHost;
      if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
        wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
    }
  }

  // Returns either IP address/hostname of the server, the URL if it's a streamed file or null if the file is local
  public String getServerName()
  {
    if (host != null && host.indexOf("://") != -1 && !host.startsWith("smb://"))
    {
      // This is a streaming file URL
      return host;
    }
    if (Sage.client)
    {
      // IMPORTANT: We can't play them back locally if they're recording because the async filter makes
      // calls to the MMC directly which won't be there, and there's also permissions issues!
      if ((Sage.getBoolean("optimize_local_client_playback", false) && !isRecording() &&
          ("localhost".equals(Sage.preferredServer) || "127.0.0.1".equals(Sage.preferredServer))) ||
          (generalType == MediaFile.MEDIAFILE_LOCAL_PLAYBACK &&
          (SageTV.hostname.equals(host) || host.startsWith("smb://"))))
        return null;
      else if (Sage.get("alternate_media_server", "").length() > 0)
        return Sage.get("alternate_media_server", "");
      else
        return Sage.preferredServer;
    }
    else if (Sage.get("alternate_media_server", "").length() > 0 && (generalType != MediaFile.MEDIAFILE_LOCAL_PLAYBACK ||
        (!SageTV.hostname.equals(host) && !host.startsWith("smb://"))))
      return Sage.get("alternate_media_server", "");
    return null;
  }

  public Object clone()
  {
    // Otherwise the update can clear them out and screw us up.
    MediaFile myClone = (MediaFile) super.clone();
    myClone.files = new Vector<File>(files);
    myClone.times = times.clone();
    return myClone;
  }

  synchronized void update(DBObject fromMe)
  {
    MediaFile mf = (MediaFile) fromMe;
    files.removeAllElements();
    files.addAll(mf.files);
    // Check for modification time change on a picture file and clear it from the cache
    // in that case. This happens when pictures are rotated/flipped.
    long oldStart = (times.length > 0 ? times[0] : 0);
    times = mf.times.clone();
    long newStart = (times.length > 0 ? times[0] : 0);
    if (oldStart != newStart && isPicture() && Sage.client)
    {
      if (Sage.DBG) System.out.println("Clearing MetaImage from cache due to picture file update:" + this);
      MetaImage.clearFromCache(this);
    }
    archive = mf.archive;
    if (infoAiringID != mf.infoAiringID)
      myAiring = null;
    infoAiringID = mf.infoAiringID;
    videoDirectory = new String(mf.videoDirectory);
    host = mf.host;
    encodedBy = mf.encodedBy;
    //mediaType = mf.mediaType;
    //mediaSubtype = mf.mediaSubtype;
    generalType = mf.generalType;
    acquisitionTech = mf.acquisitionTech;
    forcedComplete = mf.forcedComplete;
    thumbnailFile = mf.thumbnailFile;
    thumbnailOffset = mf.thumbnailOffset;
    thumbnailSize = mf.thumbnailSize;
    name = mf.name;
    createWatchCount = mf.createWatchCount;
    fileFormat = mf.fileFormat;
    super.update(fromMe);
  }

  private void checkForTVFileConversion(Show s)
  {
    if (s != null)
      checkForTVFileConversion(s.getExternalID());
  }
  private void checkForTVFileConversion(String externalID)
  {
    // Check for conversion to a TV file. This is true if it uses TMS data for the Show IDs
    if (Sage.getBoolean("enable_converting_imported_videos_to_tv_recordings", true)
        && (externalID.startsWith("EP") || externalID.startsWith("SH") ||
            externalID.startsWith("SP") || externalID.startsWith("MV") || externalID.startsWith("DT") ||
            externalID.startsWith("IE") || externalID.startsWith("IM"))) // We added IE & IM for imported episodes & movies, per request of BMT developer stuckless
    {
      if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
        generalType = MEDIAFILE_TV;
      addMediaMask(MEDIA_MASK_TV);
    }
  }

  public boolean setInfoAiring(Airing a)
  {
    Wizard wiz = Wizard.getInstance();
    // Check for dupes against the DB for Airing->MediaFile
    if (wiz.getFileForAiring(a) != null)
      return false;
    try {
      wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
      // If there's a duration for this Airing, but not one specified for the MediaFile then take the
      // Airing's duration and apply it to the MediaFile...or if we're downloading this file currently,
      // then take it from the metadata since it might not be in the file header properly
      if (a.getDuration() > 1 && times.length > 0 &&
          (getRecordDuration() <= 1 || (files.size() > 0 && FileDownloader.isDownloading(null, files.get(0)))))
      {
        if (Sage.DBG) System.out.println("Updating MediaFile duration based on new Airing metadata");
        times[1] = times[0] + a.getDuration();
      }
      forcedComplete = true; // so the files show up in the lists if its recovered info
      infoAiringID = a.id;
      myAiring = null;
      checkForTVFileConversion(a.getShow());
      if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
        wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
      // This'll affect the sorting of the secondary index so we need to re-sort this in the DB
      wiz.check(Wizard.MEDIAFILE_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
    }
    if (a.getMediaMask() != getMediaMask())
    {
      try {
        wiz.acquireWriteLock(Wizard.AIRING_CODE);
        a.setMediaMask(getMediaMask());
        wiz.logUpdate(a, Wizard.AIRING_CODE);
      } finally {
        wiz.releaseWriteLock(Wizard.AIRING_CODE);
      }
      Show s = a.getShow();
      if (s != null && !wiz.isNoShow(s))
        s.addMediaMaskRecursive(getMediaMask());
    }
    return true;
  }

  public boolean setShow(Show s)
  {
    // Check for the TV file conversion fist since it would change the media mask for the Airing. The Airing creation takes
    // care of applying the mediaMask throughout the Show's children
    checkForTVFileConversion(s);
    long newDur = getRecordDuration();
    boolean updateDur = false;
    // If there's a duration for this Airing, but not one specified for the MediaFile then take the
    // Airing's duration and apply it to the MediaFile...or if we're downloading this file currently,
    // then take it from the metadata since it might not be in the file header properly
    if (s.getDuration() > 1 && times.length > 0 && (newDur <= 1 || (files.size() > 0 && FileDownloader.isDownloading(null, files.get(0)) &&
        s.getDuration() > newDur)))
    {
      newDur = s.getDuration();
      updateDur = true;
    }
    Wizard wiz = Wizard.getInstance();
    // If there's a duration'
    Airing currAir = getContentAiring();
    Airing a = wiz.addAiring(s, currAir.stationID, getRecordTime(), newDur, currAir.partsB,
        currAir.miscB, currAir.prB, getMediaMask());
    try {
      wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
      if (updateDur)
      {
        times[1] = times[0] + newDur;
      }
      infoAiringID = a.id;
      myAiring = a;
      if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
        wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
      // This'll affect the sorting of the secondary index so we need to re-sort this in the DB
      wiz.check(Wizard.MEDIAFILE_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
    }
    return true;
  }

  public Airing getContentAiring()
  {
    if (myAiring == null)
    {
      if (infoAiringID != id && infoAiringID != 0)
        myAiring = Wizard.getInstance().getAiringForID(infoAiringID, false, true);
      if (myAiring == null)
        myAiring = new FakeAiring(id);
    }

    return myAiring;
  }

  public Show getShow()
  {
    return (getContentAiring() == null) ? null : getContentAiring().getShow();
  }

  public synchronized long getSize()
  {
    long sum = 0;
    for (int i = 0; i < files.size(); i++)
    {
      File f = files.elementAt(i);
      if ((isDVD() || isBluRay()) && f.isDirectory())
        sum += IOUtils.getDirectorySize(f);
      else
        sum+= f.length();
    }
    return sum;
  }

  public synchronized long getRecordTime()
  {
    return (times.length == 0) ? 0 : times[0];
  }

  public synchronized long getRecordEnd()
  {
    if (times.length == 0) return 0;
    long rv = times[times.length - 1];
    if (rv == 0) rv = Sage.time();
    return rv;
  }

  public synchronized boolean isRecording()
  {
    return (isTV() || generalType == MEDIAFILE_LIVE_BUFFERED_STREAM) && (times.length == 0 ||
        times[times.length - 1] == 0);
  }

  public synchronized boolean isRecording(int seg)
  {
    return (getRecordingSegment() == seg);
  }

  public synchronized boolean isRecording(File f)
  {
    return (f != null) && isRecording() && f.equals(getRecordingFile());
  }

  public synchronized long getRecordDuration()
  {
    long total = 0;
    for (int i = 0; i < times.length; i+=2)
    {
      if (times[i + 1] == 0)
        total += Sage.time() - times[i];
      else
        total += times[i + 1] - times[i];
    }
    return total;
  }

  // This is used during a halt detection so that we can switch from one segment to the next
  // without ever transitioning out of the state that indicates we are currently recording
  synchronized void rollSegment(boolean verifyFiles)
  {
    // I'm a little concerned about grabbing this DB table lock for a larger operation like this...but
    // I think it's really the only safe way to do this as making changes to this object outside of that
    // lock seems like a very bad idea and inconsistent with the rest of this class's behavior
    Wizard wiz = Wizard.getInstance();
    try {
      wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
      long currTime = Sage.time();
      endSegment(currTime, false);
      if (verifyFiles)
      {
        verifyFiles(true, false, false);
      }
      // The startSegment call will log the DB object change here
      // Be sure the start/end times of the segments are not the same as well
      startSegment(currTime + 1, null);
    } finally {
      wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
    }
  }

  void endSegment(long endTime)
  {
    endSegment(endTime, true);
  }

  synchronized void endSegment(long endTime, boolean logChange)
  {
    Wizard wiz = Wizard.getInstance();
    try {
      wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
      times[times.length - 1] = endTime;
      if (logChange && generalType != MEDIAFILE_LOCAL_PLAYBACK)
        wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
    }
  }

  /**
   * Returns a new file name that can be guaranteed to be able to be used on this media file shortly.
   * <p/>
   * You can only get one of these at a time. You must pass the {@link NextSegmentGuarantee}
   * returned from here to {@link #rollGuaranteedSegment(NextSegmentGuarantee)} to start a
   * new segment before you can call this and get a new segment.
   * <p/>
   * This is only to be used when recording and transitioning between file segments during live
   * playback.
   *
   * @return <code>null</code> if your request was denied.
   */
  synchronized NextSegmentGuarantee startGuaranteedSegment(long currTime)
  {
    // We can only assure one of these at a time.
    if (nextSegmentGuarantee != null)
      return null;
    File nextFileName = getNextSegment(false);
    return nextFileName != null ? nextSegmentGuarantee = new NextSegmentGuarantee(nextFileName, currTime) : null;
  }

  /**
   * Start a new segment with the file from a {@link NextSegmentGuarantee} object.
   * <p/>
   * This is only to be used when recording and transitioning between file segments during live
   * playback.
   *
   * @param nextSegment The {@link NextSegmentGuarantee} object provided by
   *                    {@link #startGuaranteedSegment(long)} for this {@link MediaFile}.
   * @return <code>true</code> if the segment change was successful.
   */
  synchronized boolean rollGuaranteedSegment(NextSegmentGuarantee nextSegment)
  {
    if (nextSegmentGuarantee == null || nextSegment != nextSegmentGuarantee)
      return false;

    Wizard wiz = Wizard.getInstance();
    try
    {
      wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
      endSegment(nextSegmentGuarantee.getStartTime());
      startSegment(nextSegmentGuarantee.getStartTime() + 1, null, nextSegment.getNewSegment());
    } finally {
      wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
    }

    if (Sage.DBG) System.out.println("MediaFile transitioned to subfile:" + nextSegmentGuarantee.getNextSegment() + " " + this);
    nextSegmentGuarantee = null;
    return true;
  }

  /**
   * Release a guaranteed new file segment.
   * <p/>
   * You cannot release the guarantee without the correct {@link NextSegmentGuarantee} for this
   * {@link MediaFile} unless the <code>force</code> parameter is get to <code>true</code>.
   * <p/>
   * This is only to be used when recording and transitioning between file segments during live
   * playback.
   *
   * @param nextSegment The {@link NextSegmentGuarantee} object provided by
   *                    {@link #startGuaranteedSegment(long)} for this {@link MediaFile}.
   * @param force Set <code>true</code> to clear the segment regardless of if nextSegment is correct.
   */
  synchronized void releaseGuaranteedSegment(NextSegmentGuarantee nextSegment, boolean force)
  {
    if (nextSegment == nextSegmentGuarantee || force)
      nextSegmentGuarantee = null;
  }

  synchronized void startSegment(long startTime, String newEncodedBy)
  {
    startSegment(startTime, newEncodedBy, null);
  }

  private synchronized void startSegment(long startTime, String newEncodedBy, File guaranteedFile)
  {
    Wizard wiz = Wizard.getInstance();
    try {
      wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
      if (Sage.DBG) System.out.println("MediaFile startSegment enc=" + newEncodedBy + " " + this);
      ArrayList<File> oldFiles = new ArrayList<File>(files);
      if (isLiveBufferedStream())
      {
        times = Pooler.EMPTY_LONG_ARRAY;
        files.clear();
      }
      else if (newEncodedBy != null)
        encodedBy = newEncodedBy;
      addTimes(startTime, 0);
      if (guaranteedFile != null)
        files.addElement(guaranteedFile);
      else
        files.addElement(getNextSegment(false));
      if (isLiveBufferedStream())
      {
        files.firstElement().deleteOnExit();
        oldFiles.removeAll(files);
        if (!oldFiles.isEmpty())
        {
          for (int i = 0; i < oldFiles.size(); i++)
          {
            if (Sage.DBG) System.out.println("Deleting old media file buffer file because its name changed:"  + oldFiles.get(i));
            // If the deletion fails, be sure we don't import it
            if (!oldFiles.get(i).delete())
              SeekerSelector.getInstance().addIgnoreFile(oldFiles.get(i));
          }
        }
        // This'll affect the sorting of the secondary index so we need to re-sort this in the DB
        wiz.check(Wizard.MEDIAFILE_CODE);
      }
      if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
        wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
    }
  }

  // NOTE: Narflex - I think I may have broken this when I added the conditional at the end; check that in CVS to see why we did it
  // and then test with an mms:// URL for playback
  public boolean isLocalFile()
  {
    return (!Sage.client && (host.equalsIgnoreCase(SageTV.hostname) || host.length() == 0)) || generalType == MEDIAFILE_LOCAL_PLAYBACK;
  }

  public boolean verifyFiles(boolean strict)
  {
    return verifyFiles(strict, false);
  }

  public boolean verifyFiles(boolean strict, boolean fixDurs)
  {
    return verifyFiles(strict, fixDurs, true);
  }

  public boolean verifyFiles(boolean strict, boolean fixDurs, boolean logChange)
  {
    if (generalType == MEDIAFILE_LIVE_STREAM || generalType == MEDIAFILE_LIVE_BUFFERED_STREAM)
      return true;
    else if (generalType == MEDIAFILE_DEFAULT_DVD_DRIVE)
    {
      if (Sage.LINUX_OS)
      {
        // For Linux we use cdrecord and parse its output to see what's in the optical drive
        // It returns 0 if there's valid media in the drive (not blank either)
        String burnPath = Sage.get("linux/cd_burn", "cdrecord");
        String devPath = Sage.get("default_burner_device", "/dev/cdrom");
        int rez = IOUtils.exec2(new String[] { "sh", "-c", burnPath + " -toc dev=" + devPath });
        if (Sage.DBG) System.out.println("Checking DVD status rez: " + rez);
        return rez == 0;
      }
      else if (Sage.MAC_OS_X)
      {
        // We look in the /Volumes folder and then one level below that for any VIDEO_TS folders.
        // Presence of that indicates a mounted DVD volume.
        File[] vols = new File("/Volumes").listFiles();
        for (int i = 0; vols != null && i < vols.length; i++)
        {
          if (new File(vols[i], "VIDEO_TS").isDirectory())
          {
            if (Sage.DBG) System.out.println("Mac DVD volume found at:" + vols[i]);
            return true;
          }
        }
        return false;
      }
      // For Windows, we start at C: and search all the drives for one with video_ts folder. If its
      // there then we're OK to go.
      File[] roots = File.listRoots();
      Arrays.sort(roots);
      for (int i = 0; i < roots.length; i++)
      {
        if (roots[i].toString().compareToIgnoreCase("C") < 0)
          continue;
        if (new File(roots[i], "video_ts").isDirectory())
          return true;
      }
      return false;
    }
    if (isLocalFile())
    {
      // Check if this is a file from a SageTV server that's been selected through the file browser interface
      if (Sage.client && (!host.equalsIgnoreCase(SageTV.hostname) && host.length() != 0) && generalType == MEDIAFILE_LOCAL_PLAYBACK)
      {
        if (Sage.DBG) System.out.println("Skipping MF verification since it's a local file streamed from a server");
        return true;
      }
      Wizard wiz = Wizard.getInstance();
      synchronized (this)
      {
        Set<File> doneFiles = new HashSet<File>();
        for (int i = 0; i < files.size(); i++)
        {
          File testFile = files.elementAt(i);
          if (isDVD() || isBluRay())
          {
            File rootDir = IOUtils.getRootDirectory(testFile);
            if (!testFile.exists() && (strict || rootDir == null || rootDir.isDirectory()))
            {
              if (Sage.DBG) System.out.println("MediaFile failing verify testFile=" + testFile);
              return false;
            }
            else
              return true;
          }
          boolean testFileIsFile = testFile.isFile();
          long testFileLength = testFile.length();
          // NARFLEX: 1/22/07 - I added the '&& testFileLength == 0' here because I've seen logs where isFile returned false,
          // but the length was non-zero and the file currently existed so deleting it destroyed one of their recordings.
          // Those cases were using UNC paths so it's probably a glitch in the SMB stuff
          if ((!testFileIsFile && testFileLength == 0) ||
              ((testFileLength == 0 || (testFileLength < (!isAudioOnlyTVFile() ? MINIMUM_TV_FILE_SIZE : MINIMUM_TV_AUDIO_FILE_SIZE) &&
                  isTV() && isMPEG2LegacySubtype(getLegacyMediaSubtype()))) &&
                  !isRecording(i) && !FileDownloader.isDownloading(null, testFile)) || doneFiles.contains(testFile))
          {
            // Check to make sure that the drive itself is not offline
            File rootDir = IOUtils.getRootDirectory(testFile);
            if (strict || rootDir == null || rootDir.isDirectory())
            {
              if (files.size() > 1 && generalType == MEDIAFILE_TV)
              {
                if (Sage.DBG)System.out.println("MediaFile verification deleting segment this=" + this + " segfile=" + testFile);
                // Just kill this segment, we might have others valid
                if (!doneFiles.contains(testFile))
                {
                  if (testFileIsFile && !deleteFileAndIndex(testFile))
                  {
                    // We're unable to delete this file. If we say we failed verification
                    // then there'll be a file there but the object won't exist which'll
                    // usually cause an unwanted reimport. Just hold off until the
                    // next verification round in this case.
                    if (Sage.DBG) System.out.println("MediaFile failed deleting segment file that doesn't verify, won't fail whole verify yet...");
                    doneFiles.add(testFile);
                    continue;
                  }
                }
                try {
                  wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
                  removeTimes(i);
                  files.removeElementAt(i);
                  if (logChange && generalType != MEDIAFILE_LOCAL_PLAYBACK)
                    wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
                } finally {
                  wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
                }
                i--;
              }
              else
              {
                if (Sage.DBG) System.out.println("MediaFile failing verify testFile=" + testFile + " isFile=" + testFileIsFile +
                    " len=" + testFileLength + " isTV=" + isTV() + " isRecording=" + isRecording(i));
                return false;
              }
            }
            else // drive is offline, don't remove this mediafile
              doneFiles.add(testFile);
          }
          else
            doneFiles.add(testFile);
        }
        if (fixDurs && generalType == MEDIAFILE_TV)
        {
          for (int i = 0; i < times.length; i+=2)
          {
            if (times[i] == 0)
              return false;
            if (times[i + 1] == 0)
            {
              File testFile = files.elementAt(i/2);
              // See if we can recover the duration
              long fixedDur = FormatParser.getFileDuration(testFile);
              if (fixedDur > 0)
              {
                try {
                  wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
                  times[i + 1] = times[i] + fixedDur;
                  if (logChange && generalType != MEDIAFILE_LOCAL_PLAYBACK)
                    wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
                } finally {
                  wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
                }
              }
              else
              {
                if (Sage.DBG) System.out.println("ERROR: Couldn't detect duration for file:" + testFile + " guessing it");
                try {
                  wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
                  // This is much better than returning false which will cause the file to get deleted by the Seeker
                  // Since this is from a bad shutdown; we'll just assume it just happened right then
                  times[i + 1] = Sage.time() - 5000;
                  if (logChange && generalType != MEDIAFILE_LOCAL_PLAYBACK)
                    wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
                } finally {
                  wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
                }
              }
            }
          }
        }
      }
    }
    else if (SageTV.neddy != null && NetworkClient.getSN() != null && generalType != MEDIAFILE_LOCAL_PLAYBACK)
      return NetworkClient.getSN().requestVerifyFile(this, strict, fixDurs);
    return true;
  }

  public synchronized File getRecordingFile()
  {
    return files.isEmpty() ? null : ((File) files.lastElement());
  }

  public synchronized File getFile(int segment)
  {
    if ((segment < 0) || (segment >= files.size())) return null;
    return files.elementAt(segment);
  }

  public synchronized File getParentFile()
  {
    File rv = getFile(0);
    if (rv != null)
      return rv.getParentFile();
    else
      return null;
  }

  public synchronized int getNumSegments() { return files.size(); }

  public synchronized long getStart(int segment)
  {
    if ((segment < 0) || (segment >= files.size())) return 0;
    return times[segment*2];
  }

  public synchronized long getEnd(int segment)
  {
    if ((segment < 0) || (segment >= files.size())) return 0;
    long rv = times[segment*2 + 1];
    if (rv == 0) rv = Sage.time();
    return rv;
  }

  public synchronized long getDuration(int segment)
  {
    return getEnd(segment) - getStart(segment);
  }

  public synchronized int segmentLocation(long segTime, boolean roundForward)
  {
    for (int i = 0; i < times.length; i+=2)
    {
      if (segTime < times[i])
        return Math.max(0, Math.min(times.length/2 - 1,
            i/2 + (!roundForward ? -1 : 0)));
      else if ((times[i] <= segTime) && ((times[i + 1] == 0) ||
          (times[i + 1] > segTime)))
        return i/2;
    }
    return times.length/2 - 1;
  }

  synchronized boolean delete(boolean considerInProfile)
  {
    if (considerInProfile && isCompleteRecording() && getContentAiring() != null && isTV())
    {
      Carny.getInstance().addDontLike(getContentAiring(), false);
    }
    while (!files.isEmpty())
    {
      File theFile = files.lastElement();
      long t1 = Sage.time();
      long s = theFile.length();
      if (theFile.isDirectory())
      {
        // NOTE: Delete directory recursively
        if ((isDVD() || isBluRay()) && Sage.getBoolean("directory_deletion_of_dvds_enabled", false))
        {
          System.out.println("Deleting media directory:" + theFile);
          IOUtils.deleteDirectory(theFile);
        }
      }
      else if (theFile.isFile() && !FSManager.getInstance().deleteFile(theFile))
      {
        if (Sage.DBG) System.out.println("FAILED deleting file " + theFile);
        return false;
      }
      else if (Sage.DBG)
        System.out.println("Deleted media segment file " + theFile + " dtime=" + (Sage.time() - t1) + " len=" + s);
      // And remove any index files we created
      String fullPath = theFile.getAbsolutePath();
      int dotIdx = fullPath.lastIndexOf('.');
      if (dotIdx != -1)
      {
        File indFile = new File(fullPath.substring(0, dotIdx) + ".ind");
        indFile.delete();
      }
      // Don't remove the last entry from files because it's used to maintain the file path index
      if (files.size() > 1)
      {
        files.remove(files.size() - 1);
        removeTimes(times.length/2 - 1);
      }
      else
        break;
    }
    // Also remove any .properties files that may have been generated
    File metaFile = getMetadataPropertiesFile();
    if (metaFile != null)
      metaFile.delete();
    return true;
  }

  public synchronized String toString()
  {
    StringBuffer sb = new StringBuffer("MediaFile[");
    sb.append("id=" + id + " ");
    sb.append(getContentAiring());
    sb.append(" mask=");
    if (isTV())
      sb.append("T");
    if (isVideo())
      sb.append("V");
    if (isMusic())
      sb.append("M");
    if (isPicture())
      sb.append("P");
    if (isDVD())
      sb.append("D");
    if (isBluRay())
      sb.append("B");
    sb.append(" host=" + host);
    sb.append(" encodedBy=" + encodedBy);
    sb.append(" format=" + fileFormat);
    if (!files.isEmpty())
      sb.append(" " + files.firstElement());
    for (int i = 0; i < files.size(); i++)
    {
      sb.append(", Seg");
      sb.append(i);
      sb.append('[');
      sb.append(Sage.df(times[i*2]));
      sb.append('-');
      sb.append(Sage.df(times[i*2 + 1]));
      sb.append(']');
    }
    sb.append(']');
    return sb.toString();
  }

  public synchronized long[][] getRecordTimes()
  {
    long[][] rv = new long[times.length/2][2];
    for (int i = 0; i < times.length; i+=2)
    {
      rv[i/2][0] = times[i];
      rv[i/2][1] = (times[i + 1] == 0) ? Sage.time() : times[i + 1];
    }
    return rv;
  }

  public synchronized int getRecordingSegment()
  {
    if (isRecording()) return times.length/2 - 1;
    else return -1;
  }

  // This is for when we record to new files and we have to create filenames
  private String getFileExtension()
  {
    return (fileFormat == null) ? ".mpg" : fileFormat.getPreferredFileExtension();
  }

  private File getDesiredSegmentFile(int fileNum, String forcedStringName, boolean skipAiringID)
  {
    if (generalType == MEDIAFILE_LIVE_BUFFERED_STREAM)
    {
      // JAK: 8/2/05 - Use a different file extension for the circularly buffered
      // files so Windows doesn't try to parse them because sometimes it crashes Explorer.
      return new File(videoDirectory, (forcedStringName != null ? forcedStringName :
        createValidFilename(name)) + '-' + fileNum + ".mpgbuf"/*getFileExtension()*/);
    }
    else if (getShow() == null)
    {
      return new File(videoDirectory, (forcedStringName != null ? forcedStringName :
        createValidFilename(name)) + '-' +
        id + '-' + fileNum + getFileExtension());
    }
    else
    {
      if (Wizard.getInstance().isNoShow(getShow()))
      {
        StringBuffer nameBuf = new StringBuffer();
        ManualRecord mr = Wizard.getInstance().getManualRecord(getContentAiring());
        if (mr != null && mr.getName().trim().length() > 0)
        {
          nameBuf.append(createValidFilename(mr.getName()));
        }
        else
        {
          nameBuf.append(Math.abs(getContentAiring().stationID));
        }
        nameBuf.append('_');
        nameBuf.append(getContentAiring().getChannelNum(0/*providerID*/));
        nameBuf.append('_');
        if (times.length == 0)
          nameBuf.append(ManualRecord.fileTimeFormat.format(new Date(getContentAiring().getStartTime())));
        else
          nameBuf.append(ManualRecord.fileTimeFormat.format(new Date(getRecordTime())));
        nameBuf.append('-');
        nameBuf.append(fileNum);
        nameBuf.append(getFileExtension());
        return new File(videoDirectory, nameBuf.toString());
      }
      else
      {
        Show s = getShow();
        // Jeff Harrison - 10/5/2015
        // Add spaces around SxxExx and Episode Name
        String sectionDivider = (Sage.getBoolean("extended_filenames", false) ? " - " : "-");
        String namePart = (forcedStringName != null ? forcedStringName :
          (createValidFilename(s.getTitle()) +
            ((Sage.getBoolean("use_season_episode_nums_in_filenames", false) && s.getSeasonNumber() > 0 && s.getEpisodeNumber() > 0) ?
              (sectionDivider + "S" + (s.getSeasonNumber() < 10 ? "0" : "") + s.getSeasonNumber() + "E" + (s.getEpisodeNumber() < 10 ? "0" : "") + s.getEpisodeNumber()) : "") +
                ((Sage.getBoolean("use_episodes_in_filenames", true) && s.getEpisodeName().length() > 0) ?
                  (sectionDivider + createValidFilename(s.getEpisodeName())) : "")));
        if (namePart.length() == 0 || "-".equals(namePart))
          namePart = Sage.get("default_filename_when_null", "SageTV");
        else if (namePart.startsWith("-")) // messes up cmd line switches, so don't use - for first char
          namePart = namePart.substring(1);
        return new File(videoDirectory, namePart + (skipAiringID ? "" : (sectionDivider +
          infoAiringID)) + '-' + fileNum + getFileExtension());
      }
    }

  }

  private File getNextSegment(boolean ignoreFiles)
  {
    return getNextSegment(ignoreFiles, null, false);
  }

  private File getNextSegment(boolean ignoreFiles, String forcedStringName, boolean skipAiringID)
  {
    /*
     * ENSURE WE DO NOT CREATE FILES WITH THE SAME NAME TWICE. This is possible because
     * verifyFiles can remove singular segments, which messes up our naming convention, but nothing
     * else besides the naming convention goes wrong.
     */
    File rv;
    // We need to be sure the filenames are in increasing order so the DB index for them stays correct,
    // so don't use files.size() to get the filenum if we already have a segment...instead start with the suffix
    // from the last segment in the list.
    int fileNum = files.size(); // default in case we fail parsing for some reason
    if (!files.isEmpty())
    {
      String lastFilename = files.lastElement().getName();
      int dashIdx = lastFilename.lastIndexOf('-');
      int dotIdx = lastFilename.lastIndexOf('.');
      if (dashIdx != -1 && dotIdx != -1 && dotIdx > dashIdx)
      {
        try
        {
          fileNum = 1 + Integer.parseInt(lastFilename.substring(dashIdx + 1, dotIdx));
        }
        catch (NumberFormatException nfe)
        {
          if (Sage.DBG) System.out.println("Error parsing segment number from filename:" + nfe + " filename=" + lastFilename);
        }
      }
    }
    do
    {
      rv = getDesiredSegmentFile(fileNum, forcedStringName, skipAiringID);
      if (generalType == MEDIAFILE_LIVE_BUFFERED_STREAM)
      {
        rv.deleteOnExit();
        break;
      }

      fileNum++;
    } while (!ignoreFiles && (rv.isFile() || files.contains(rv) ||
      (nextSegmentGuarantee != null && rv.equals(nextSegmentGuarantee.getNewSegment()))));
    if (!ignoreFiles && !rv.isFile())
    {
      try
      {
        rv.createNewFile();
        if (Sage.DBG) System.out.println("MediaFile created subfile:" + rv + " " + this);
      }
      catch (IOException e)
      {
        System.out.println("MediaFile unable to create file for next segment:" + e);
      }
    }
    return rv;
  }

  public String getMediaTitle()
  {
    if (getShow() != null)
    {
      if (Wizard.getInstance().isNoShow(getShow()))
        return Sage.rez("Channel") + " " + getContentAiring().getChannelNum(0/*providerID*/);
      else if (isMusic())
        return getShow().getEpisodeName();
      else
        return getShow().getTitle();
    }
    else
    {
      // Check for mrs
      ManualRecord mr = Wizard.getInstance().getManualRecord(getContentAiring());
      if (mr != null)
        return mr.getContentAiring().getTitle();
      else
        return getName();
    }
  }

  public static String createValidFilename(String tryMe)
  {
    if (tryMe == null) return "null";
    boolean allowUnicode = Sage.getBoolean("allow_unicode_characters_in_generated_filenames", false);
    boolean extendedFilenames = Sage.getBoolean("extended_filenames", false);
    if (!allowUnicode) {
      // Normalize any accented characters by decomposing them into their
      // ascii value and the accent. The accent will be stripped out in the loop
      // below.
      tryMe = Normalizer.normalize(tryMe, Normalizer.Form.NFD);
    }
    int len = tryMe.length();
    StringBuffer sb = new StringBuffer(len);
    for (int i = 0; i < len; i++)
    {
      char c = tryMe.charAt(i);
      // Stick with ASCII to prevent issues with the filesystem names unless a custom property is set
      if (allowUnicode)
      {
        if (Character.isLetterOrDigit(c) ||
                (extendedFilenames && LEGAL_FILE_NAME_CHARACTERS.contains(String.valueOf(c))))
            sb.append(c);
      } else if ((c >= 'a' && c <= 'z') ||
        (c >= '0' && c <= '9') ||
        (c >= 'A' && c <= 'Z') ||
        // Jeff Harrison - 09/10/2016
     	  // Keep spaces and other extra characters in filenames
        (extendedFilenames && LEGAL_FILE_NAME_CHARACTERS.contains(String.valueOf(c))))
          sb.append(c);
    }
    return sb.toString();
  }

  public File[] getFiles()
  {
    return files.toArray(new File[0]);
  }

  public boolean isCompleteRecording()
  {
    // NOTE: If they're using negative padding we want to use the reduced duration for this
    // calculation.
    return ((!isTV() || archive) && !isAnyLiveStream()) || (!isRecording() &&
        (forcedComplete || (getRecordDuration() >= Math.min(getContentAiring().getSchedulingDuration(),
            getContentAiring().getDuration())- LOSS_FOR_COMPLETE)));
  }

  /**
   * Use with care.
   */
  void thisIsComplete()
  {
    if (!forcedComplete)
    {
      Wizard wiz = Wizard.getInstance();
      try {
        wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
        forcedComplete = true;
        if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
          wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
      } finally {
        wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
      }
    }
  }

  int getCreateWatchCount() { return createWatchCount; }

  public String getName() { return name; }
  public boolean isArchiveFile() { return archive; }
  public boolean isArchiveOK(File archiveDir)
  {
    if (archive) return false;
    File newRoot = IOUtils.getRootDirectory(archiveDir);

    long spaceNeededOnNewRoot = 0;
    for (int i = 0; i < files.size(); i++)
    {
      try
      {
        File currFile = files.elementAt(i);
        if (!archiveDir.getCanonicalFile().equals(currFile.getParentFile().getCanonicalFile()))
        {
          File orgParent = IOUtils.getRootDirectory(currFile);

          if (!orgParent.equals(newRoot))
          {
            spaceNeededOnNewRoot += currFile.length();
          }
        }
      }
      catch (IOException e)
      {
        System.out.println("MediaFile I/O Error:" + e);
      }
    }
    if (spaceNeededOnNewRoot > Sage.getDiskFreeSpace(newRoot.toString()))
      return false;
    else
      return true;
  }

  boolean shouldIWaitToStartPlayback()
  {
    if (shouldIWaitForData())
      return true;
    return !hasValidFileFormat();
  }
  boolean shouldIWaitForData()
  {
    File recFile = null;
    synchronized (this)
    {
      // NOTE: 10/20/04 - I commented out the isTV() to prevent demux hanging
      // for no data being produced to a live buffered stream
      if (isRecording() && !isLiveStream()/* && isTV()*/)
        recFile = getRecordingFile();
      else
      {
        return false;
      }
    }
    if (MINIMUM_TV_FILE_SIZE == 0)
      return false;
    if (isLocalFile())
    {
      MMC mmc = MMC.getInstance();
      // NOTE: 7/8/05 - JK - I changed this to match what the file verifier does. Otherwise
      // this could end up causing the player to load a file that's too small to be read. And it
      // also means it won't start to load a file that won't pass verification due to size.
      return mmc.getRecordedBytes(recFile) < (!isAudioOnlyTVFile() ? MINIMUM_TV_FILE_SIZE : MINIMUM_TV_AUDIO_FILE_SIZE);
    }
    else
    {
      Socket sock = null;
      DataOutputStream outStream = null;
      DataInputStream inStream = null;
      try
      {
        sock = new Socket();
        sock.connect(new InetSocketAddress(Sage.preferredServer, 7818));
        sock.setSoTimeout(2000);
        sock.setTcpNoDelay(true);

        outStream = new DataOutputStream(sock.getOutputStream());
        inStream = new DataInputStream(sock.getInputStream());
        outStream.write("OPENW ".getBytes(Sage.BYTE_CHARSET));
        outStream.write(recFile.toString().getBytes("UTF-16BE"));
        outStream.write("\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        String str = Sage.readLineBytes(inStream);
        if (!"OK".equals(str))
        {
          // This can happen in a transient state while switching segments and we reference
          // a segment that was zero-sized but was just deleted in preparation for switching
          // to the next one in a no signal situation. We know we are recording...we would not have
          // got down to here if we are not...so shortly we should have a valid file created (albeit possibly empty).
          // So just treat this the same as the zero file size case.
          return true;
        }
        // get the size
        outStream.write("SIZE\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();

        str = Sage.readLineBytes(inStream);
        long remoteSize = Long.parseLong(str.substring(0, str.indexOf(' ')));
        outStream.write("QUIT\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        return remoteSize < (!isAudioOnlyTVFile() ? MINIMUM_TV_FILE_SIZE : MINIMUM_TV_AUDIO_FILE_SIZE);
      }
      catch (Exception e)
      {
        if (Sage.DBG) System.out.println("Error getting file size for wait data testing:" + e);
        // Also return true in this case rather than false. If we have an error getting the file size (likely due to
        // a timeout because of server GC)...then we should just check again, which is what the caller will
        // end up doing. We don't want it to move on and try to play it if we can't confirm it's OK.
        return true;
      }
      finally
      {
        if (sock != null)
          try{sock.close();}catch(Exception e){}
        if (outStream != null)
          try{outStream.close();}catch(Exception e){}
        if (inStream != null)
          try{inStream.close();}catch(Exception e){}
      }
    }
  }

  public void simpleArchive()
  {
    if (archive) return;
    archive = true;
    if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
      Wizard.getInstance().logUpdate(this, Wizard.MEDIAFILE_CODE);
  }

  public void simpleUnarchive()
  {
    if (!archive) return;
    archive = false;
    if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
      Wizard.getInstance().logUpdate(this, Wizard.MEDIAFILE_CODE);
  }

  // This should ONLY be used by the transcoder when dealing with multi-segment files. DO NOT use this for recording/import purposes
  protected void addSegmentFileDirect(File newFile)
  {
    addTimes(newFile.lastModified() -
        FormatParser.getFileDuration(newFile), newFile.lastModified());
    files.addElement(newFile);
  }

  public boolean setFiles(File[] newFiles)
  {
    return setFiles(newFiles, 0, 0);
  }
  public synchronized boolean setFiles(File[] newFiles, long newStart, long newEnd)
  {
    if (generalType == MEDIAFILE_LOCAL_PLAYBACK)
    {
      if (newFiles == null || newFiles.length == 0) return false;
      ContainerFormat newFormat;
      if (host != null && host.indexOf("://") == -1 && !SageTV.hostname.equals(host))
        newFormat = FormatParser.getFFMPEGFileFormat("stv://" + host + "/" + newFiles[0]);
      else
        newFormat = FormatParser.getFileFormat(newFiles[0]);
      if (fileFormat != null && fileFormat.hasMetadata())
        newFormat.addMetadata(fileFormat.getMetadata());
      // These are no longer stored in the DB, so there's no reason to update the index when we change this
      times = Pooler.EMPTY_LONG_ARRAY;
      files.clear();
      initialize(newFiles[0], "", newFormat);
      return true;
    }
    if (newFiles.length != files.size())
    {
      if (Sage.DBG) System.out.println("FAILED in MediaFile.setFiles because one of the new files ain't right: " + this + " "  + Arrays.asList(newFiles));
      return false;
    }
    // Double-check to make sure all of the new files are there
    List<File> killFiles = new ArrayList<File>();
    for (int i = 0; i < newFiles.length; i++)
    {
      if (!newFiles[i].isFile() || newFiles[i].length() == 0)
      {
        if (Sage.DBG) System.out.println("FAILED in MediaFile.setFiles because one of the new files ain't right:" + this + " " + Arrays.asList(newFiles));
        return false;
      }
    }
    ContainerFormat newFileFormat = FormatParser.getFileFormat(newFiles[0]);
    if (fileFormat != null && fileFormat.hasMetadata() && newFileFormat != null)
      newFileFormat.addMetadata(fileFormat.getMetadata());
    Wizard wiz = Wizard.getInstance();
    try {
      wiz.acquireWriteLock(Wizard.MEDIAFILE_CODE);
      for (int i = 0; i < files.size(); i++)
      {
        File currFile = files.elementAt(i);
        if (currFile.equals(newFiles[i]) || !currFile.isFile())
          continue;
        killFiles.add(new File(currFile.toString() + ".delete"));
        if (!currFile.renameTo(killFiles.get(killFiles.size() - 1)))
        {
          // Fix what we've done
          while (i > 0)
          {
            i--;
            new File(files.elementAt(i).toString() + ".delete").renameTo(
                files.get(i));
          }
          if (Sage.DBG) System.out.println("Failed renaming file " + currFile);
          return false;
        }
      }
      files.clear();
      files.addAll(Arrays.asList(newFiles));
      fileFormat = newFileFormat;
      // If this is a single file and we have the format information then use that duration information to update
      // the times information as well
      if (times.length == 2 & fileFormat != null)
      {
        times[1] = times[0] + fileFormat.getDuration();
      }
      if (OPTIMIZE_METADATA_MEM_USAGE)
        optimizeMetadataStorage();
      if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
      {
        wiz.logUpdate(this, Wizard.MEDIAFILE_CODE);
        // This'll affect the sorting of the secondary index so we need to re-sort this in the DB
        wiz.check(Wizard.MEDIAFILE_CODE);
      }
    } finally {
      wiz.releaseWriteLock(Wizard.MEDIAFILE_CODE);
    }
    if (Sage.getBoolean("mstry_del_src_after", true))
    {
      for (int i = 0; i < killFiles.size(); i++)
      {
        File f = killFiles.get(i);
        if (!deleteFileAndIndex(f) && f.isFile())
        {
          if (Sage.DBG) System.out.println("FAILED deleting file after updating media file set, will perform at exit: " +
              f + " mf=" + this);
          f.deleteOnExit();
        }
      }
    }
    if (newStart != 0 && newEnd != 0)
    {
      boolean changedTimes = false;
      for (int i = 0; i < times.length; i+=2)
      {
        if (times[i] < newStart)
        {
          times[i] = newStart;
          changedTimes = true;
        }
        if (times[i + 1] > newEnd)
        {
          times[i + 1] = newEnd;
          changedTimes = true;
        }
      }
      if (changedTimes && generalType != MEDIAFILE_LOCAL_PLAYBACK)
        Wizard.getInstance().logUpdate(this, Wizard.MEDIAFILE_CODE);
    }
    return true;
  }

  public boolean isSameAlbum(MediaFile testMe)
  {
    Album myAlbum = Wizard.getInstance().getCachedAlbumForMediaFile(this);
    Album theirAlbum = Wizard.getInstance().getCachedAlbumForMediaFile(testMe);
    return (myAlbum == theirAlbum) || (myAlbum != null && theirAlbum != null &&
        myAlbum.getTitleStringer() == theirAlbum.getTitleStringer() &&
        myAlbum.getArtistObj() == theirAlbum.getArtistObj());
  }

  public void copyToLocalStorage(File localFile) throws IOException
  {
    copyToLocalStorage(files.elementAt(0), localFile);
  }

  // For downloading thumbnail/subtitle files; if it's a subtitle file you need to get MediaServer access outside of this call
  public void copyToLocalStorage(File remoteFile, File localFile) throws IOException
  {
    copyToLocalStorage(remoteFile, 0, 0, localFile);
  }

  public void copyToLocalStorage(File remoteFile, long offset, long length, File localFile) throws IOException
  {
    if (isLocalFile()) throw new IOException("Cannot copy non-remote file to local");
    Socket sock = null;
    DataOutputStream outStream = null;
    DataInputStream inStream = null;
    OutputStream fileOut = null;
    try
    {
      sock = new Socket();
      sock.connect(new InetSocketAddress(/*host*/Sage.preferredServer, 7818));
      sock.setSoTimeout(30000);
      sock.setTcpNoDelay(true);
      outStream = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
      inStream = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
      outStream.write("OPENW ".getBytes(Sage.BYTE_CHARSET));
      outStream.write(remoteFile.toString().getBytes("UTF-16BE"));
      outStream.write("\r\n".getBytes(Sage.BYTE_CHARSET));
      outStream.flush();
      String str = Sage.readLineBytes(inStream);
      if (!"OK".equals(str))
        throw new IOException("Error opening remote file of:" + str);
      if (length == 0)
      {
        // get the size
        outStream.write("SIZE\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        str = Sage.readLineBytes(inStream);
        length = Long.parseLong(str.substring(0, str.indexOf(' ')));
      }
      byte[] buf = new byte[65536];
      fileOut = new FileOutputStream(localFile);
      long currOffset = offset;
      while (length > 0)
      {
        int currRead = (int)Math.min(buf.length, length);
        outStream.write(("READ " + currOffset + " " + currRead + "\r\n").getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        inStream.readFully(buf, 0, currRead);
        fileOut.write(buf, 0, currRead);
        length -= currRead;
        currOffset += currRead;
      }
      outStream.write("QUIT\r\n".getBytes(Sage.BYTE_CHARSET));
      outStream.flush();
    }
    finally
    {
      if (sock != null)
        sock.close();
      if (outStream != null)
        outStream.close();
      if (inStream != null)
        inStream.close();
      if (fileOut != null)
        fileOut.close();
    }
  }

  // remote file can be specified because it could be a separate thumbnail file we're trying to load
  public byte[] copyToLocalMemory(File remoteFile, long offset, int length, byte[] localBuf)
      throws IOException {

    if (isLocalFile())
    {
      if (length == 0)
      {
        offset = 0;
        length = (int)remoteFile.length();
      }
      if (localBuf == null)
      {
        localBuf = new byte[length];
      }
      FileInputStream fileIn = null;
      try
      {
        fileIn = new FileInputStream(remoteFile);
        fileIn.skip(offset);
        fileIn.read(localBuf, 0, length);
        return localBuf;
      }
      finally
      {
        if (fileIn != null)
        {
          try{fileIn.close();}catch(Exception e){}
        }
      }
    }
    else
    {
      Socket sock = null;
      DataOutputStream outStream = null;
      DataInputStream inStream = null;
      try
      {
        sock = new Socket();
        sock.connect(new InetSocketAddress(/*host*/Sage.preferredServer, 7818));
        sock.setSoTimeout(30000);
        sock.setTcpNoDelay(true);
        outStream = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
        inStream = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
        outStream.write("OPENW ".getBytes(Sage.BYTE_CHARSET));
        outStream.write(remoteFile.toString().getBytes("UTF-16BE"));
        outStream.write("\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        String str = Sage.readLineBytes(inStream);
        if (!"OK".equals(str))
          throw new IOException("Error opening remote file of:" + str);
        // get the size
        outStream.write("SIZE\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        str = Sage.readLineBytes(inStream);
        long remoteSize = Long.parseLong(str.substring(0, str.indexOf(' ')));
        if (length == 0)
        {
          offset = 0;
          length = (int)remoteSize;
        }
        if (localBuf == null)
        {
          localBuf = new byte[length];
        }
        int currRead = 0;
        long amountToRead = Math.min(remoteSize - offset, length);
        int buffOffset = 0;
        while (amountToRead > 0)
        {
          currRead = (int)Math.min(65536, amountToRead);
          outStream.write(("READ " + offset + " " + currRead + "\r\n").getBytes(Sage.BYTE_CHARSET));
          outStream.flush();
          inStream.readFully(localBuf, buffOffset, currRead);
          offset += currRead;
          amountToRead -= currRead;
          buffOffset += currRead;
        }
        outStream.write("QUIT\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        return localBuf;
      }
      finally
      {
        if (sock != null)
          sock.close();
        if (outStream != null)
          outStream.close();
        if (inStream != null)
          inStream.close();
      }
    }
  }

  public MetaImage getFullImage()
  {
    if (!isPicture())
      return null;
    else
      return MetaImage.getMetaImage(this);
  }

  public File getSpecificThumbnailFile() { return thumbnailFile; }

  public boolean hasSpecificThumbnail()
  {
    if (isPicture() && THUMB_FOLDER_NAME.length() > 0)
      return true;
    if (thumbnailFile != null /*&& (!isLocalFile() || thumbnailFile.isFile())*/)
      return true;
    if (VIDEO_THUMBNAIL_FILE_GEN && (isVideo() || isBluRay()) && (thumbnailLoadState != 3))
    {
      if (fileFormat != null && fileFormat.isDRMProtected())
        return false;
      // Don't call getPrimaryVideoFormat in this class because that may force a format detection which could be undesirable for performance reasons
      if (fileFormat != null && MediaFormat.MPEG4X.equals(fileFormat.getPrimaryVideoFormat()))
        return false;
      else
        return true;
    }
    return false;
  }

  public boolean hasThumbnail()
  {
    if (hasSpecificThumbnail()) return true;
    if (generalType == MEDIAFILE_TV)
    {
      // TV recording, look for the channel logo
      Channel c = getContentAiring().getChannel();
      if (c != null)
        return EPG.getInstance().getLogoPath(c) != null;
    }
    return false;

  }

  private File cachedThumbLocation;

  public File getGeneratedThumbnailFileLocation()
  {
    if ((!isPicture() || THUMB_FOLDER_NAME.length() == 0) && (!VIDEO_THUMBNAIL_FILE_GEN ||
        isAnyLiveStream() || (!isVideo() && !isBluRay()))) return null;
    if (cachedThumbLocation != null)
      return cachedThumbLocation;
    File zeroFile = getFile(0);
    if (zeroFile == null) return null;
    return cachedThumbLocation = new File(THUMB_FOLDER, createValidFilename(zeroFile.toString()) + ".jpg");
  }

  public static MetaImage getGenericImage(int mediaMask, ResourceLoadListener loadNotifier)
  {
    if (loadNotifier != null && loadNotifier.getUIMgr() != null)
    {
      if ((mediaMask & DBObject.MEDIA_MASK_DVD) == DBObject.MEDIA_MASK_DVD)
        return MetaImage.getMetaImage(loadNotifier.getUIMgr().get("ui/default_dvd_icon", "dvd.png"), loadNotifier);
      else if ((mediaMask & DBObject.MEDIA_MASK_BLURAY) == DBObject.MEDIA_MASK_BLURAY)
        return MetaImage.getMetaImage(loadNotifier.getUIMgr().get("ui/default_bluray_icon", "bluray.png"), loadNotifier);
      else if ((mediaMask & DBObject.MEDIA_MASK_MUSIC) == DBObject.MEDIA_MASK_MUSIC)
        return MetaImage.getMetaImage(loadNotifier.getUIMgr().get("ui/default_music_icon", "MusicArt.png"), loadNotifier);
      else if ((mediaMask & DBObject.MEDIA_MASK_PICTURE) == DBObject.MEDIA_MASK_PICTURE)
        return MetaImage.getMetaImage(loadNotifier.getUIMgr().get("ui/default_picture_icon", "Slideshow.png"), loadNotifier);
      else
        return MetaImage.getMetaImage(loadNotifier.getUIMgr().get("ui/default_video_icon", "VideoArt.png"), loadNotifier);
    }
    else
      return MetaImage.getMetaImage((String) null);
  }

  private void addThumbTimer()
  {
    Sage.addTimerTask(new TimerTask()
    {
      public void run()
      {
        Vector<ResourceLoadListener> needToBeNotified = null;
        synchronized (thumbLock)
        {
          //System.out.println("Got the callback from the timer for " + MediaFile.this);
          thumbnailLoadState = 0;
          if (thumbLoadNotifiers != null)
          {
            needToBeNotified = thumbLoadNotifiers;
            thumbLoadNotifiers = null;
          }
        }
        if (needToBeNotified != null)
        {
          Set<ResourceLoadListener> alreadyNotified = new HashSet<ResourceLoadListener>();
          for (int i = 0; i < needToBeNotified.size(); i++)
          {
            ResourceLoadListener loadNotifier = needToBeNotified.get(i);
            // Avoid notifying the same person more than once
            if (alreadyNotified.add(needToBeNotified.get(i)))
            {
              // Check to see if it still even needs it
              if (loadNotifier.loadStillNeeded(MediaFile.this))
              {
                // Check if these will even allow rendering if we do the refresh before we waste that call
                boolean refreshNow = false;
                MetaImage myMeta = MetaImage.getMetaImageNoLoad(MediaFile.this);
                long timeDiff = Sage.eventTime() - loadNotifier.getUIMgr().getRouter().getLastEventTime();
                if (myMeta != null && myMeta.mightLoadFast(loadNotifier.getUIMgr()))
                  refreshNow = true;
                else if (timeDiff > loadNotifier.getUIMgr().getInt("ui/inactivity_timeout_for_full_thumb_load", 1500))
                  refreshNow = true;
                if (refreshNow)
                  loadNotifier.loadFinished(MediaFile.this, true);
                else
                {
                  synchronized (thumbLock)
                  {
                    if (thumbLoadNotifiers == null)
                    {
                      thumbLoadNotifiers = new Vector<ResourceLoadListener>();
                      thumbLoadNotifiers.add(loadNotifier);
                      addThumbTimer();
                    }
                    else
                      thumbLoadNotifiers.add(loadNotifier);
                  }
                }
              }
            }
          }
        }
      }
    }, Sage.getInt("ui/inactivity_timeout_for_full_thumb_load", 1500), 0);

  }
  // This will return a MetaImage that will load quickly, and if it can't get one then it'll
  // return one with a Wait wrapper on it so the loadNotifier will be refreshed when the load completes
  private MetaImage getWaitLoadThumb(final ResourceLoadListener loadNotifier, MetaImage myMeta)
  {
    if (loadNotifier == null || !loadNotifier.needsLoadCallback(this))
      return myMeta;
    synchronized (thumbLock)
    {
      if (thumbnailLoadState == 3)
      {
        return getGenericImage(getMediaMask(), loadNotifier);
      }
      else if (thumbnailLoadState == 1)
      {
        if (thumbLoadNotifiers == null)
          thumbLoadNotifiers = new Vector<ResourceLoadListener>();
        if (!thumbLoadNotifiers.contains(loadNotifier))
          thumbLoadNotifiers.add(loadNotifier);
        return new MetaImage.Waiter(getGenericImage(getMediaMask(), loadNotifier), this);
      }
      else
      {
        if (myMeta.mightLoadFast(loadNotifier.getUIMgr()) ||
            Sage.eventTime() - loadNotifier.getUIMgr().getRouter().getLastEventTime() > loadNotifier.getUIMgr().getInt("ui/inactivity_timeout_for_full_thumb_load", 1500))//lastThumbLoadNotifyTime < Sage.getInt("ui/thumbnail_load_delay", 300))
        {
          return myMeta;
        }
        thumbnailLoadState = 1;
        if (thumbLoadNotifiers == null)
          thumbLoadNotifiers = new Vector<ResourceLoadListener>();
        if (!thumbLoadNotifiers.contains(loadNotifier))
          thumbLoadNotifiers.add(loadNotifier);
        addThumbTimer();
        //System.out.println("Returning the WAITER meta Image and starting the timer for the callback " + this);
        return new MetaImage.Waiter(getGenericImage(getMediaMask(), loadNotifier), this);
      }
    }
  }

  public boolean isThumbnailLoaded(UIManager uiMgr)
  {
    // You can also get Channel logos this way, it makes it all easier
    if (!hasThumbnail())
      return false;
    if (!hasSpecificThumbnail())
    {
      if (generalType == MEDIAFILE_TV)
      {
        // TV recording, look for the channel logo

        Airing conAir = getContentAiring();
        if (conAir != null)
        {
          Channel c = conAir.getChannel();
          if (c != null)
          {
            MetaImage mi = MetaImage.getMetaImageNoLoad(EPG.getInstance().getLogoPath(c));
            return (mi != null && mi.mightLoadFast(uiMgr));
          }
        }
      }
      return false;
    }
    if ((!isPicture() && ((!isVideo() && !isBluRay()) || !VIDEO_THUMBNAIL_FILE_GEN)) || thumbnailFile != null)
    {
      // We still may have a representative thumbnail file sitting around to use.
      MetaImage mi = MetaImage.getMetaImageNoLoad(new MetaImage.MediaFileThumbnail(this));
      return (mi != null && mi.mightLoadFast(uiMgr));
    }
    File genThumbPath = getGeneratedThumbnailFileLocation();
    MetaImage mi = MetaImage.getMetaImageNoLoad(genThumbPath);
    return (mi != null && mi.mightLoadFast(uiMgr));
  }

  // Used to ensure that a generated thumbnail is done
  private MetaImage getFinishedThumbGen(final ResourceLoadListener loadNotifier)
  {
    synchronized (thumbLock)
    {
      if (thumbLoadNotifiers == null)
        thumbLoadNotifiers = new Vector<ResourceLoadListener>();
      // This is to ensure that other loaders which may have spawned for UI reasons don't kill the loading we're waiting on here
      thumbLoadNotifiers.add(new ResourceLoadListener()
      {
        public UIManager getUIMgr()
        {
          return (loadNotifier != null) ? loadNotifier.getUIMgr() : UIManager.getLocalUI();
        }
        public void loadFinished(Object obj, boolean success)
        {
        }
        public boolean loadStillNeeded(Object obj)
        {
          return true;
        }
        public boolean needsLoadCallback(Object obj)
        {
          return true;
        }
      });
      while (thumbnailLoadState < 2)
      {
        try
        {
          thumbLock.wait(5000);
        }
        catch (InterruptedException e){}
        if (thumbnailLoadState == 0)
        {
          if (Sage.DBG) System.out.println("Thumbnail gen wait saw the state go back to zero; triggering a thumb gen again to finish");
          getThumbnail(null, false);
        }
      }
      if (thumbnailLoadState == 2)
        return MetaImage.getMetaImage(getGeneratedThumbnailFileLocation());
      else
        return getGenericImage(getMediaMask(), loadNotifier);
    }
  }

  public MetaImage getThumbnail(final ResourceLoadListener loadNotifier)
  {
    return getThumbnail(loadNotifier, false);
  }
  public MetaImage getThumbnail(final ResourceLoadListener loadNotifier, boolean waitForLoad)
  {
    // You can also get Channel logos this way, it makes it all easier
    if (!hasThumbnail() || isAnyLiveStream())
      return null;
    if (!hasSpecificThumbnail())
    {
      if (generalType == MEDIAFILE_TV)
      {
        // TV recording, look for the channel logo

        Airing conAir = getContentAiring();
        if (conAir != null)
        {
          Channel c = conAir.getChannel();
          if (c != null)
            return EPG.getInstance().getLogo(c, loadNotifier);
        }
      }
      return null;
    }
    if ((!isPicture() && ((!isVideo() && !isBluRay()) || !VIDEO_THUMBNAIL_FILE_GEN)) || thumbnailFile != null)
    {
      boolean useExtThumb = false;
      if (!Sage.isTrueClient() && thumbnailFile != null && !files.isEmpty() && !files.firstElement().equals(thumbnailFile))
        useExtThumb = true;
      if (loadNotifier != null && loadNotifier.getUIMgr() != null)
        return useExtThumb ? loadNotifier.getUIMgr().getBGLoader().getMetaImageFast(thumbnailFile, loadNotifier, null) :
          loadNotifier.getUIMgr().getBGLoader().getMetaImageFast(new MetaImage.MediaFileThumbnail(this), loadNotifier, null);
        // We still may have a representative thumbnail file sitting around to use.
        return useExtThumb ? MetaImage.getMetaImage(thumbnailFile) :
          getWaitLoadThumb(loadNotifier, MetaImage.getMetaImage(new MetaImage.MediaFileThumbnail(this)));
    }

    // If we don't have data for the thumbnail yet then return null until its ready
    if (shouldIWaitToStartPlayback() || files.isEmpty() || THUMB_FOLDER_NAME.length() == 0)
    {
      if (loadNotifier != null && loadNotifier.getUIMgr() != null)
        return loadNotifier.getUIMgr().getBGLoader().getMetaImageFast(getGenericImage(getMediaMask(), loadNotifier), loadNotifier, null);
      return getGenericImage(getMediaMask(), loadNotifier);
    }

    if (loadNotifier != null && loadNotifier.getUIMgr() != null)
    {
      // Do a regular BG load of this resource so it never slows things down
      return loadNotifier.getUIMgr().getBGLoader().getMetaThumbFast(this, loadNotifier, getGenericImage(getMediaMask(), loadNotifier));
    }

    synchronized (thumbLock)
    {
      if (thumbnailLoadState == 1)
      {
        if (loadNotifier != null && loadNotifier.needsLoadCallback(this))
        {
          if (thumbLoadNotifiers == null)
            thumbLoadNotifiers = new Vector<ResourceLoadListener>();
          if (!thumbLoadNotifiers.contains(loadNotifier))
            thumbLoadNotifiers.add(loadNotifier);
        }
        if (waitForLoad)
        {
          return getFinishedThumbGen(loadNotifier);
        }
        return new MetaImage.Waiter(getGenericImage(getMediaMask(), loadNotifier), this);
      }
      else if (thumbnailLoadState == 3)
        return getGenericImage(getMediaMask(), loadNotifier);
      // Just return the proper MetaImage object here if we've already loaded it OK once. We reset this flag in
      // the reinitializeMetadata function now to handle things like picture rotation
      else if (thumbnailLoadState == 2)
      {
        // NARFLEX: 4/9/09 - NOTE we do NOT do a background load of thumbnails that we generated if we already loaded them for this UI.
        // Instead we're giving a reference to the actual File object which will then require loading before it is rendered.
        // This is not essential to optimize; and may be better left the way it is...but its probably the cause of slowdowns
        // when re-entering some of the menus even in extender mode. So let's put it in there; because that's how I'd expect it to
        // behave.
        MetaImage rv = MetaImage.getMetaImage(getGeneratedThumbnailFileLocation());
        if (rv != null && !rv.isNullOrFailed())
          return getWaitLoadThumb(loadNotifier, rv);
      }

      /*
       * We cache these in a local directory. We can't use the thumbnailFile field
       * to handle this because thumbnail caching is independent on the client & server.
       * We verify cache matches based on constructing a safe full name from the path.
       * If we're smaller than the thumbnail size then we don't use the thumbnail.
       */
      MetaImage myMeta = null;
      int thumbWidth = Sage.getInt("ui/thumbnail_width_new", 512);
      int thumbHeight = Sage.getInt("ui/thumbnail_height_new", 512);
      float aspectRatio;
      if (isPicture())
      {
        myMeta = MetaImage.getMetaImage(this);
        Dimension imageSize = myMeta.getImageSize(0);
        aspectRatio = ((float)imageSize.width) / imageSize.height;
      }
      else
      {
        aspectRatio = getPrimaryVideoAspectRatio(false);
        if (aspectRatio <= 0)
          aspectRatio = 1;
      }
      // Maintain aspect ratio
      if (aspectRatio > 1.0f)
        thumbHeight = Math.round(thumbWidth / aspectRatio);
      else
        thumbWidth = Math.round(aspectRatio * thumbHeight);

      // Make them even numbers (FFMPEG wants this for the thumbnails it generates)
      thumbHeight -= thumbHeight % 2;
      thumbWidth -= thumbWidth % 2;
      final File imageThumbFile = getGeneratedThumbnailFileLocation();

      if (Math.abs(getRecordTime() - imageThumbFile.lastModified()) > 2500 && isPicture())
      {
        if (Sage.DBG) System.out.println("Re-generating thumbnail because source image has changed recTime=" + Sage.df(getRecordTime()) +
            " thumbTime=" + Sage.df(imageThumbFile.lastModified()));
      }
      else if (imageThumbFile.isFile())
      {
        MetaImage thumbMeta = MetaImage.getMetaImage(imageThumbFile);
        // Check to make sure the size of the thumbnail image is what we'd expect. It may not
        // be if it wasn't generated correctly. (old bug)
        // Only regenerate for AR if it's a picture file...but we also changed the thumbnail size at one point and want to regenerate old ones that are too small,
        // so if both dimensions are off in the requested size, then also regenerate in that case, even for non-picture files.
        if ((Math.abs(thumbMeta.getWidth() - thumbWidth) < 4 && Math.abs(thumbMeta.getHeight() - thumbHeight) < 4) ||
            (!isPicture() && imageThumbFile.length() > 0 && (Math.abs(thumbMeta.getWidth() - thumbWidth) < 16 || Math.abs(thumbMeta.getHeight() - thumbHeight) < 16)))
        {
          // Set the flag so we know we can properly load it quickly
          thumbnailLoadState = 2;
          return getWaitLoadThumb(loadNotifier, thumbMeta);
        }
        else
        {
          if (Sage.DBG) System.out.println("Re-generating thumbnail because it's size doesn't match the source image's AR currThumbWidth=" + thumbMeta.getWidth() +
              " currThumbHeight=" + thumbMeta.getHeight() + " newThumbWidth=" + thumbWidth + " newThumbHeight=" + thumbHeight);
          // If we don't remove this from the MetaImage cache, it won't use the new sizing
          MetaImage.clearFromCache(thumbMeta.getSource());
          imageThumbFile.delete();
        }
      }

      // Thumbnail not present, create a new one.
      thumbnailLoadState = 1;
      final int finalThumbWidth = thumbWidth;
      final int finalThumbHeight = thumbHeight;
      Runnable imageLoader = new Runnable()
      {
        public void run()
        {
          // Check first if the request still needs to be performed
          if (thumbLoadNotifiers != null && thumbLoadNotifiers.size() > 0)
          {
            boolean needLoad = false;
            Vector<ResourceLoadListener> orgNotifiers;
            // NOTE: We cannot hold the thumbLock and then call loadStillNeeded because it can deadlock
            // against calls coming into getThumbnail. If the listener list changes while we're checking it w/out
            // the lock then do this over again.
            while (true)
            {
              synchronized (thumbLock)
              {
                orgNotifiers = new Vector<ResourceLoadListener>(thumbLoadNotifiers);
              }
              for (int i = 0; i < orgNotifiers.size(); i++)
              {
                if (thumbLoadNotifiers.get(i).loadStillNeeded(MediaFile.this))
                {
                  needLoad = true;
                  break;
                }
              }
              synchronized (thumbLock)
              {
                if (needLoad || orgNotifiers.size() == thumbLoadNotifiers.size())
                {
                  if (!needLoad)
                  {
                    if (Sage.DBG) System.out.println("Aborting thumbnail load of " + MediaFile.this + " because nothing is displaying it anymore");
                    thumbnailLoadState = 0;
                    thumbLoadNotifiers = null;
                    thumbLock.notify();
                    return;
                  }
                  break;
                }
              }
            }
          }
          // NARFLEX - 5/13/09 - Had a bug report where there was a 2 minute delay from a GC cycle we forced here; we really shouldn't
          // be forcing GCs since we know that only has a negative effect on performance (the only benefit is keeping down the max heap
          // size; but I don't think that's as important anymore)
          //Sage.gcPause();
          if (Sage.DBG) System.out.println("MediaFile creating thumbnail to " + imageThumbFile + " for " + MediaFile.this);
          if (isPicture())
          {
            if (!Sage.getBoolean("ui/disable_native_image_loader", false))
            {
              try
              {
                if (isLocalFile() || !Sage.client/*isTrueClient()*/) // optimize for pseudo-clients
                {
                  if (ImageLoader.createThumbnail(getFile(0).toString(),
                      imageThumbFile.toString(), finalThumbWidth, finalThumbHeight, MetaImage.getMetaImage(MediaFile.this).getRotation()))
                  {
                    thumbnailLoadState = 2;
                  }
                  else
                  {
                    System.out.println("ERROR internal error creating thumbnail file.");
                    thumbnailLoadState = 3;
                  }
                }
                else
                {
                  File tempFile = File.createTempFile("stv", ".img");
                  tempFile.deleteOnExit();
                  copyToLocalStorage(tempFile);
                  if (ImageLoader.createThumbnail(tempFile.toString(),
                      imageThumbFile.toString(), finalThumbWidth, finalThumbHeight, MetaImage.getMetaImage(MediaFile.this).getRotation()))
                  {
                    thumbnailLoadState = 2;
                  }
                  else
                  {
                    System.out.println("ERROR internal error creating thumbnail file.");
                    thumbnailLoadState = 3;
                  }
                  tempFile.delete();
                }
              }
              catch (Throwable t)
              {
                System.out.println("ERROR Error creating thumbnail file of: " + t);
                thumbnailLoadState = 3;
              }
              // In case we need to refresh what's in the cache due to an AR change
              MetaImage.clearFromCache(imageThumbFile);
              // Make sure the thumbnail's timestamp matches the picture's timestamp so
              // we can track regeneration correctly
              imageThumbFile.setLastModified(getRecordTime());
            }
            else
            {
              MetaImage myMeta = MetaImage.getMetaImage(MediaFile.this);
              Image fullImage = myMeta.getJavaImage();
              Sage.gcPause();
              BufferedImage thumbImage = ImageUtils.createBestOpaqueScaledImage(
                  fullImage, finalThumbWidth, finalThumbHeight);
              fullImage = null;
              myMeta.removeJavaRef(0);
              myMeta.releaseJavaImage(0);
              Sage.gcPause();
              try
              {
                javax.imageio.ImageIO.write(thumbImage, "jpg", imageThumbFile);
                thumbnailLoadState = 2;
              }
              catch (Exception e)
              {
                System.out.println("ERROR Error creating thumbnail file of: " + e);
                thumbnailLoadState = 3;
              }
              finally
              {
                thumbImage.flush();
                thumbImage = null;
                Sage.gcPause();
              }
              // In case we need to refresh what's in the cache due to an AR change
              MetaImage.clearFromCache(imageThumbFile);
              // Make sure the thumbnail's timestamp matches the picture's timestamp so
              // we can track regeneration correctly
              imageThumbFile.setLastModified(getRecordTime());
            }
          }
          else
          {
            String pathString;
            File unmountDir = null;
            if (isBluRay())
            {
              File bdmvDir = getFile(0);
              // We need to find the main movie and then extract it from there
              // Check for ISO mounting
              boolean skipThumbGen = false;
              if (bdmvDir.isFile())
              {
                unmountDir = FSManager.getInstance().requestISOMount(bdmvDir, loadNotifier == null ? null : loadNotifier.getUIMgr());
                if (unmountDir == null)
                {
                  if (Sage.DBG) System.out.println("FAILED mounting ISO image for BluRay thumbnail generation");
                  skipThumbGen = true;
                }
                else
                {
                  if (new File(unmountDir, "bdmv").isDirectory())
                    bdmvDir = new File(unmountDir, "bdmv");
                  else if (new File(unmountDir, "BDMV").isDirectory())
                    bdmvDir = new File(unmountDir, "BDMV");
                  else
                    bdmvDir = unmountDir;
                }
              }
              if (skipThumbGen)
              {
                pathString = null;
                // Just let an exception occur below
              }
              else
              {
                BluRayParser mybdp = new BluRayParser(bdmvDir, Sage.client ? Sage.preferredServer : null);
                try
                {
                  mybdp.fullyAnalyze();
                  bdmvDir = new File(bdmvDir, "STREAM");
                  pathString = new File(bdmvDir, mybdp.getMainPlaylist().playlistItems[0].itemClips[0].clipName + (mybdp.doesUseShortFilenames() ? ".MTS" : ".m2ts")).getPath();
                }
                catch (IOException ioe)
                {
                  if (Sage.DBG) System.out.println("IO Error analyzing BluRay file structure of:" + ioe);
                  pathString = null;
                }
              }
            }
            else
            {
              pathString = getFile(0).toString();
            }
            if (pathString != null && Sage.client && (generalType != MEDIAFILE_LOCAL_PLAYBACK || !SageTV.hostname.equals(host)))
            {
              pathString = "stv://" + Sage.preferredServer + "/" + pathString;
            }
            else if (isRecording())
            {
              // This works around activeFile issues with FFMPEG because the MediaServer always knows that state
              pathString = "stv://localhost/" + pathString;
            }
            pathString = pathString == null ? null : IOUtils.getLibAVFilenameString(pathString);
            boolean deinterlaceGen = false;
            if (fileFormat != null)
            {
              VideoFormat vidForm = fileFormat.getVideoFormat();
              if (vidForm != null && vidForm.isInterlaced())
                deinterlaceGen = true;
            }
            String res = null;
            try
            {
              int targetTime = (getDuration(0) >= 30000 && !pathString.toLowerCase().endsWith(".flv") &&
                  !pathString.toLowerCase().endsWith(".ogm")) ?
                      Sage.getInt("video_thumbnail_extraction_time_offset_sec", 15) : 0;
              if (isTV())
              {
                // This is to account for any start padding that the show had so we take the thumbnail from after the show starts
                long targetOffset = (getContentAiring().getStartTime() - getStart(0))/1000;
                if (targetOffset + targetTime < getDuration(0)/1000 && targetOffset > 0)
                  targetTime = (int) (targetTime + targetOffset);
              }
              // For MPEG2 TS files, don't seek because it can cause issues if FFMPEG can't parse it well
              // Should work better in the newer version of FFMPEG now that we fixed MPEGTS seeking
              // We're disabling this again on EMBEDDED since this *should* work fine now; leaving it at zero caused lots of black
              // thumbnails since that would correspond with show transitions from the broadcast quite often.
              // However, not having it at zero causes CPU issues on embedded quite often...so just leave it there.
              res = execVideoThumbGen(pathString, deinterlaceGen, targetTime, finalThumbWidth, finalThumbHeight, true, imageThumbFile, -1);
              if ((!imageThumbFile.isFile() || imageThumbFile.length() == 0))
              {
                // Creation failed, but we used an offset time so try again without an offset
                res = execVideoThumbGen(pathString, deinterlaceGen, 0, finalThumbWidth, finalThumbHeight, false, imageThumbFile, -1);
              }
              thumbnailLoadState = 2;
            }
            catch (Exception e)
            {
              if (Sage.DBG) System.out.println("Error creating thumbnail from video file:" + MediaFile.this + " of " + e);
              thumbnailLoadState = 3;
            }
            if (unmountDir != null)
            {
              FSManager.getInstance().releaseISOMount(unmountDir);
            }
            if (thumbnailLoadState != 3 && (!imageThumbFile.isFile() || imageThumbFile.length() == 0))
            {
              if (Sage.DBG) System.out.println("Error creating thumbnail from video file:" + MediaFile.this + " res=" + res);
              thumbnailLoadState = 3;
            }
          }
          if (thumbLoadNotifiers != null)
          {
            Vector<ResourceLoadListener> needToBeNotified = null;
            synchronized (thumbLock)
            {
              needToBeNotified = thumbLoadNotifiers;
              thumbLoadNotifiers = null;
              thumbLock.notify();
            }
            Set<ResourceLoadListener> alreadyNotified = new HashSet<ResourceLoadListener>();
            for (int i = 0; i < needToBeNotified.size(); i++)
            {
              // Avoid notifying the same person more than once
              if (alreadyNotified.add(needToBeNotified.get(i)))
                needToBeNotified.get(i).loadFinished(MediaFile.this, thumbnailLoadState == 2);
            }
          }
        }
      };
      if (loadNotifier != null && loadNotifier.needsLoadCallback(this))
      {
        if (thumbLoadNotifiers == null)
          thumbLoadNotifiers = new Vector<ResourceLoadListener>();
        if (!thumbLoadNotifiers.contains(loadNotifier))
          thumbLoadNotifiers.add(loadNotifier);
      }
      synchronized (thumbGenList)
      {
        thumbGenList.insertElementAt(imageLoader, 0);
        if (thumbGenThread == null)
        {
          thumbGenThread = new Thread("ThumbnailGen")
          {
            public void run()
            {
              while (true)
              {
                Runnable nextRun = null;
                synchronized (thumbGenList)
                {
                  if (thumbGenList.isEmpty())
                  {
                    try
                    {
                      thumbGenList.wait();
                    }catch (InterruptedException e)
                    {}
                    continue;
                  }
                  nextRun = thumbGenList.remove(0);
                }
                try
                {
                  nextRun.run();
                }
                catch (Throwable th)
                {
                  if (Sage.DBG) System.out.println("ERROR with thumbnail gen thread of: " + th);
                  th.printStackTrace();
                }
              }
            }
          };
          thumbGenThread.setDaemon(true);
          thumbGenThread.setPriority(Thread.MIN_PRIORITY);
          thumbGenThread.start();
        }
        thumbGenList.notifyAll();
      }
      if (waitForLoad)
      {
        return getFinishedThumbGen(loadNotifier);
      }
      return new MetaImage.Waiter(getGenericImage(getMediaMask(), loadNotifier), MediaFile.this);
    }
  }

  public String execVideoThumbGen(String pathString, boolean deinterlace, float offsetTime, int width, int height, boolean skipNonKeys, File imageThumbFile)
  {
    return execVideoThumbGen(pathString, deinterlace, offsetTime, width, height, skipNonKeys, imageThumbFile, -1);
  }
  public String execVideoThumbGen(String pathString, boolean deinterlace, float offsetTime, int width, int height, File imageThumbFile)
  {
    return execVideoThumbGen(pathString, deinterlace, offsetTime, width, height, imageThumbFile, -1);
  }
  public String execVideoThumbGen(String pathString, boolean deinterlace, float offsetTime, int width, int height, File imageThumbFile,
      int targetStream)
  {
    return execVideoThumbGen(pathString, deinterlace, offsetTime, width, height, false, imageThumbFile, -1);
  }
  public String execVideoThumbGen(String pathString, boolean deinterlace, float offsetTime, int width, int height, boolean skipNonKeys, File imageThumbFile,
      int targetStream)
  {
    List<String> args = new ArrayList<String>(25);
    if (Sage.LINUX_OS || Sage.MAC_OS_X)
      args.add("nice");
    args.add(FFMPEGTranscoder.getTranscoderPath());
    if (offsetTime > 0)
    {
      args.add("-ss");
      args.add(Float.toString(offsetTime));
    }
    args.add("-y");
    if (Sage.WINDOWS_OS)
    {
      args.add("-priority");
      args.add("idle");
    }
    if (isRecording())
      args.add("-activefile");
    if (skipNonKeys)
    {
      args.add("-skip_frame");
      args.add("nokey");
      args.add("-skip_idct");
      args.add("nokey");
      args.add("-skip_loop_filter");
      args.add("nokey");
    }
    args.add("-i");
    args.add(pathString);
    args.add("-f");
    args.add("mjpeg");
    if (deinterlace)
      args.add("-deinterlace");
    // New version of FFMPEG requires using libavfilter for cropping
    args.add("-vf");
    args.add("crop=0:8:0:0,scale=" + width + ":" + height);
    args.add("-vframes");
    args.add("1");
    args.add("-an");
    // Our optimized mode sets skipNonKeys to true...but if that fails just grab the first frame so we don't do a huge search again
    if (skipNonKeys)
    {
      // I'm not sure if we even need to worry about energy so much as variance...that's where the detail in the thumbnail comes from is the contrast...
      //        args.add("-minpixenergy");
      // We're trying this at higher values since we're getting some bad thumbnails through in testing
      //        args.add("45");//args.add("35");//args.add("25");//args.add("20");
      args.add("-minpixvar");
      args.add("300");
      args.add("-minpixnumframes");
      args.add("150"); // used to be 600, but quite often this spends way too much time looking for a good frame
    }
    else
    {
      // This'll still do minimal keyframe obeyance which fixes some bad files such as: Y:\testcontent\_KRCA-DT_20080129_124117.mpg
      args.add("-minpixenergy");
      args.add("1");
    }
    if (targetStream >= 0)
    {
      args.add("-map");
      args.add("0:" + targetStream);
    }
    args.add("-vsync");
    args.add("0");
    args.add(IOUtils.getLibAVFilenameString(imageThumbFile.toString()));
    String res = IOUtils.exec(args.toArray(Pooler.EMPTY_STRING_ARRAY), true, true, true);
    return res;
  }

  public boolean isThumbnailEmbedded()
  {
    if (!hasSpecificThumbnail() || files.isEmpty())
      return false;
    return files.get(0).equals(thumbnailFile);
  }
  public BufferedImage loadEmbeddedThumbnail()
  {
    // JK: I'm not sure why I added the !thumbnailFile.isFile() conditional to this, but it breaks loading of
    // album art on the client so I'm disabling it. I think it was maybe some optimization I was trying to deal
    // with inaccessible network resources.
    if (!hasSpecificThumbnail() || thumbnailSize < 0/* || !thumbnailFile.isFile()*/)
      return null;
    byte[] imData = new byte[thumbnailSize];
    try
    {
      copyToLocalMemory(thumbnailFile, thumbnailOffset, thumbnailSize, imData);
      BufferedImage rv = ImageUtils.fullyLoadImage(imData, 0, imData.length);
      Sage.gc();
      return rv;
    }
    catch (Exception e)
    {
      System.out.println("ERROR loading thumbnail file:" + e + " for " + this);
    }
    return null;
  }
  public byte[] loadEmbeddedThumbnailData()
  {
    // JK: I'm not sure why I added the !thumbnailFile.isFile() conditional to this, but it breaks loading of
    // album art on the client so I'm disabling it. I think it was maybe some optimization I was trying to deal
    // with inaccessible network resources.
    if (!hasSpecificThumbnail())
      return null;
    byte[] imData = new byte[thumbnailSize];
    try
    {
      copyToLocalMemory(thumbnailFile, thumbnailOffset, thumbnailSize, imData);
      return imData;
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR loading thumbnail file:" + e + " for " + this);
    }
    return null;
  }

  // Return array is InputStream, Long where the Long says how much can be read from the stream
  // The caller MUST close the InputStream that is returned to it
  public Object[] loadEmbeddedThumbnailDataAsStream()
  {
    // JK: I'm not sure why I added the !thumbnailFile.isFile() conditional to this, but it breaks loading of
    // album art on the client so I'm disabling it. I think it was maybe some optimization I was trying to deal
    // with inaccessible network resources.
    if (!hasSpecificThumbnail())
      return null;
    if (isLocalFile())
    {
      long length = thumbnailSize;
      long offset = thumbnailOffset;
      if (length == 0)
      {
        offset = 0;
        length = (int)thumbnailFile.length();
      }
      FileInputStream fileIn = null;
      try
      {
        fileIn = new FileInputStream(thumbnailFile);
        fileIn.skip(offset);
      }
      catch (IOException e)
      {
        if (fileIn != null)
        {
          try{fileIn.close();}catch(Exception e1){}
        }
        return null;
      }
      return new Object[] { fileIn, new Long(length) };
    }
    else
    {
      byte[] imData = new byte[thumbnailSize];
      try
      {
        copyToLocalMemory(thumbnailFile, thumbnailOffset, thumbnailSize, imData);
        return new Object[] { new ByteArrayInputStream(imData), new Long(thumbnailSize) };
      }
      catch (Exception e)
      {
      }
      return null;
    }
  }

  // Return array is File, Long, Long where the Long says the offset & how much can be read from the stream
  public Object[] loadEmbeddedThumbnailDataAsLocalFile()
  {
    // JK: I'm not sure why I added the !thumbnailFile.isFile() conditional to this, but it breaks loading of
    // album art on the client so I'm disabling it. I think it was maybe some optimization I was trying to deal
    // with inaccessible network resources.
    if (!hasSpecificThumbnail())
      return null;
    if (isLocalFile())
    {
      long length = thumbnailSize;
      long offset = thumbnailOffset;
      if (length == 0)
      {
        offset = 0;
        length = (int)thumbnailFile.length();
      }
      return new Object[] { thumbnailFile, new Long(offset), new Long(length), Boolean.FALSE };
    }
    else
    {
      try
      {
        File tmpFile = File.createTempFile("stv", ".img");
        tmpFile.deleteOnExit();
        copyToLocalStorage(thumbnailFile, thumbnailOffset, thumbnailSize, tmpFile);
        return new Object[] { tmpFile, new Long(0), new Long(thumbnailSize), Boolean.TRUE };
      }
      catch (IOException ioe)
      {
        if (Sage.DBG) System.out.println("ERROR creating local temp copy of MediaFile of:" + ioe);
      }
      return null;
    }
  }

  public boolean hasFile(File testFile)
  {
    return files.contains(testFile);
  }

  public long getStreamBufferSize()
  {
    Long rv = streamBufferSizeMap.get(this);
    if (rv != null)
      return rv.longValue();
    else
      return Sage.getLong("default_pause_buffer_size_dtv", 5*16*1024*1024);
  }
  public void setStreamBufferSize(long x)
  {
    streamBufferSizeMap.put(this, new Long(x));
  }

  public boolean hasValidFileFormat()
  {
    if (isLiveStream() || isDVD()) return true;
    ContainerFormat testFormat = getFileFormat();
    if (testFormat == null) return false;
    if ((isTV() || (!files.isEmpty() && FileDownloader.isDownloading(files.get(0)) &&
        (MediaFormat.MPEG2_PS.equals(testFormat.getFormatName()) || MediaFormat.MPEG2_TS.equals(testFormat.getFormatName()))) ||
        (isLiveBufferedStream() && isVideo())) && (testFormat.getNumberOfStreams() == 0 ||
        (testFormat.getPrimaryVideoFormat().length() == 0 && !testFormat.hasAudioOnlyStream()) || (testFormat.getPrimaryAudioFormat().length() == 0 && !testFormat.hasVideoOnlyStream())))
      return false;
    return true;
  }

  public boolean isAudioOnlyTVFile()
  {
    ContainerFormat tempFormat = fileFormat;
    return tempFormat != null && tempFormat.hasAudioOnlyStream();
  }

  public void setMediafileFormat(ContainerFormat newFormat)
  {
    // Check to see if we've changed the stream format or IDs for any of the streams; and if so we should have anybody playing this file reload it.
    boolean majorChange = false;
    if (fileFormat != null)
    {
      if (newFormat.getNumberOfStreams() != fileFormat.getNumberOfStreams() ||
          newFormat.getNumAudioStreams() != fileFormat.getNumAudioStreams() ||
          newFormat.getNumVideoStreams() != fileFormat.getNumVideoStreams())
        majorChange = true;
      else
      {
        for (int i = 0; i < fileFormat.getNumberOfStreams(); i++)
        {
          BitstreamFormat oldStreamFormat = fileFormat.getStreamFormat(i);
          BitstreamFormat newStreamFormat = newFormat.getStreamFormat(i);
          if (!oldStreamFormat.getFormatName().equals(newStreamFormat.getFormatName()))
          {
            majorChange = true;
            break;
          }
          String oldID = oldStreamFormat.getId();
          String newID = newStreamFormat.getId();
          if (oldID != newID && (oldID == null || !oldID.equals(newID)))
          {
            majorChange = true;
            break;
          }
        }
      }
    }
    synchronized (formatLock)
    {
      fileFormat = newFormat;
      if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
        Wizard.getInstance().logUpdate(this, Wizard.MEDIAFILE_CODE);
    }
    if (majorChange)
    {
      if (Sage.DBG) System.out.println("MAJOR Change to file format detected...reload any active media players using: " + this);
      List<VideoFrame> activeVFs = VideoFrame.getVFsUsingMediaFile(this);
      for (int i = 0; i < activeVFs.size(); i++)
      {
        activeVFs.get(i).reloadFile();
      }
    }
  }

  public String getEncodedBy() { return encodedBy; }

  public void checkForSubtitles()
  {
    synchronized (formatLock)
    {
      if (Sage.client && generalType != MEDIAFILE_LOCAL_PLAYBACK)
      {
        // This will cause the server to do the format detect and then update this DB object before the call returns.
        SageTVConnection sn = NetworkClient.getSN();
        if (sn != null)
        {
          try
          {
            sn.requestAction("GetMediaFileFormatDescription", new Object[] { this, Boolean.TRUE });
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR executing server API call of: " + e);
          }
        }
      }
      else if (FormatParser.updateExternalSubs(getFile(0), fileFormat))
      {
        if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
        {
          Wizard.getInstance().logUpdate(this, Wizard.MEDIAFILE_CODE);
        }
      }
    }

  }

  public ContainerFormat getFileFormat()
  {
    if (formatDetectFailed && !isRecording() && (files.isEmpty() || !FileDownloader.isDownloading(files.get(0))))
      return fileFormat;
    if (fileFormat == null)
    {
      if (isDVD() || isAnyLiveStream() || files.size() < 1)
        return null; // we can't detect these from the file system at this time
      // NARFLEX - 5/12/09 - We can't do this inside the formatLock because it'll get the this lock which could
      // cause a potential deadlock since some calls go the other way (get this lock first then call getFileFormat)
      if (shouldIWaitForData())
        return fileFormat; // should be null; but just in case it gets set below from another thread somehow
      boolean updateDB = false;
      synchronized (formatLock)
      {
        if (fileFormat == null)
        {
          if (Sage.client)
          {
            // This will cause the server to do the format detect and then update this DB object before the call returns.
            SageTVConnection sn = NetworkClient.getSN();
            if (sn != null)
            {
              try
              {
                sn.requestAction("GetMediaFileFormatDescription", new Object[] { this });
              }
              catch (Exception e)
              {
                if (Sage.DBG) System.out.println("ERROR executing server API call of: " + e);
              }
            }
          }
          else
            fileFormat = FormatParser.getFileFormat(files.firstElement());
          if (fileFormat != null & generalType != MEDIAFILE_LOCAL_PLAYBACK)
            updateDB = true;
          if (fileFormat == null)
            formatDetectFailed = true;
        }
      }
      if (updateDB)
        Wizard.getInstance().logUpdate(this, Wizard.MEDIAFILE_CODE);
    }
    else if ((fileFormat.getNumberOfStreams() == 0 || (fileFormat.getPrimaryVideoFormat().length() == 0 && !fileFormat.hasAudioOnlyStream()) ||
        (fileFormat.getPrimaryAudioFormat().length() == 0 && !fileFormat.hasVideoOnlyStream())) && !shouldIWaitForData())
    {
      boolean downloadVideoCheck = !files.isEmpty() && FileDownloader.isDownloading(files.get(0)) &&
          (MediaFormat.MPEG2_PS.equals(fileFormat.getFormatName()) || MediaFormat.MPEG2_TS.equals(fileFormat.getFormatName()));
      if (isTV() || (isLiveBufferedStream() && isVideo()) || downloadVideoCheck)
      {
        CaptureDevice cdev = guessCaptureDeviceFromEncoding();
        if (downloadVideoCheck || (cdev != null && cdev.isDynamicFormatEncoder()))
        {
          // This is from network encoder or other source that isn't providing us with the stream format
          // data, so set it ourself.
          if (Sage.DBG) System.out.println("Doing pre-emptive file format detection on recording MediaFile...." + files.firstElement());
          boolean updateDB = false;
          // See note in above block about the sync block for this call
          if (shouldIWaitForData())
            return fileFormat;
          synchronized (formatLock)
          {
            if (fileFormat.getNumberOfStreams() == 0 || (fileFormat.getPrimaryVideoFormat().length() == 0 && !fileFormat.hasAudioOnlyStream()) ||
                (fileFormat.getPrimaryAudioFormat().length() == 0 && !fileFormat.hasVideoOnlyStream()))
            {
              if (Sage.client && !downloadVideoCheck)
              {
                // This will cause the server to do the format detect and then update this DB object before the call returns.
                SageTVConnection sn = NetworkClient.getSN();
                if (sn != null)
                {
                  try
                  {
                    sn.requestAction("GetMediaFileFormatDescription", new Object[] { this });
                  }
                  catch (Exception e)
                  {
                    if (Sage.DBG) System.out.println("ERROR executing server API call of: " + e);
                  }
                }
              }
              else
              {
                ContainerFormat newFileFormat = FormatParser.getFileFormat(files.firstElement());
                if (newFileFormat != null)
                {
                  fileFormat = newFileFormat;
                  if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
                    updateDB = true;
                }
              }
            }
          }
          if (downloadVideoCheck && isMusic())
          {
            // We may have gotten this wrong initially where we tagged it as music, but now we found a video stream
            if (fileFormat.getPrimaryVideoFormat().length() > 0)
            {
              if (Sage.DBG) System.out.println("Downloaded file progress was initially tagged as music...updating to video now that we found a video stream for:" + this);
              setMediaMask(((getMediaMask() & ~MEDIA_MASK_MUSIC) | MEDIA_MASK_VIDEO) & DBObject.MEDIA_MASK_ALL);
              updateDB = true;
            }
          }
          if (updateDB)
            Wizard.getInstance().logUpdate(this, Wizard.MEDIAFILE_CODE);

          if (!downloadVideoCheck && !isRecording() && (fileFormat.getNumberOfStreams() == 0 || (fileFormat.getPrimaryVideoFormat().length() == 0 && !fileFormat.hasAudioOnlyStream()) ||
              (fileFormat.getPrimaryAudioFormat().length() == 0 && !fileFormat.hasVideoOnlyStream())) &&
              getContentAiring().getSchedulingEnd() < Sage.time()) // Don't set it as a failure unless we're past the end time in case this is called during a segment transition, or after a partial
          {
            // We're done recording but still didn't get a proper format detection on this file; don't try to do it again
            if (Sage.DBG) System.out.println("Marking format detection as failed for:" + this);
            formatDetectFailed = true;
          }

        }
      }
    }
    return fileFormat;
  }

  public String getPrimaryVideoFormat()
  {
    getFileFormat();
    if (fileFormat != null)
      return fileFormat.getPrimaryVideoFormat();
    else
      return "";
  }

  public String getPrimaryAudioFormat()
  {
    getFileFormat();
    if (fileFormat != null)
      return fileFormat.getPrimaryAudioFormat();
    else
      return "";
  }

  public String getContainerFormat()
  {
    getFileFormat();
    if (fileFormat != null)
      return fileFormat.getFormatName();
    else
      return "";
  }

  public static byte getLegacyMediaSubtype(ContainerFormat cf)
  {
    if (cf != null)
    {
      String container = cf.getFormatName();
      if (MediaFormat.AVI.equals(container))
        return MEDIASUBTYPE_AVI;
      else if (MediaFormat.JPEG.equals(container))
        return MEDIASUBTYPE_JPEG;
      else if (MediaFormat.GIF.equals(container))
        return MEDIASUBTYPE_GIF;
      else if (MediaFormat.PNG.equals(container))
        return MEDIASUBTYPE_PNG;
      else if (MediaFormat.MP3.equals(container))
        return MEDIASUBTYPE_MP3;
      else if (MediaFormat.MPEG1.equals(container))
        return MEDIASUBTYPE_MPEG1_PS;
      else if (MediaFormat.WAV.equals(container))
        return MEDIASUBTYPE_WAV;
      else if (MediaFormat.MPEG2_TS.equals(container))
        return MEDIASUBTYPE_MPEG2_TS;
      else if (MediaFormat.MPEG2_PES_VIDEO.equals(container) ||
          MediaFormat.MPEG2_PES_AUDIO.equals(container))
        return MEDIASUBTYPE_MPEG2_PES;
      else if (MediaFormat.ASF.equals(container))
        return MEDIASUBTYPE_WMA;
      else if (MediaFormat.MPEG2_PS.equals(container))
      {
        if (MediaFormat.MPEG4X.equals(cf.getPrimaryVideoFormat()))
          return MEDIASUBTYPE_MPEG2_PS_MPEG4_VIDEO;
        else
          return MEDIASUBTYPE_MPEG2_PS;
      }
    }
    return MEDIASUBTYPE_NULL;
  }
  public byte getLegacyMediaSubtype()
  {
    getFileFormat();
    if (fileFormat != null)
    {
      return getLegacyMediaSubtype(fileFormat);
    }
    return MEDIASUBTYPE_NULL;
  }
  public byte getLegacyMediaType()
  {
    if (isVideo())
      return MEDIATYPE_VIDEO;
    else if (isMusic())
      return MEDIATYPE_AUDIO;
    else if (isPicture())
      return MEDIATYPE_PICTURE;
    else if (isDVD())
      return MEDIATYPE_DVD;
    else if (isBluRay())
      return MEDIATYPE_BLURAY;
    else
      return MEDIATYPE_NULL;
  }

  public float getPrimaryVideoAspectRatio()
  {
    return getPrimaryVideoAspectRatio(true);
  }
  public float getPrimaryVideoAspectRatio(boolean forceFormatDetect)
  {
    if (forceFormatDetect)
      getFileFormat();
    if (fileFormat == null)
      return 0;
    for (int i = 0; i < fileFormat.getNumberOfStreams(); i++)
    {
      BitstreamFormat bf = fileFormat.getStreamFormat(i);
      if (bf instanceof VideoFormat)
      {
        VideoFormat vidform = (VideoFormat) bf;
        float ar = vidform.getAspectRatio();
        if (ar == 0 && vidform.getHeight() > 0)
        {
          ar = ((float)vidform.getWidth()) / vidform.getHeight();
        }
        return ar;
      }
    }
    return 0;
  }

  public CaptureDevice guessCaptureDeviceFromEncoding()
  {
    String[] allConns = MMC.getInstance().getCaptureDeviceNames();
    CaptureDevice matchingDevice = null;
    int maxMatchLen = 0;
    for (int i = 0; i < allConns.length; i++)
    {
      if (encodedBy != null && encodedBy.startsWith(allConns[i]))
      {
        if (allConns[i].length() > maxMatchLen)
        {
          CaptureDevice cdev = MMC.getInstance().getCaptureDeviceNamed(allConns[i]);
          if (cdev != null)
          {
            matchingDevice = cdev;
            maxMatchLen = allConns[i].length();
          }
        }
      }
    }
    return matchingDevice;
  }

  // This is called on local MediaFile objects when they should no longer be needed. This will remove any special
  // mounting/access that took place. It has no effect on non-local MediaFiles.
  public void cleanupLocalFile()
  {
    if (generalType != MEDIAFILE_LOCAL_PLAYBACK)
      return;

    if (Sage.DBG) System.out.println("cleanupLocalFile called for " + this);
    if (Sage.client && !SageTV.hostname.equals(host) && host.indexOf("://") == -1)
    {
      // The client is playing back a file from the server which is not URL based; so notify
      // the SageTV server that we no longer need access to this path
      SageTVConnection sn = NetworkClient.getSN();
      if (sn != null)
      {
        try
        {
          sn.requestMediaServerAccess(files.get(0), false);
        }
        catch (Exception e)
        {
          if (Sage.DBG) System.out.println("ERROR clearing special file access on server of:" + e);
        }
      }
    }
    else if (host.startsWith("smb://"))
    {
      FSManager.getInstance().releaseLocalSMBAccess(host);
    }
  }

  private void addTimes(long start, long stop)
  {
    long[] newTimes = new long[times.length + 2];
    if (times.length > 0)
      System.arraycopy(times, 0, newTimes, 0, times.length);
    times = newTimes;
    times[times.length - 2] = start;
    times[times.length - 1] = stop;
  }

  private void removeTimes(int index)
  {
    long[] newTimes = new long[times.length - 2];
    if (newTimes.length > 0)
    {
      if (index > 0)
        System.arraycopy(times, 0, newTimes, 0, index*2);
      if (index*2 + 2 < times.length)
        System.arraycopy(times, index*2 + 2, newTimes, index, times.length - index*2 - 2);
    }
    times = newTimes;
  }

  public String getVideoDirectory()
  {
    return videoDirectory;
  }


  Airing myAiring;
  String videoDirectory;
  String name;
  boolean archive;
  int infoAiringID;
  String host;
  String encodedBy;

  byte generalType;
  byte acquisitionTech;

  long thumbnailOffset;
  int thumbnailSize;
  File thumbnailFile;
  boolean forcedComplete;
  Vector<File> files;
  private long[] times;
  private int createWatchCount;

  private int thumbnailLoadState;
  private Object thumbLock = new Object();
  private static final Vector<Runnable> thumbGenList = new Vector<Runnable>();
  private static Thread thumbGenThread;

  private Object formatLock = new Object();
  private boolean formatDetectFailed = false;

  private static Map<MediaFile, Long> streamBufferSizeMap = new WeakHashMap<MediaFile, Long>();

  private Vector<ResourceLoadListener> thumbLoadNotifiers;

  // This is null if we haven't parsed it yet or can't figure it out
  ContainerFormat fileFormat;

  // This is null if we have not guaranteed anyone what the next segment file name will be. This is
  // the object returned when we need the next segment name before we actually want to add a new
  // segment to the MediaFile.
  private NextSegmentGuarantee nextSegmentGuarantee;

  public static final Comparator<MediaFile> AIRING_ID_COMPARATOR =
      new Comparator<MediaFile>()
  {
    public int compare(MediaFile m1, MediaFile m2)
    {
      if (m1 == m2)
        return 0;
      else if (m1 == null)
        return 1;
      else if (m2 == null)
        return -1;

      return (m1.infoAiringID != m2.infoAiringID)
          ? m1.infoAiringID - m2.infoAiringID
          : m1.id - m2.id;
    }
  };

  public static final Comparator<MediaFile> FILE_PATH_COMPARATOR =
      new Comparator<MediaFile>()
  {
    public int compare(MediaFile m1, MediaFile m2)
    {
      if (m1 == m2)
        return 0;
      else if (m1 == null)
        return 1;
      else if (m2 == null)
        return -1;

      if (m1.files.size() == 0)
        return (m2.files.size() == 0) ? 0 : 1;
      else if (m2.files.size() == 0)
        return -1;

      File f1 = m1.files.get(0);
      File f2 = m2.files.get(0);
      return f1.compareTo(f2);
    }
  };

  public class FakeAiring extends Airing
  {
    public FakeAiring(int fakeID)
    {
      super(fakeID);
      duration = getRecordDuration();
      time = getRecordTime();
      if (isAnyLiveStream())
      {
        time = Sage.time();
        duration = 10*365*Sage.MILLIS_PER_DAY;
      }
      stationID = 0;
      if (MediaFile.this.infoAiringID != 0 && MediaFile.this.infoAiringID != MediaFile.this.id)
      {
        Airing testAir = Wizard.getInstance().getAiringForID(infoAiringID, false, true);
        if (testAir != null)
          showID = testAir.showID;
      }
    }

    public String getPartialString()
    {
      StringBuffer sb = new StringBuffer();
      Airing infoAir = (infoAiringID == id) ? getLiveMatchedAiring() : Wizard.getInstance().getAiringForID(infoAiringID, false, true);
      if (infoAir != null)
      {
        return infoAir.getPartialString();
      }
      sb.append(getName());
      sb.append('\n');
      sb.append(Sage.rez("Duration_At_Time", new Object[] { Sage.durFormatHrMinPretty(duration),
          ZClock.getSpecialTimeString(new Date(time)) }));
      return sb.toString();
    }

    public String getTitle()
    {
      if (infoAiringID != id && infoAiringID != 0)
      {
        Airing infAir = Wizard.getInstance().getAiringForID(infoAiringID, false, true);
        if (infAir != null)
          return infAir.getTitle();
      }
      else
      {
        Airing infAir = getLiveMatchedAiring();
        if (infAir != null)
          return infAir.getTitle();
      }
      return getName();
    }

    public String toString()
    {
      return "FA[" + super.toString() + ']';
    }

    public MediaFile getMediaFile() { return MediaFile.this; }

    public String getChannelNum(long providerID)
    {
      if (isAnyLiveStream())
      {
        CaptureDeviceInput cdi = MMC.getInstance().getCaptureDeviceInputNamed(encodedBy);
        if (cdi != null)
        {
          int stationIDGuess = EPG.getInstance().guessStationIDFromPhysicalChannel(cdi.getProviderID(), cdi.getChannel(), false);
          String guessRV = EPG.getInstance().getChannel(cdi.getProviderID(), stationIDGuess);
          if (guessRV != null && guessRV.length() > 0)
            return guessRV;
          return cdi.getChannel();
        }
        return "";
      }
      return super.getChannelNum(providerID);
    }

    public Channel getChannel()
    {
      if (isAnyLiveStream())
      {
        CaptureDeviceInput cdi = MMC.getInstance().getCaptureDeviceInputNamed(encodedBy);
        if (cdi != null)
        {
          String currNum = cdi.getChannel();
          int guessedStationID = EPG.getInstance().guessStationIDFromPhysicalChannel(cdi.getProviderID(), currNum, false);
          return Wizard.getInstance().getChannelForStationID(guessedStationID);
        }
        else
          return null;
      }
      else
        return super.getChannel();
    }

    public int getStationID()
    {
      if (isAnyLiveStream())
      {
        CaptureDeviceInput cdi = MMC.getInstance().getCaptureDeviceInputNamed(encodedBy);
        if (cdi != null)
        {
          String currNum = cdi.getChannel();
          return EPG.getInstance().guessStationIDFromPhysicalChannel(cdi.getProviderID(), currNum, false);
        }
        else
          return super.getStationID();
      }
      else
        return super.getStationID();
    }

    public String getFullString()
    {
      StringBuffer sb;
      Airing infoAir = (infoAiringID != id) ? Wizard.getInstance().getAiringForID(infoAiringID, false, true) :
        getLiveMatchedAiring();
      if (infoAir != null)
      {
        sb = new StringBuffer(infoAir.getFullString());
      }
      else
        sb = new StringBuffer(getName());
      sb.append('\n');
      sb.append(Sage.durFormatHrMinPretty(duration));
      return sb.toString();
    }

    public Show getShow()
    {
      if (isAnyLiveStream())
      {
        Airing a = getLiveMatchedAiring();
        if (a != null)
          return a.getShow();
      }
      return super.getShow();
    }

    public long getSchedulingStart() { return time; }

    public long getSchedulingDuration() { return duration; }

    public long getSchedulingEnd() { return time + duration; }

    void setPersist(byte how){}

    private Airing getLiveMatchedAiring()
    {
      if (isAnyLiveStream())
      {
        CaptureDeviceInput cdi = MMC.getInstance().getCaptureDeviceInputNamed(encodedBy);
        if (cdi != null)
        {
          String currNum = cdi.getChannel();
          int guessedStationID = EPG.getInstance().guessStationIDFromPhysicalChannel(cdi.getProviderID(), currNum, false);
          Airing[] theAirs = Wizard.getInstance().getAirings(guessedStationID, Sage.time(), Sage.time() + 1, false);
          if (theAirs.length > 0)
            return theAirs[0];
        }
      }
      return null;
    }
  }

  public class NextSegmentGuarantee
  {
    private final File nextSegment;
    private final long currTime;

    private NextSegmentGuarantee(File nextSegment, long currTime)
    {
      this.nextSegment = nextSegment;
      this.currTime = currTime;
    }

    public File getNewSegment()
    {
      return nextSegment;
    }

    public String getNextSegment()
    {
      return nextSegment.toString();
    }

    public long getStartTime()
    {
      return currTime;
    }
  }
}
