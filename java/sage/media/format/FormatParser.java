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
package sage.media.format;

/**
 *
 * @author Narflex
 */
public class FormatParser
{
  public static final String[][] FORMAT_SUBSTITUTIONS = {
    { "mov,mp4,m4a,3gp,3g2,mj2", MediaFormat.QUICKTIME },
    { "mpeg4aac", MediaFormat.AAC },
    { "mp4a / 0x6134706D", MediaFormat.AAC },
    // mpeg we do ourself to split it up into MPEG1 and MPEG2-PS; we also do better TS detection as well
    { "mpegts", MediaFormat.MPEG2_TS },
    { "mpeg2video", MediaFormat.MPEG2_VIDEO },
    { "mpeg1video", MediaFormat.MPEG1_VIDEO },
    { "mpeg4", MediaFormat.MPEG4_VIDEO },
    { "wmav1", MediaFormat.WMA7 },
    { "wmav2", MediaFormat.WMA8 }, // this is V8 or V9 lossy
    { "0x0163", MediaFormat.WMA9LOSSLESS }, // can't play it back with FFMPEG, but it can detect it
    { "c[1][0][0] / 0x0163", MediaFormat.WMA9LOSSLESS }, // can't play it back with FFMPEG, but it can detect it
    { "WMV3 / 0x33564D57", MediaFormat.WMV9 },
    { "[0][0][0][0] / 0x0000", "0X0000" }, // cleanup
    { "wmv3", MediaFormat.WMV9 },
    { "wmv2", MediaFormat.WMV8 },
    { "wmv1", MediaFormat.WMV7 },
    { "flv", MediaFormat.FLASH_VIDEO },
    { "h264", MediaFormat.H264 },
    { "ogg", MediaFormat.OGG },
    { "vorbis", MediaFormat.VORBIS },
    { "0x0162", MediaFormat.WMA_PRO },
    { "libfaad", MediaFormat.AAC },
    { "truehd", MediaFormat.DOLBY_HD },
    { "ac3_truehd", MediaFormat.DOLBY_HD },
    { "dts_hd", MediaFormat.DTS_HD },
    { "dts_ma", MediaFormat.DTS_MA },
    { "ec-3 / 0x332D6365", MediaFormat.EAC3 },
    { "PCM_BLURAY", "PCM_BD" },
    { "OG[0][0] / 0X674F", MediaFormat.VORBIS },
    { "dtsh / 0x68737464", MediaFormat.DTS_HD },
    { "dtsl / 0x6c737464", MediaFormat.DTS_MA },
    { "dtsc / 0x63737464", MediaFormat.DTS },
    { "mlpa / 0x61706c6d", MediaFormat.DOLBY_HD },
    { "AC-3 / 0X332D6361", MediaFormat.AC3 },
  };

  private static FormatParserPlugin formatParserPluginInstance;
  private static Object formatParserPluginLock = new Object();
  private static final long MPEG_PARSER_SEARCH_LENGTH = 30*1024*1024;
  private static boolean DISABLE_FORMAT_DETECTION = false;
  private static boolean MINIMIZE_EXIF_MEM_USAGE = false;
  public static final java.io.File FORMAT_DETECT_MOUNT_DIR = new java.io.File("/tmp/formatmount");
  public static String substituteName(String s)
  {
    if (s == null) return null;
    for (int i = 0; i < FORMAT_SUBSTITUTIONS.length; i++)
      if (FORMAT_SUBSTITUTIONS[i][0].equalsIgnoreCase(s))
        return FORMAT_SUBSTITUTIONS[i][1];
    return s.toUpperCase();
  }
  public static ContainerFormat getFileFormat(java.io.File f)
  {
    if (!f.isFile())
    {
      // Check for a BluRay folder structure
      if (f.isDirectory() && (f.getName().equalsIgnoreCase(sage.Seeker.BLURAY_VOLUME_SECRET) ||
          (new java.io.File(f, "index.bdmv").isFile() && new java.io.File(f, "MovieObject.bdmv").isFile())))
      {
        return parseBluRayFormat(f);
      }
      return null;
    }
    if (f.length() == 0 && (!sage.MMC.getInstance().isRecording(f) || sage.MMC.getInstance().getRecordedBytes(f) == 0))
    {
      if (sage.Sage.DBG) System.out.println("Returning null format for zero-length file");
      return null;
    }
    try
    {
      ContainerFormat format = new ContainerFormat();
      String easyFormat = null;
      boolean foundEasy = false;
      ContainerFormat myFormat = null;
      if (foundEasy = setContainerTypeEasy(f, format))
      {
        // If it's an image format, then just stop now.
        easyFormat = format.getFormatName();
        if (easyFormat != null && (MediaFormat.BMP.equals(easyFormat) ||
            MediaFormat.GIF.equals(easyFormat) || MediaFormat.JPEG.equals(easyFormat) ||
            MediaFormat.PNG.equals(easyFormat) || MediaFormat.TIFF.equals(easyFormat) ||
            MediaFormat.BMP.equals(easyFormat) ||
            MediaFormat.SMIL.equals(easyFormat) || MediaFormat.SWF.equals(easyFormat)))
        {
          addAdditionalMetadata(f, format);
          return format;
        }
      }
      // Check for MP3 files since we can parse those fast in Java
      ContainerFormat mp3Format = MP3Parser.parseMP3File(f);
      if (mp3Format != null)
      {
        // Our MP3 parser figured it out. :)
        addAdditionalMetadata(f, mp3Format);
        if (sage.Sage.DBG) System.out.println("File Format-MP3 parsed " + f + "=" + mp3Format);
        return mp3Format;
      }
      if (DISABLE_FORMAT_DETECTION)
        return foundEasy ? format : null;
      String lcfname = f.toString().toLowerCase();

      // Don't try to format detect ISO files; except BluRay
      if (lcfname.endsWith(".iso"))
      {
        synchronized (FORMAT_DETECT_MOUNT_DIR)
        {
          java.io.File mountDir = sage.FSManager.getInstance().requestISOMount(f, (java.io.File)null);
              if (mountDir != null)
              {
                // Check for a BluRay folder structure
                java.io.File bdDir = null;
                boolean isBluRay = (bdDir = new java.io.File(mountDir, "BDMV")).isDirectory() || (bdDir = new java.io.File(mountDir, "bdmv")).isDirectory();
                boolean isDVD = false;
                ContainerFormat rv = null;
                if (!isBluRay)
                {
                  // Check for a DVD
                  if (new java.io.File(mountDir, "VIDEO_TS").isDirectory() ||
                      new java.io.File(mountDir, "video_ts").isDirectory())
                  {
                    isDVD = true;
                    format.setFormatName("DVD");
                    rv = format;
                  }
                }
                else
                {
                  rv = parseBluRayFormat(bdDir);
                }
                sage.FSManager.getInstance().releaseISOMount(mountDir);
                return rv;
              }
              return null;
        }
      }


      // Only do our MPEG detection if we don't know the type yet; or we know it's MPEG
      ContainerFormat internalParsedAudioOnlyFormat = null;
      if (!lcfname.endsWith(".evo") && !lcfname.endsWith(".tivo") && (!foundEasy || (MediaFormat.MPEG2_PS.equals(easyFormat) || MediaFormat.MPEG2_TS.equals(easyFormat) ||
          MediaFormat.MPEG1.equals(easyFormat) || MediaFormat.MPEG2_PES_VIDEO.equals(easyFormat) ||
          MediaFormat.MPEG2_PES_VIDEO.equals(easyFormat)))
          && !sage.Sage.getBoolean("skip_internal_format_parser",false))
      {
        if (sage.Sage.DBG) System.out.println("Using internal format detector first for: " + f);
        if ((myFormat = extractMyFormat(f)) != null)
        {
          if (myFormat.getNumberOfStreams() > 0)
          {
            if (sage.Sage.DBG) System.out.println("File Format Parsed-2a " + f + "=" + myFormat);
            addAdditionalMetadata(f, myFormat);
            if (sage.Sage.DBG) System.out.println("File Format Parsed-2b " + f + "=" + myFormat);
            if (myFormat.hasAudioOnlyStream() && sage.Sage.getBoolean("double_check_audioonly_tv_formats", true)) {
              internalParsedAudioOnlyFormat = myFormat;
              myFormat = null;
              if (sage.Sage.DBG) System.out.println("Using external format detector because we only detected audio on the internal format...");
            } else {
              return myFormat;
            }
          }
          else
          {
            System.out.println("Re-detecting file format due to bad MPEG detection, file=" + f + " formatDetected=" + myFormat);
          }
        }
      }

      FormatParserPlugin plugin = getFormatParserPluginInstance();
            
      if (plugin != null)
      {
        if (sage.Sage.DBG) System.out.println("Using the format detector plugin");
        
        try
        {
          ContainerFormat pluginFormat = plugin.parseFormat(f);
            
          if (pluginFormat != null && pluginFormat.streamFormats != null && pluginFormat.streamFormats.length > 0)
          {
            addAdditionalMetadata(f, pluginFormat);
            return pluginFormat;
          }
        }
        catch (Throwable ex)
        {
          System.out.println("Error in Format Detector Plugin: " + ex.getMessage());
          ex.printStackTrace();
        }
      }
      
      if (sage.Sage.DBG) System.out.println("Now using external format detector for: " + f);
      String ffmpegInfo = getFFMPEGFormatInfo(f.toString());
      if ("TRUE".equals(sage.Sage.get("debug_ffmpeg_format_info", null)))
        System.out.println("File:" + f + " len=" + f.length() + " FFMPEG Info:" + ffmpegInfo);
      String formatName = extractContainerFromFFMPEGInfo(ffmpegInfo);
      if (formatName == null)
      {
        // This is not a format that FFMPEG understands. Maybe it's one that we do.
        if (myFormat != null)
        {
          addAdditionalMetadata(f, myFormat);
          if (sage.Sage.DBG) System.out.println("File Format Parsed-1 " + f + "=" + myFormat);
          return myFormat;
        }
        if (!foundEasy)
        {
          System.out.println("FORMATERROR UNABLE TO PARSE FILE TYPE FOR FILE-1: "+ f);
          // See if we can at least extract some metadata from a plugin
          if (addAdditionalMetadata(f, format))
            return format;
          else
            return null;
        }
        else
          addAdditionalMetadata(f, format);
      }
      else
      {
        format.setFormatName(substituteName(formatName));
        if (foundEasy && !"Audio".equals(easyFormat)) // don't allow the generic "Audio" type through, this just means ID3 tagged
          format.setFormatName(easyFormat);
        format.setDuration(extractDurationFromFFMPEGInfo(ffmpegInfo));
        format.setBitrate(extractContainerBitrateFromFFMPEGInfo(ffmpegInfo));
        format.setStreamFormats(extractStreamFormatsFromFFMPEGInfo(ffmpegInfo));
        format.setDRMProtection(extractDRMFromFFMPEGInfo(ffmpegInfo));
        java.util.regex.Matcher metaMat = ffmpegMetadataPat.matcher(ffmpegInfo);
        while (metaMat.find())
        {
          // Check for character conversion stuff...usually it's UTF-8, but if there's
          // invalid chars in that conversion then try doing no conversion
          String rawProp = metaMat.group(2);
          String utf8Conv = rawProp;
          try
          {
            byte[] rawChars = new byte[rawProp.length()];
            for (int i = 0; i < rawChars.length; i++)
              rawChars[i] = (byte)((rawProp.charAt(i)) & 0xFF);
            utf8Conv = new String(rawChars, "UTF-8");
            for (int i = 0; i < utf8Conv.length(); i++)
            {
              if ((utf8Conv.charAt(i) & 0xFFFF) >= 0xFFFD)
              {
                // Invalid character so it's probably not UTF-8
                utf8Conv = rawProp;
                break;
              }
            }
          }
          catch (java.io.UnsupportedEncodingException uee){}
          // Don't override initial data with repeats; this happens with multi-audio tracks and chapters with unique titles
          // There's a special case with WM/TrackNumber where it also occurs alongside WM/Track sometimes; TrackNumber is the
          // preferred one to use, but we map them both to track so we need to check for that case.
          format.addMetadata(metaMat.group(1), utf8Conv, "WM/TrackNumber".equalsIgnoreCase(metaMat.group(1)));//new String(metaMat.group(2).getBytes(), "UTF-8"));
        }
        addAdditionalMetadata(f, format);
        if (MediaFormat.MPEG2_PS.equals(format.getFormatName()) || MediaFormat.MPEG2_TS.equals(format.getFormatName()))
        {
          if (sage.Sage.DBG) System.out.println("WARNING: Native format detection failed for MPEG file " + f + " of format" +
              format);
          if ((sage.MMC.getInstance().isRecording(f) || sage.FileDownloader.isDownloading(f)) &&
              (internalParsedAudioOnlyFormat == null || format.getNumVideoStreams() == 0) &&
              !sage.Sage.getBoolean("skip_internal_format_parser",false))
          {
            // If we had an audio-only detection, and FFMPEG also gave us audio only, then return that format. Revert to usual
            // behavior of returning null format if we are not falling into this special case.
            if (internalParsedAudioOnlyFormat != null) {
              if (sage.Sage.DBG) System.out.println("Returning internally detected audio only format because FFMPEG also did not detect a video stream");
              return internalParsedAudioOnlyFormat;
            }

            // Return null here so we get another go at this...what likely happened was that we couldn't detect it due to not enough data,
            // and then we let FFMPEG take a crack at it and by then there was enough data in the file.
            if (sage.Sage.DBG) System.out.println("Returning NULL format due to internal detection failure since redoing it will likely fix it...");
            return null;
          }
          // Check or 192 byte TS file
          if (MediaFormat.MPEG2_TS.equals(format.getFormatName()))
          {
            java.io.InputStream inStream = null;
            String fstr = f.toString();
            try
            {
              inStream = new java.io.FileInputStream(fstr);
              byte[] readBuf = new byte[390];
              inStream.read(readBuf);
              if (readBuf[4] == 0x47 && readBuf[196] == 0x47 && readBuf[388] == 0x47)
              {
                if (sage.Sage.DBG) System.out.println("Detected 192 byte packet size in TS file");
                format.setPacketSize(192);
              }
            }
            catch (java.io.IOException e)
            {}
            finally
            {
              if (inStream != null)
              {
                try{inStream.close(); }catch(Exception exc){}
              }
            }
          }
        }
      }
      if (sage.Sage.DBG) System.out.println("File Format Parsed " + f + "=" + format);
      return format;
    }
    catch (Throwable t)
    {
      if (sage.Sage.DBG) System.out.println("ERROR parsing media file " + f + " of:" + t);
      t.printStackTrace(System.out);
      return null;
    }
  }

  private static boolean addAdditionalMetadata(java.io.File f, ContainerFormat format)
  {
    // See if there's any ID3 data on the file
    boolean rv = false;
    if (sage.media.format.MediaFormat.JPEG.equals(format.getFormatName()))
    {
      try
      {
        ReadMetadata exifParser = new ReadMetadata(f);
        if (!DISABLE_FORMAT_DETECTION && !MINIMIZE_EXIF_MEM_USAGE)
          format.addMetadata(sage.media.format.MediaFormat.META_DESCRIPTION, exifParser.toString());
        format.addMetadata(sage.media.format.MediaFormat.META_TRACK, Integer.toString(exifParser.getImageOrientationAsInt()));
        //				format.addMetadata(sage.media.format.MediaFormat.META_WIDTH, Integer.toString(exifParser.getImageDimensions()[0]));
        //				format.addMetadata(sage.media.format.MediaFormat.META_HEIGHT, Integer.toString(exifParser.getImageDimensions()[1]));
        try
        {
          format.addMetadata(sage.media.format.MediaFormat.META_AIRING_TIME, Long.toString(exifParser.getImageDate().getTime()));
        }
        catch (Exception e2){} // this can fail if there's no EXIF info; but there is JPEG info
        if (exifParser.hasJpgThumbnail())
        {
          format.addMetadata(sage.media.format.MediaFormat.META_THUMBNAIL_OFFSET, Long.toString(exifParser.getThumbnailFileOffset()));
          format.addMetadata(sage.media.format.MediaFormat.META_THUMBNAIL_SIZE, Long.toString(exifParser.getThumbnailSize()));
        }
      }
      catch (Exception e)
      {
        // This can happen if there's no metdata in the JPEG file, so don't always log the error
        //if (sage.Sage.DBG) System.out.println("ERROR w/ EXIF parsing: " + e);
        //e.printStackTrace();
      }
    }
    else
    {
      java.util.Map id3Map = ID3Parser.extractID3MetaData(f);
      if (id3Map != null)
      {
        format.addMetadata(id3Map);
        if (id3Map.size() > 0)
          rv = true;
      }
    }
    if (MediaFormat.MPEG2_PS.equals(format.getFormatName()) || MediaFormat.MPEG2_TS.equals(format.getFormatName()))
    {
      java.util.Map mpegMap = MpegMetadata.getFileMetadataAsMap(f, format);
      if (mpegMap != null && mpegMap.size() > 0)
      {
        format.addMetadata(mpegMap);
        rv = true;
      }
    }
    String parsePlugins = sage.Sage.get("mediafile_metadata_parser_plugins", "");
    java.util.StringTokenizer toker = new java.util.StringTokenizer(parsePlugins, ",;");
    while (toker.hasMoreTokens())
    {
      try
      {
        sage.MediaFileMetadataParser parsie = (sage.MediaFileMetadataParser) Class.forName(toker.nextToken(), true, sage.Sage.extClassLoader).newInstance();
        Object parseRes = parsie.extractMetadata(f, "");
        if (parseRes != null && parseRes instanceof java.util.Map)
        {
          format.addMetadata((java.util.Map)parseRes);
          if (((java.util.Map)parseRes).size() > 0)
            rv = true;
        }
      }
      catch (Throwable e1)
      {
        if (sage.Sage.DBG) System.out.println("Error instantiating metadata parser plugin of:" + e1);
      }
    }

    // Check for a simple properties file
    java.io.File propMetaFile = new java.io.File(f.toString() + ".properties");
    if (propMetaFile.isFile())
    {
      java.util.Map metaProps = extractMetadataProperties(f, propMetaFile, false);
      if (metaProps != null)
        format.addMetadata(metaProps);
    }

    return rv;
  }

  private static FormatParserPlugin getFormatParserPluginInstance()
  {
    /*
     * Check to see if an instance has already been created.  If not check to see if one is configured and attempt to create an instance
    */
    if (formatParserPluginInstance == null)
    {
      String parsePlugin = sage.Sage.get("mediafile_mediaformat_parser_plugin", "");

      if (!parsePlugin.isEmpty())
      {
        synchronized (formatParserPluginLock)
        {
          if (formatParserPluginInstance != null)
          {
              return formatParserPluginInstance;
          }
          
          try
          {
            formatParserPluginInstance = (FormatParserPlugin) Class.forName(parsePlugin, true, sage.Sage.extClassLoader).newInstance();
          } 
          catch (Throwable e1)
          {
            if (sage.Sage.DBG)
            {
              System.out.println("Error instantiating metadata parser plugin of: " + e1);
            }
          }
        }
      }
    }
    
    return formatParserPluginInstance;
  }
  
  public static java.util.Map extractMetadataProperties(java.io.File mediaPath, java.io.File propFile, boolean invokePlugins)
  {
    java.util.Map metaProps = null;

    if (invokePlugins)
    {
      String parsePlugins = sage.Sage.get("mediafile_metadata_parser_plugins", "");
      java.util.StringTokenizer toker = new java.util.StringTokenizer(parsePlugins, ",;");
      while (toker.hasMoreTokens())
      {
        try
        {
          sage.MediaFileMetadataParser parsie = (sage.MediaFileMetadataParser) Class.forName(toker.nextToken(), true, sage.Sage.extClassLoader).newInstance();
          Object parseRes = parsie.extractMetadata(mediaPath, "");
          if (parseRes != null && parseRes instanceof java.util.Map)
          {
            if (metaProps == null)
              metaProps = new java.util.HashMap();
            metaProps.putAll((java.util.Map) parseRes);
          }
        }
        catch (Exception e1)
        {
          if (sage.Sage.DBG) System.out.println("Error instantiating metadata parser plugin of:" + e1);
        }
      }
    }

    if (propFile != null && propFile.isFile())
    {
      java.io.InputStream propStream = null;
      try
      {
        propStream = new java.io.FileInputStream(propFile);
        java.util.Properties fileProps = new java.util.Properties();
        fileProps.load(propStream);
        propStream.close();
        propStream = null;
        if (metaProps != null)
        {
          metaProps.putAll(fileProps);
          return metaProps;
        }
        else
          return fileProps;
      }catch (Exception e)
      {
        if (sage.Sage.DBG) System.out.println("ERROR parsing properties metadata of:" + e);
      }
      finally
      {
        if (propStream != null)
        {
          try
          {
            propStream.close();
          }
          catch (Exception e){}
        }
      }
    }
    return metaProps;
  }

  public static long getFileDuration(java.io.File f)
  {
    if (!f.isFile() || f.length() == 0)
      return 0;
    try
    {
      String ffmpegInfo = getFFMPEGFormatInfo(f.toString());
      long rv = extractDurationFromFFMPEGInfo(ffmpegInfo);
      if (rv > 0)
        return rv;
      // Try out detection techniques
      String myFormatData = MPEGParser.getMediaAVInf0(f.toString(),
          Math.min(f.length(), MPEG_PARSER_SEARCH_LENGTH),
          sage.MMC.getInstance().isRecording(f) || sage.FileDownloader.isDownloading(f), -1);
      java.util.regex.Matcher mat = myRetPat.matcher(myFormatData);
      if (mat.find())
      {
        try
        {
          if ( 0 > Integer.parseInt(mat.group(1)))
            return 0;
        }
        catch (NumberFormatException e)
        {
          System.out.println("ERROR parsing Ret value:" + e + " str=" + myFormatData);
          return 0;
        }
      }
      else
        return 0;

      mat = myDurationPat.matcher(myFormatData);
      if (mat.find())
      {
        try
        {
          return Long.parseLong(mat.group(1), 16)/10000; // it's in 10,000,000/th's of a second'
        }
        catch (NumberFormatException e)
        {
          System.out.println("ERROR parsing Duration value:" + e + " str=" + myFormatData);
        }
      }
    }
    catch (Throwable t)
    {
      System.out.println("ERROR getting file duration of:" + t);
      t.printStackTrace();
    }
    return 0;
  }

  public static ContainerFormat getFFMPEGFileFormat(String f)
  {
    try
    {
      ContainerFormat format = new ContainerFormat();
      String ffmpegInfo = getFFMPEGFormatInfo(f);
      String formatName = extractContainerFromFFMPEGInfo(ffmpegInfo);
      if (formatName == null)
      {
        System.out.println("FORMATERROR UNABLE TO PARSE FILE TYPE FOR FILE: "+ f);
        return null;
      }
      else
      {
        format.setFormatName(substituteName(formatName));
        format.setDuration(extractDurationFromFFMPEGInfo(ffmpegInfo));
        format.setBitrate(extractContainerBitrateFromFFMPEGInfo(ffmpegInfo));
        format.setStreamFormats(extractStreamFormatsFromFFMPEGInfo(ffmpegInfo));
        format.setDRMProtection(extractDRMFromFFMPEGInfo(ffmpegInfo));
        java.util.regex.Matcher metaMat = ffmpegMetadataPat.matcher(ffmpegInfo);
        while (metaMat.find())
          format.addMetadata(metaMat.group(1), metaMat.group(2));
      }
      if (sage.Sage.DBG) System.out.println("File Format Parsed3 " + f + "=" + format);
      return format;
    }
    catch (Throwable t)
    {
      if (sage.Sage.DBG) System.out.println("ERROR parsing media file " + f + " of:" + t);
      t.printStackTrace(System.out);
      return null;
    }
  }

  public static ContainerFormat extractMyFormat(java.io.File f)
  {
    try
    {
      //request channel -1, ask getMediaAVInf0 to find first valid stream
      String myFormatData = MPEGParser.getMediaAVInf0(f.toString(),
          Math.min(f.length(), MPEG_PARSER_SEARCH_LENGTH),
          sage.MMC.getInstance().isRecording(f) || sage.FileDownloader.isDownloading(f), -1);
      return extractFormatFromMyString(myFormatData);
    }
    catch (Throwable thr)
    {
      System.out.println("ERROR with file parsing:" + thr);
      return null;
    }
  }

  public static ContainerFormat extractFormatFromMyString(String myFormatData)
  {
    return extractMyFormat(myFormatData, null);
  }
  private static ContainerFormat extractMyFormat(String myFormatData, java.io.File f)
  {
    try
    {
      java.util.regex.Matcher mat = myRetPat.matcher(myFormatData);
      int first_channel = 0;
      //ZQ if Ret>=0 we get get first channel that has format information; otherwise there is no vaild channel.
      if (mat.find())
      {
        try
        {
          first_channel = Integer.parseInt(mat.group(1));
          if ( 0 > first_channel )
          {
            if (sage.Sage.DBG) System.out.println("Not find a valid channel" + myFormatData);
            return null;
          }
        }
        catch (NumberFormatException e)
        {
          System.out.println("ERROR parsing Ret value:" + e + " str=" + myFormatData);
          return null;
        }
      }
      else
        return null;

      int numPrograms = 0;
      mat = myProgramPat.matcher(myFormatData);
      if (mat.find())
      {
        try
        {
          numPrograms = Integer.parseInt(mat.group(1));
        }
        catch (NumberFormatException e)
        {
          System.out.println("ERROR parsing Program value:" + e + " str=" + myFormatData);
          return null;
        }
      }
      long duration = 0;
      mat = myDurationPat.matcher(myFormatData);
      if (mat.find())
      {
        try
        {
          duration = Long.parseLong(mat.group(1), 16)/10000; // it's in 10,000,000/th's of a second'
        }
        catch (NumberFormatException e)
        {
          System.out.println("ERROR parsing Duration value:" + e + " str=" + myFormatData);
          return null;
        }
      }

      mat = myFormatPat.matcher(myFormatData);
      boolean matchedNow = mat.find();
      /* ZQ. we don't need to enume channel any more, as we ask finding first vaild one.
			if (!matchedNow && numPrograms > 1)
			{
                int currProgram = first_channel+1;
				// Try to find another program that has format data and then continue on from there
				for ( ; currProgram < numPrograms; currProgram++)
				{
					myFormatData = MPEGParser.getMediaAVInf0(f.toString(),
						Math.min(f.length(), 30*1024*1024),
						sage.MMC.getInstance().isRecording(f), currProgram);
                    mat = myRetPat.matcher(myFormatData);
             		if (mat.find())
                    {
                        try
                        {
                            first_channel = Integer.parseInt(mat.group(1));
                            if ( 0 > first_channel )
                            {
                                currProgram++;
                                continue;
                            }
                        }
                        catch (NumberFormatException e)
                        {
                            System.out.println("ERROR parsing Ret value:" + e + " str=" + myFormatData);
                            currProgram++;
                            continue;
                        }
                    }
					mat = myDurationPat.matcher(myFormatData);
					if (mat.find())
					{
						try
						{
							duration = Long.parseLong(mat.group(1), 16)/10000; // it's in 10,000,000/th's of a second'
						}
						catch (NumberFormatException e)
						{
							System.out.println("ERROR parsing Duration value:" + e + " str=" + myFormatData);
						}
					}
					mat = myFormatPat.matcher(myFormatData);
					if (mat.find())
					{
						ContainerFormat newFormat = ContainerFormat.buildFormatFromString(mat.group(1));
						if (newFormat != null && newFormat.getNumberOfStreams() > 0)
						{
							matchedNow = true;
							break;
						}
					} else
                        currProgram++;
				}
			}
       */
      if (matchedNow)
      {
        String baseFormat = mat.group(1);
        if (baseFormat == null || baseFormat.trim().length() == 0)
          return null;
        ContainerFormat rv = ContainerFormat.buildFormatFromString(baseFormat);
        if (rv != null)
        {
          rv.setDuration(duration);
          if (numPrograms > 1 && f != null)
          {
            // Add the stream info for all of the programs
            java.util.ArrayList streamFormats = new java.util.ArrayList();
            if (rv.getNumberOfStreams() > 0)
              streamFormats.addAll(java.util.Arrays.asList(rv.getStreamFormats()));
            int currProgram = first_channel+1;
            for ( ; currProgram < numPrograms; currProgram++)
            {
              myFormatData = MPEGParser.getMediaAVInf0(f.toString(),
                  Math.min(f.length(), MPEG_PARSER_SEARCH_LENGTH),
                  sage.MMC.getInstance().isRecording(f) || sage.FileDownloader.isDownloading(f), currProgram);
              mat = myFormatPat.matcher(myFormatData);
              if (mat.find())
              {
                ContainerFormat newFormat = ContainerFormat.buildFormatFromString(mat.group(1));
                if (newFormat != null && newFormat.getNumberOfStreams() > 0)
                {
                  streamFormats.addAll(java.util.Arrays.asList(newFormat.getStreamFormats()));
                }
              }
            }
            rv.setStreamFormats((BitstreamFormat[]) streamFormats.toArray(new BitstreamFormat[0]));
          }
          return rv;
        }
      }
      else if (myFormatData.indexOf("Format:SCRAMBLED") != -1)
      {
        if (sage.Sage.DBG) System.out.println("Scrambled file format detected; tag it as a TS video file with unsupported DRM. format=" + myFormatData);
        ContainerFormat rv = new ContainerFormat();
        rv.setFormatName(MediaFormat.MPEG2_TS);
        rv.setDRMProtection("UNKNOWN-DRM");
        VideoFormat vf = new VideoFormat();
        vf.setFormatName(MediaFormat.MPEG2_VIDEO);
        rv.setStreamFormats(new BitstreamFormat[] { vf });
        return rv;
      }
    }
    catch (Throwable thr)
    {
      System.out.println("ERROR with file parsing:" + thr);
    }
    return null;
  }

  public static String getFFMPEGFormatInfo(String f)
  {
    // Use FFMPEG to try to get the duration of the file
    try
    {
      if (sage.Sage.WINDOWS_OS)
      {
        return sage.IOUtils.exec(new String[] { sage.FFMPEGTranscoder.getTranscoderPath(), "-priority", "idle",
            "-dumpmetadata", "-v", "2", "-i",
            sage.IOUtils.getLibAVFilenameString(f) }, true, true, true);
      }
      else
        return sage.IOUtils.exec(new String[] { "nice", sage.FFMPEGTranscoder.getTranscoderPath(), "-dumpmetadata", "-v", "2", "-i",
            sage.IOUtils.getLibAVFilenameString(f) }, true, true, true);
    }
    catch (Exception e)
    {
      System.out.println("ERROR launching transcoder of:" + e);
      return "";
    }
  }

  private static java.util.regex.Pattern ffmpegDurationPat = java.util.regex.Pattern.compile("Duration\\: ([0-9]*)\\:([0-9]*)\\:([0-9]*)\\.([0-9]*)");
  private static java.util.regex.Pattern ffmpegStartTimePat = java.util.regex.Pattern.compile("start\\: ([0-9\\.]*)");
  private static java.util.regex.Pattern ffmpegContainerPat = java.util.regex.Pattern.compile("Input \\#0\\, (.*), from");
  private static java.util.regex.Pattern ffmpegContainerBitratePat = java.util.regex.Pattern.compile("Duration\\: .* bitrate\\: ([0-9]*) kb\\/s");
  private static java.util.regex.Pattern ffmpegStreamPat = java.util.regex.Pattern.compile("Stream \\#0\\.(\\d*).*");
  private static java.util.regex.Pattern ffmpegFrameRatePat = java.util.regex.Pattern.compile("\\, (\\d*\\.?\\d*) fps");
  private static java.util.regex.Pattern ffmpegFrameRatePat2 = java.util.regex.Pattern.compile("\\, (\\d*\\.?\\d*) tbr");
  private static java.util.regex.Pattern ffmpegDRMPat = java.util.regex.Pattern.compile("META\\:ENCRYPTED\\[(.*?)\\]"); // old style: "META:ENCRYPTED[MS-DRM]"
  private static java.util.regex.Pattern ffmpegNewDRMPat = java.util.regex.Pattern.compile("META\\:ENCRYPTION=(.*)(\\r)*\\n"); // new style: "META:ENCRYPTION=MS-DRM"
  // There's sometimes multiple \r's before the \n
  private static java.util.regex.Pattern ffmpegMetadataPat = java.util.regex.Pattern.compile("META\\:(.*?)\\=(.*)(\\r)*\\n");
  private static java.util.regex.Pattern myRetPat = java.util.regex.Pattern.compile("\\ARet\\:(\\-?\\d+) ");
  private static java.util.regex.Pattern myProgramPat = java.util.regex.Pattern.compile(" Program\\:(\\d+) ");
  private static java.util.regex.Pattern myDurationPat = java.util.regex.Pattern.compile(" Duration\\:([a-fA-F0-9]+) ");
  private static java.util.regex.Pattern myFormatPat = java.util.regex.Pattern.compile(" Format\\:AV\\-INF\\|(.*)");

  public static long extractDurationFromFFMPEGInfo(String info)
  {
    java.util.regex.Matcher mat = ffmpegDurationPat.matcher(info);
    if (mat.find())
    {
      try
      {
        int hours = Integer.parseInt(mat.group(1));
        int mins = Integer.parseInt(mat.group(2));
        int secs = Integer.parseInt(mat.group(3));
        int hundreths = Integer.parseInt(mat.group(4));
        return (((hours*60 + mins)*60 + secs)*1000 + hundreths*10);
      }
      catch (NumberFormatException e)
      {
        System.out.println("ERROR parsing duration string:" + e + " str=" + info);
      }
    }
    return -1;
  }

  public static long extractStartTimeFromFFMPEGInfo(String info)
  {
    java.util.regex.Matcher mat = ffmpegStartTimePat.matcher(info);
    if (mat.find())
    {
      try
      {
        return Math.round(Float.parseFloat(mat.group(1)) * 1000);
      }
      catch (NumberFormatException e)
      {
        System.out.println("ERROR parsing start time string:" + e + " str=" + info);
      }
    }
    return 0;
  }

  public static String extractContainerFromFFMPEGInfo(String info)
  {
    java.util.regex.Matcher mat = ffmpegContainerPat.matcher(info);
    if (mat.find())
    {
      return mat.group(1);
    }
    else
      return null;
  }

  public static String extractDRMFromFFMPEGInfo(String info)
  {
    java.util.regex.Matcher mat = ffmpegDRMPat.matcher(info);
    if (mat.find())
    {
      return mat.group(1);
    }

    mat = ffmpegNewDRMPat.matcher(info);
    if (mat.find())
      return mat.group(1);

    return null;
  }

  // Returns in bits/second
  public static int extractContainerBitrateFromFFMPEGInfo(String info)
  {
    java.util.regex.Matcher mat = ffmpegContainerBitratePat.matcher(info);
    if (mat.find())
    {
      try
      {
        return Integer.parseInt(mat.group(1)) * 1000;
      }
      catch (NumberFormatException e)
      {
        System.out.println("ERROR parsing bitrate string:" + e + " str=" + info);
      }
    }
    return -1;
  }

  public static BitstreamFormat[] extractStreamFormatsFromFFMPEGInfo(String info)
  {
    java.util.regex.Matcher mat = ffmpegStreamPat.matcher(info);
    java.util.ArrayList rv = new java.util.ArrayList();
    while (mat.find())
    {
      String currStreamInfo = mat.group(0);
      int currStreamNum = Integer.parseInt(mat.group(1));
      // See if there's a language on the line
      String language = null;
      int commaIdx = currStreamInfo.indexOf(',');
      if (commaIdx != -1)
      {
        int parenIdx = currStreamInfo.indexOf('(');
        if (parenIdx < commaIdx && parenIdx != -1)
        {
          int paren2Idx = currStreamInfo.indexOf(')', parenIdx);
          if (paren2Idx < commaIdx && paren2Idx != -1)
          {
            language = currStreamInfo.substring(parenIdx + 1, paren2Idx);
            // If there's a language code in brackets, take that instead
            int bidx = language.indexOf('[');
            if (bidx != -1)
            {
              int bidx2 = language.lastIndexOf(']');
              if (bidx2 != -1)
                language = language.substring(bidx + 1, bidx2);
            }
          }
        }
      }
      if (currStreamInfo.indexOf("Video: ") != -1)
      {
        // Video stream info
        VideoFormat newFormat = new VideoFormat();
        newFormat.setOrderIndex(currStreamNum);
        java.util.StringTokenizer toker = new java.util.StringTokenizer(
            currStreamInfo.substring(currStreamInfo.indexOf("Video: ") + "Video: ".length()), ",");
        // First is the codec name
        newFormat.setFormatName(substituteName(toker.nextToken().trim()));
        // Next is the pixel format if it's there
        String nextStr = toker.nextToken();
        int[] myDims = null;
        boolean pastPixFmt = false;
        boolean foundAR = false;
        // fps is last for video on desktop
        // bitrate is last for video on embedded
        while ((nextStr.indexOf(" fps") == -1 && nextStr.indexOf(" tbr") == -1))
        {
          nextStr = nextStr.trim();
          if ("interlaced".equals(nextStr))
          {
            pastPixFmt = true;
            newFormat.setInterlaced(true);
          }
          else if ("progressive".equals(nextStr))
          {
            pastPixFmt = true;
            newFormat.setInterlaced(false);
          }
          else if (nextStr.startsWith("AR:"))
          {
            pastPixFmt = true;
            try
            {
              int arWidth = Integer.parseInt(nextStr.substring(4, nextStr.lastIndexOf(':')));
              int arHeight = Integer.parseInt(nextStr.substring(nextStr.lastIndexOf(':') + 1));
              newFormat.setArDen(arHeight);
              newFormat.setArNum(arWidth);
              newFormat.setAspectRatio(((float)arWidth)/arHeight);
              // AR is more accurate than DAR...although they really should be the same I think
              foundAR = true;
            }
            catch (NumberFormatException e)
            {
              System.out.println("BAD AR Format info:" + e + " str=" + nextStr);
            }
          }
          else if (nextStr.startsWith("PAR")  && !foundAR)
          {
            pastPixFmt = true;
            int darIdx = nextStr.indexOf("DAR");
            if (darIdx != -1)
            {
              String arString = nextStr.substring(darIdx + 3).trim();
              try
              {
                int arWidth = Integer.parseInt(arString.substring(0, arString.lastIndexOf(':')));
                int arHeight = Integer.parseInt(arString.substring(arString.lastIndexOf(':') + 1));
                newFormat.setArDen(arHeight);
                newFormat.setArNum(arWidth);
                newFormat.setAspectRatio(((float)arWidth)/arHeight);
              }
              catch (NumberFormatException e)
              {
                System.out.println("BAD AR Format info:" + e + " str=" + nextStr);
              }
            }
          }
          else if (nextStr.indexOf(" kb/s") != -1)
          {
            try
            {
              int newRate = Integer.parseInt(nextStr.substring(0, nextStr.indexOf(" kb/s"))) * 1000;
              if (newRate != 104857000) // This is FFFF for MPEG2 video which means it can't find it correctly
                newFormat.setBitrate(newRate);
            }
            catch (NumberFormatException e)
            {
              System.out.println("Error parsing bitrate of:" + e + " str=" + nextStr);
            }
          }
          else if ((myDims = parseSeparatedInts(nextStr, 'x')) != null)
          {
            pastPixFmt = true;
            newFormat.setWidth(myDims[0]);
            newFormat.setHeight(myDims[1]);
          }
          else if ((myDims = parseSeparatedInts(nextStr, '/')) != null)
          {
            pastPixFmt = true;
            // It's frame time in the output so flip it
            // Make sure it's sane!
            if (((float)myDims[1]) / myDims[0] < 200)
            {
              newFormat.setFpsNum(myDims[1]);
              newFormat.setFpsDen(myDims[0]);
              if (myDims[0] != 0)
                newFormat.setFps(((float) myDims[1]) / myDims[0]);
            }
          }
          else if (!pastPixFmt)
          {
            newFormat.setColorspace(nextStr);
          }
          if (toker.hasMoreTokens())
            nextStr = toker.nextToken().trim();
          else
            break;
        }

        // We want the 'tbr' for MPEG2Video and the 'fps' for all other content (based on testing)
        float betterFps = extractFpsFromFFMPEGStreamInfo(currStreamInfo, MediaFormat.MPEG2_VIDEO.equals(newFormat.getFormatName()) ? 2 : 1);
        if ((betterFps == 0 || betterFps >= 100) && !MediaFormat.MPEG2_VIDEO.equals(newFormat.getFormatName()))
          betterFps = extractFpsFromFFMPEGStreamInfo(currStreamInfo, 2); // if fps isn't there or is bad, then get tbr
        if (betterFps != 0)
        {
          // The 'fps' framerate is really what we want unless it's not specified, then we use the frame time information instead
          newFormat.setFps(betterFps);
          newFormat.setFpsDen(0);
          newFormat.setFpsNum(0);
        }

        String streamID = extractStreamPESIDFromFFMPEGStreamInfo(currStreamInfo);
        if (streamID != null)
          newFormat.setId(streamID);

        rv.add(newFormat);
      }
      else if (currStreamInfo.indexOf("Audio: ") != -1)
      {
        // Audio stream info
        AudioFormat newFormat = new AudioFormat();
        newFormat.setOrderIndex(currStreamNum);
        java.util.StringTokenizer toker = new java.util.StringTokenizer(
            currStreamInfo.substring(currStreamInfo.indexOf("Audio: ") + "Audio: ".length()), ",");
        // First is the codec name
        newFormat.setFormatName(substituteName(toker.nextToken().trim()));

        if ("aac_latm".equalsIgnoreCase(newFormat.getFormatName()))
        {
          newFormat.setFormatName(MediaFormat.AAC);
          newFormat.setAudioTransport("LATM");
        }

        if (toker.hasMoreTokens())
        {
          // Check for sampling rate & number of channels
          String str = toker.nextToken().trim();
          if (str.endsWith("Hz"))
          {
            try
            {
              newFormat.setSamplingRate(Integer.parseInt(str.substring(0, str.indexOf(" Hz"))));
            }
            catch (NumberFormatException e)
            {
              System.out.println("Error parsing sampling rate:" + e + " str=" + str);
            }
            str = toker.nextToken().trim();

            // Now is the channel information
            if ("mono".equals(str))
              newFormat.setChannels(1);
            else if ("stereo".equals(str))
              newFormat.setChannels(2);
            else if ("5:1".equals(str) || "5.1".equals(str))
              newFormat.setChannels(6);
            else if ("7:1".equals(str) || "7.1".equals(str))
              newFormat.setChannels(8);
            else if (str.endsWith(" channels"))
            {
              try
              {
                newFormat.setChannels(Integer.parseInt(str.substring(0, str.indexOf(' '))));
              }
              catch (NumberFormatException e)
              {
                System.out.println("Error parsing number of channels:" + e + " str=" + str);
              }
            }

            if (toker.hasMoreTokens())
              str = toker.nextToken().trim();
          }

          // deal with sample formats in newer builds of ffmpeg
          // look for any of: s8, u8, s16, u16, s24, u24, s32, u32, s64, u64, flt, dbl
          // this should hopefully cover any changes in future versions of ffmpeg...
          if("s8".equals(str) || "u8".equals(str) ||
              "s16".equals(str) || "u16".equals(str) ||
              "s24".equals(str) || "u24".equals(str) ||
              "s32".equals(str) || "u32".equals(str) ||
              "s64".equals(str) || "u64".equals(str) ||
              "flt".equals(str) || "dbl".equals(str)
              )
          {
            if(toker.hasMoreTokens())
              str = toker.nextToken().trim(); // skip it
          }

          if (str.endsWith(" kb/s"))
          {
            try
            {
              newFormat.setBitrate(Integer.parseInt(str.substring(0, str.indexOf(" kb/s"))) * 1000);
            }
            catch (NumberFormatException e)
            {
              System.out.println("Error parsing bitrate of:" + e + " str=" + str);
            }
          }
        }
        newFormat.setLanguage(language);
        String streamID = extractStreamPESIDFromFFMPEGStreamInfo(currStreamInfo);
        if (streamID != null)
          newFormat.setId(streamID);
        rv.add(newFormat);
      }
      else if (currStreamInfo.indexOf("Data: ") != -1)
      {
        // Data stream info
      }
      else if (currStreamInfo.indexOf("Subtitle: ") != -1)
      {
        // Subtitle stream info
        SubpictureFormat newFormat = new SubpictureFormat();
        newFormat.setOrderIndex(currStreamNum);
        String subForm = currStreamInfo.substring(currStreamInfo.indexOf("Subtitle: ") +
            "Subtitle: ".length());
        // Strip off any extra data (like kb/s) after the sub name
        int commaIdx2 = subForm.indexOf(',');
        if (commaIdx2 != -1)
          subForm = subForm.substring(0, commaIdx2).trim();
        if (subForm.startsWith("["))
        {
          // Cleanup the [x][x][x][x] / 0x#### stuff
          int slashIdx = subForm.indexOf('/');
          if (slashIdx != -1)
            subForm = subForm.substring(slashIdx + 1).trim();
        }
        newFormat.setFormatName(substituteName(subForm));
        newFormat.setLanguage(language);
        String streamID = extractStreamPESIDFromFFMPEGStreamInfo(currStreamInfo);
        if (streamID != null)
          newFormat.setId(streamID);
        rv.add(newFormat);
      }
      else
      {
        // Unknown type of stream
      }
    }
    return (BitstreamFormat[]) rv.toArray(new BitstreamFormat[0]);
  }

  public static float extractFpsFromFFMPEGStreamInfo(String streamInfo, int mode)
  {
    java.util.regex.Matcher mat = (mode == 1 ? ffmpegFrameRatePat : ffmpegFrameRatePat2).matcher(streamInfo);
    if (mat.find())
    {
      try
      {
        return Float.parseFloat(mat.group(1));
      }
      catch (NumberFormatException e)
      {
        System.out.println("Error parsing frame rate of:" + e + " str=" + streamInfo);
      }
    }
    return 0;
  }

  public static int[] parseSeparatedInts(String s, char separator)
  {
    try
    {
      s = s.trim();
      int x = s.indexOf(separator);
      return new int[] { Integer.parseInt(s.substring(0, x)), Integer.parseInt(s.substring(x + 1)) };
    }
    catch (Exception e)
    {
      return null;
    }
  }

  private static boolean setContainerTypeEasy(java.io.File f, ContainerFormat cf)
  {
    java.io.InputStream inStream = null;
    String fstr = f.toString();
    try
    {
      inStream = new java.io.FileInputStream(fstr);
      byte[] readBuf = new byte[189];
      inStream.read(readBuf);
      // Check for MPEG2 PS or interleaved PES first
      if ((readBuf[0] == 0 && readBuf[1] == 0 && readBuf[2] == 1 && ((byte)(readBuf[3] & 0xFF)) == ((byte)0xBA) &&
          ((readBuf[4] & 0xC0) == 0x40)) /* PS */)
      {
        cf.setFormatName(MediaFormat.MPEG2_PS);
        return true;
      }
      else if ((readBuf[0] == 0 && readBuf[1] == 0 && readBuf[2] == 1 && (((byte)(readBuf[3] & 0xF0)) == ((byte)0xE0))) /* Video PES */)
      {
        cf.setFormatName(MediaFormat.MPEG2_PES_VIDEO);
        return true;
      }
      else if ((readBuf[0] == 0 && readBuf[1] == 0 && readBuf[2] == 1 && (((byte)(readBuf[3] & 0xE0)) == ((byte)0xC0))) /* Audio PES */)
      {
        cf.setFormatName(MediaFormat.MPEG2_PES_AUDIO);
        return true;
      }
      else if ((readBuf[0] == 0 && readBuf[1] == 0 && readBuf[2] == 1 && ((byte)(readBuf[3] & 0xFF)) == ((byte)0xBA) &&
          ((readBuf[4] & 0xF0) == 0x20))) /* MPEG-1 */
      {
        cf.setFormatName(MediaFormat.MPEG1);
        return true;
      }
      else if (readBuf[0] == 0x47 && readBuf[188] == 0x47)
      {
        // Check for MPEG2 TS
        cf.setFormatName(MediaFormat.MPEG2_TS);
        return true;
      }
      else if (((readBuf[0] & 0xFF) == 0xFF) && ((readBuf[1] & 0xFF) == 0xD8) && ((readBuf[2] & 0xFF) == 0xFF))
      {
        // JPEG file marker (JFIF would check another byte, which should be E0, but that's too restrictive)
        cf.setFormatName(MediaFormat.JPEG);
        return true;
      }
      else if ((readBuf[0]  == 'G') && (readBuf[1] == 'I') && (readBuf[2] == 'F') && (readBuf[3] == '8'))
      {
        // GIF87a or GIF89a
        cf.setFormatName(MediaFormat.GIF);
        return true;
      }
      else if (((readBuf[0] & 0xFF) == 0x89) && ((readBuf[1] & 0xFF) == 0x50) && ((readBuf[2] & 0xFF) == 0x4E) &&
          ((readBuf[3] & 0xFF) == 0x47) && ((readBuf[4] & 0xFF) == 0x0D) && ((readBuf[5] & 0xFF) == 0x0A) &&
          ((readBuf[6] & 0xFF) == 0x1A) && ((readBuf[7] & 0xFF) == 0x0A))
      {
        cf.setFormatName(MediaFormat.PNG);
        return true;
      }
      else if ((readBuf[0]  == 'B') && (readBuf[1] == 'M'))
      {
        cf.setFormatName(MediaFormat.BMP);
        cf.addMetadata(MediaFormat.META_WIDTH, Integer.toString(((readBuf[21] & 0xFF) << 24) | ((readBuf[20] & 0xFF) << 16) |
            ((readBuf[19] & 0xFF) << 8) | (readBuf[18] & 0xFF)));
        cf.addMetadata(MediaFormat.META_HEIGHT, Integer.toString(((readBuf[25] & 0xFF) << 24) | ((readBuf[24] & 0xFF) << 16) |
            ((readBuf[23] & 0xFF) << 8) | (readBuf[22] & 0xFF)));
        return true;
      }
      else if ((readBuf[0] == 'I' && readBuf[1] == 'I' && readBuf[2] == 42 && readBuf[3] == 0) ||
          (readBuf[0] == 'M' && readBuf[1] == 'M' && readBuf[2] == 0 && readBuf[3] == 42))
      {
        cf.setFormatName(MediaFormat.TIFF);
        return true;
      }
      else if (readBuf[0] == 'R' && readBuf[1] == 'I' && readBuf[2] == 'F' && readBuf[3] == 'F' &&
          readBuf[8] == 'A' && readBuf[9] == 'V' && readBuf[10] == 'I' && (readBuf[11] == ' ' || readBuf[11] == 0x19))
      {
        cf.setFormatName(MediaFormat.AVI);
        return true;
      }
      else if (readBuf[0] == 'R' && readBuf[1] == 'I' && readBuf[2] == 'F' && readBuf[3] == 'F' &&
          readBuf[8] == 'W' && readBuf[9] == 'A' && readBuf[10] == 'V' && readBuf[11] == 'E')
      {
        cf.setFormatName(MediaFormat.WAV);
        return true;
      }
      else if ((readBuf[0] == 'f' && readBuf[1] == 'L' && readBuf[2] == 'a' && readBuf[3] == 'C') || fstr.toLowerCase().endsWith(".flac"))
      {
        cf.setFormatName(MediaFormat.FLAC);
        return true;
      }
      else if (readBuf[0] == 0x30 && readBuf[1] == 0x26 && (readBuf[2]&0xFF) == 0xB2 && readBuf[3] == 0x75 && (readBuf[4]&0xFF) == 0x8E &&
          readBuf[5] == 0x66 && (readBuf[6]&0xFF) == 0xCF && readBuf[7] == 0x11 && (readBuf[8]&0xFF) == 0xA6 && (readBuf[9]&0xFF) == 0xD9 &&
          readBuf[10] == 0 && (readBuf[11]&0xFF) == 0xAA && readBuf[12] == 0 && readBuf[13] == 0x62 && (readBuf[14]&0xFF) == 0xCE &&
          readBuf[15] == 0x6C)
      {
        cf.setFormatName(MediaFormat.ASF);
        return true;
      }
      else if (readBuf[0] == 'O' && readBuf[1] == 'g' && readBuf[2] == 'g' && readBuf[3] == 'S' && readBuf[4] == 0 &&
          readBuf[5] <= 0x7)
      {
        cf.setFormatName(MediaFormat.OGG);
        return true;
      }
      else if (readBuf[0] == 'I' && readBuf[1] == 'D' && readBuf[2] == '3')
      {
        // It's some kind of audio file most likely; just set it to "Audio" so we'll skip our own MPEG detector and use FFMPEG instead
        cf.setFormatName("Audio");
        return true;
      }
      else if ((readBuf[4] == 'f' && readBuf[5] == 't' && readBuf[6] == 'y' && readBuf[7] == 'p') ||
          (readBuf[4] == 'm' && readBuf[5] == 'o' && readBuf[6] == 'o' && readBuf[7] == 'v') ||
          (readBuf[4] == 'm' && readBuf[5] == 'd' && readBuf[6] == 'a' && readBuf[7] == 't') ||
          (readBuf[4] == 'f' && readBuf[5] == 'r' && readBuf[6] == 'e' && readBuf[7] == 'e') ||
          (readBuf[4] == 's' && readBuf[5] == 'k' && readBuf[6] == 'i' && readBuf[7] == 'p') ||
          (readBuf[4] == 'w' && readBuf[5] == 'i' && readBuf[6] == 'd' && readBuf[7] == 'e') ||
          (readBuf[4] == 'p' && readBuf[5] == 'n' && readBuf[6] == 'o' && readBuf[7] == 't'))
      {
        cf.setFormatName(MediaFormat.QUICKTIME);
        return true;
      }
      else if ((readBuf[0] & 0xFF) == 0x1A && (readBuf[1] & 0xFF) == 0x45 && (readBuf[2] & 0xFF) == 0xDF && (readBuf[3] & 0xFF) == 0xA3 &&
          ((readBuf[8] == 'm' && readBuf[9] == 'a' && readBuf[10] == 't' && readBuf[11] == 'r' && readBuf[12] == 'o' && readBuf[13] == 's' &&
          readBuf[14] == 'k' && readBuf[15] == 'a') ||
          (readBuf[24] == 'm' && readBuf[25] == 'a' && readBuf[26] == 't' && readBuf[27] == 'r' && readBuf[28] == 'o' && readBuf[29] == 's' &&
          readBuf[30] == 'k' && readBuf[31] == 'a')))
      {
        cf.setFormatName(MediaFormat.MATROSKA);
        return true;
      }
      else if ((readBuf[0] == 'C' || readBuf[0] == 'F') && readBuf[1] == 'W' && readBuf[2] == 'S')
      {
        cf.setFormatName(MediaFormat.SWF);
        return true;
      }
      else if (readBuf[0] == 'F' && readBuf[1] == 'L' && readBuf[2] == 'V')
      {
        cf.setFormatName(MediaFormat.FLASH_VIDEO);
        return true;
      }
      else if (((readBuf[0] & 0xFF) == 0x7F && (readBuf[1] & 0xFF) == 0xFE && (readBuf[2] & 0xFF) == 0x80 && (readBuf[3] & 0xFF) == 0x01) ||
          ((readBuf[0] & 0xFF) == 0xFE && (readBuf[1] & 0xFF) == 0x7F && (readBuf[2] & 0xFF) == 0x01 && (readBuf[3] & 0xFF) == 0x80) ||
          ((readBuf[0] & 0xFF) == 0x1F && (readBuf[1] & 0xFF) == 0xFF && (readBuf[2] & 0xFF) == 0xE8 && (readBuf[3] & 0xFF) == 0x00) ||
          ((readBuf[0] & 0xFF) == 0xFF && (readBuf[1] & 0xFF) == 0x1F && (readBuf[2] & 0xFF) == 0x00 && (readBuf[3] & 0xFF) == 0xE8))
      {
        cf.setFormatName(MediaFormat.DTS);
        return true;
      }
      else if (readBuf[0] == '<' && readBuf[1] == 's' && readBuf[2] == 'm' && readBuf[3] == 'i' && readBuf[4] == 'l')
      {
        cf.setFormatName(MediaFormat.SMIL);
        java.io.BufferedReader buffRead = new java.io.BufferedReader(new java.io.InputStreamReader(inStream));
        String line = buffRead.readLine();
        while (line != null)
        {
          int durIdx = line.indexOf("dur=\"");
          if (durIdx != -1)
          {
            int durIdx2 = line.indexOf("ms", durIdx);
            if (durIdx2 != -1)
            {
              try
              {
                long duration = Long.parseLong(line.substring(durIdx + 5, durIdx2));
                cf.setDuration(duration);
                break;
              }
              catch (NumberFormatException nfe)
              {
                if (sage.Sage.DBG) System.out.println("Error parsing SMIL duration of:" + nfe);
              }
            }
          }
          line = buffRead.readLine();
        }
        return true;
      }
      if ((readBuf[0] & 0xFF) == 0x1A && (readBuf[1] & 0xFF) == 0x45 && (readBuf[2] & 0xFF) == 0xDF && (readBuf[3] & 0xFF) == 0xA3)
      {
        // EBML file, check if it's Matroska
        for (int i = 4; i < readBuf.length - 10; i++)
        {
          if ((readBuf[i] & 0xFF) == 0x42 && (readBuf[i + 1] & 0xFF) == 0x82) {
            // Found the doctype tag, skip over the length field which can be up to
            // 8 bytes long but will be followed by 'matroska'
            for (int j = i + 2; j < i + 10 && j < readBuf.length - 7; j++) {
              if (readBuf[j] == 'm' && readBuf[j + 1] == 'a' && readBuf[j + 2] == 't' && readBuf[j + 3] == 'r' &&
                  readBuf[j + 4] == 'o' && readBuf[j + 5] == 's' && readBuf[j + 6] == 'k' && readBuf[j + 7] == 'a')
              {
                cf.setFormatName(MediaFormat.MATROSKA);
                return true;
              }
            }
          }
        }
      }
    }
    catch (java.io.IOException e)
    {}
    finally
    {
      if (inStream != null)
      {
        try{inStream.close(); }catch(Exception exc){}
      }
    }

    // Check for filename association matching
    String lcname = fstr.toLowerCase();
    if (lcname.endsWith(".gif"))
    {
      cf.setFormatName(MediaFormat.GIF);
      return true;
    }
    else if (lcname.endsWith(".bmp"))
    {
      cf.setFormatName(MediaFormat.BMP);
      return true;
    }
    else if (lcname.endsWith(".jpg") || lcname.endsWith(".jpeg"))
    {
      cf.setFormatName(MediaFormat.JPEG);
      return true;
    }
    else if (lcname.endsWith(".png"))
    {
      cf.setFormatName(MediaFormat.PNG);
      return true;
    }
    return false;
  }

  private static String extractStreamPESIDFromFFMPEGStreamInfo(String currStreamInfo)
  {
    // It's between the brackets from the first comma
    int commaIdx = currStreamInfo.indexOf(',');
    if (commaIdx == -1)
      return null;
    int brack1 = currStreamInfo.indexOf('[');
    if (brack1 > commaIdx || brack1 == -1)
      return null;
    int brack2 = currStreamInfo.indexOf(']');
    if (brack2 > commaIdx || brack2 == -1 || brack2 < brack1)
      return null;
    if (brack2 - brack1 == 5)
      return currStreamInfo.substring(brack2 - 2, brack2);
    if (brack2 - brack1 == 6)
      return currStreamInfo.substring(brack2 - 3, brack2);
    if (brack2 - brack1 == 7)
      return currStreamInfo.substring(brack2 - 4, brack2);
    return null;
  }

  public static ContainerFormat parseBluRayFormat(java.io.File bdmvDir)
  {
    // For parsing BluRay files we only care about the playlist entries currently.
    // We'll look through them all and find the one playlist which is the longest; and that'll be the main
    // movie playlist. We could later fake multiple titles if we find more than one playlist of a decent length to allow
    // accessing additional content on the disc; this will probably be needed for multi-show discs like for TV series.
    // We'll also be able to extract the complete format information from the playlist entry as well.
    // I could also be wrong about the format information; we may very well need to parse the clipinfo files to get that data;
    // the playlist dumpers look like that's what they're doing
    try
    {
      sage.media.bluray.BluRayParser bdp = new sage.media.bluray.BluRayParser(bdmvDir);
      bdp.fullyAnalyze();
      if (sage.Sage.DBG && sage.Sage.getBoolean("dump_bluray_info", false))
        bdp.dumpInfo();
      return bdp.getFileFormat();
    }
    catch (Exception e)
    {
      System.out.println("ERROR parsing BluRay structure of:" + e);
      e.printStackTrace();
    }
    return null;
  }

  // This searches for external subtitles files and adds them as streams to the ContainerFormat
  // Returns true if changes were made to the format
  public static boolean updateExternalSubs(java.io.File theFile, ContainerFormat cf)
  {
    boolean rv = false;
    // Only video can have subtitles
    if (cf == null || cf.getNumVideoStreams() == 0 || theFile == null)
      return rv;
    rv |= cf.validateExternalSubtitles();
    // Get our base file path for checking for other subtitle files
    String fileprefix = theFile.getAbsolutePath();
    int idx = fileprefix.lastIndexOf('.');
    if (idx != -1)
      fileprefix = fileprefix.substring(0, idx + 1);

    // First check for a single SRT file in parallel
    java.io.File testFile = new java.io.File(fileprefix + "srt");
    if (testFile.isFile())
    {
      if (cf.formatHasSubtitlePath(testFile.toString()))
        return rv;
      SubpictureFormat subpic = new SubpictureFormat();
      subpic.setFormatName(MediaFormat.SRT);
      subpic.setPath(testFile.toString());
      cf.addStream(subpic);
      return true;
    }
    // Next check for a single IDX/SUB file in parallel
    testFile = new java.io.File(fileprefix + "idx");
    if (testFile.isFile() && new java.io.File(fileprefix + "sub").isFile())
    {
      if (cf.formatHasSubtitlePath(testFile.toString()))
        return rv;
      boolean invalidFile = false;
      // Now we have to open this file and parse it to see what languages are inside
      java.io.BufferedReader buffRead = null;
      int defaultLang = -1;
      try
      {
        buffRead = sage.IOUtils.openReaderDetectCharset(testFile, sage.Sage.BYTE_CHARSET);
        String line = buffRead.readLine();
        if (line != null && line.indexOf("VobSub index file") == -1)
          invalidFile = true;
        while (!invalidFile && line != null)
        {
          if (line.startsWith("langidx:"))
            defaultLang = Integer.parseInt(line.substring(8).trim());
          else if (line.startsWith("id:"))
          {
            idx = line.indexOf(',');
            if (idx != -1)
            {
              String currLang = line.substring(3, idx).trim();
              SubpictureFormat subpic = new SubpictureFormat();
              subpic.setFormatName(MediaFormat.VOBSUB);
              subpic.setLanguage(currLang);
              subpic.setPath(testFile.toString());
              cf.addStream(subpic);
              idx = line.indexOf("index:");
              if (idx != -1)
              {
                try
                {
                  int currIndex = Integer.parseInt(line.substring(idx + 6).trim());
                  subpic.setOrderIndex(currIndex);
                  if (currIndex == defaultLang)
                    subpic.setPrimary(true);
                }
                catch (NumberFormatException fe)
                {
                  System.out.println("ERROR parsing subtitle index in " + testFile + " of " + fe);
                }
              }
            }
          }
          line = buffRead.readLine();
        }
      }
      catch (Exception nfe)
      {
        System.out.println("ERROR parsing IDX subtitle file: " + testFile + " of :" + nfe);
      }
      finally
      {
        if (buffRead != null)
        {
          try
          {
            buffRead.close();
          }
          catch (Exception e){}
        }
      }
      if (!invalidFile)
        return true;
    }
    // Next check for a single SUB file in parallel
    testFile = new java.io.File(fileprefix + "sub");
    if (testFile.isFile())
    {
      if (cf.formatHasSubtitlePath(testFile.toString()))
        return rv;
      SubpictureFormat subpic = new SubpictureFormat();
      subpic.setFormatName(MediaFormat.SUB);
      subpic.setPath(testFile.toString());
      cf.addStream(subpic);
      return true;
    }
    // Next check for a single SSA/ASS file in parallel
    testFile = new java.io.File(fileprefix + "ssa");
    if (testFile.isFile() || (testFile = new java.io.File(fileprefix + "ass")).isFile())
    {
      if (cf.formatHasSubtitlePath(testFile.toString()))
        return rv;
      SubpictureFormat subpic = new SubpictureFormat();
      subpic.setFormatName(MediaFormat.SSA);
      subpic.setPath(testFile.toString());
      cf.addStream(subpic);
      return true;
    }
    // Next check for a single SAMI file in parallel
    testFile = new java.io.File(fileprefix + "smi");
    if (testFile.isFile() || (testFile = new java.io.File(fileprefix + "sami")).isFile())
    {
      if (cf.formatHasSubtitlePath(testFile.toString()))
        return rv;
      // Now we have to open this file and parse it to see what languages are inside
      // We do this by finding the string inside the <style></style> block and then breaking
      // that up into name/attribute sections. The names that start with a period are the language class
      // names; and then their attributes tell us what the actual language is.
      java.io.BufferedReader buffRead = null;
      StringBuffer styleText = new StringBuffer();
      try
      {
        buffRead = sage.IOUtils.openReaderDetectCharset(testFile, sage.Sage.BYTE_CHARSET);
        String line = buffRead.readLine();
        boolean inStyle = false;
        while (line != null)
        {
          if (!inStyle)
          {
            idx = line.toLowerCase().indexOf("<style ");
            if (idx != -1)
            {
              inStyle = true;
              styleText.append(line.substring(idx));
              styleText.append('\n');
            }
          }
          else if (inStyle)
          {
            idx = line.toLowerCase().indexOf("</style>");
            if (idx != -1)
            {
              styleText.append(line.substring(0, idx));
              break;
            }
            styleText.append(line);
            styleText.append('\n');
          }
          line = buffRead.readLine();
        }
      }
      catch (Exception nfe)
      {
        System.out.println("ERROR parsing SAMI subtitle file: " + testFile + " of :" + nfe);
      }
      finally
      {
        if (buffRead != null)
        {
          try
          {
            buffRead.close();
          }
          catch (Exception e){}
        }
      }
      String style = styleText.toString();
      if (style.length() > 0)
      {
        String[] langs = sage.media.sub.SAMISubtitleHandler.extractLanguagesFromStyleSection(style);
        for (int m = 0; m < langs.length; m++)
        {
          SubpictureFormat subpic = new SubpictureFormat();
          subpic.setFormatName(MediaFormat.SAMI);
          subpic.setLanguage(langs[m]);
          subpic.setPath(testFile.toString());
          cf.addStream(subpic);
        }
        return langs.length > 0 || rv;
      }
      return rv;
    }

    // Now we do the directory listing to check for multiple subtitle files
    java.io.File parent = theFile.getParentFile();
    if (parent != null && parent.isDirectory())
    {
      String name = theFile.getName();
      int dotIdx = name.lastIndexOf('.');
      name = name.substring(0, dotIdx + 1);
      String[] fileList = parent.list();
      for (int i = 0; fileList != null && i < fileList.length; i++)
      {
        if (fileList[i].startsWith(name))
        {
          dotIdx = fileList[i].lastIndexOf('.');
          String ext = fileList[i].substring(dotIdx + 1);
          String subFormat = null;
          if ("srt".equalsIgnoreCase(ext))
            subFormat = sage.media.format.MediaFormat.SRT;
          else if ("sub".equalsIgnoreCase(ext))
            subFormat = sage.media.format.MediaFormat.SUB;
          else if ("ssa".equalsIgnoreCase(ext) || "ass".equalsIgnoreCase(ext))
            subFormat = sage.media.format.MediaFormat.SSA;
          if (subFormat != null && !cf.formatHasSubtitlePath((testFile = new java.io.File(parent, fileList[i])).toString()))
          {
            // Now extract the language data
            String currLang = fileList[i].substring(name.length(), fileList[i].length() - 4);
            SubpictureFormat subpic = new SubpictureFormat();
            subpic.setFormatName(subFormat);
            subpic.setLanguage(currLang);
            subpic.setPath(testFile.toString());
            cf.addStream(subpic);
            rv = true;
          }
        }
      }
    }
    return rv;
  }
}
