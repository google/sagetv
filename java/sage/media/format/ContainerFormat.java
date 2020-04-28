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

import sage.Sage;

/**
 *
 * @author Narflex
 */
public class ContainerFormat extends MediaFormat
{
  /*
   * The other special ones we need to deal with are:
   * "WM/Picture" or "Picture" - comma separate list of 2 numbers, first is offset second is size
   * "GenreIDBase1" - need to convert this to a zero based genreid
   * "Track" - need to check this for #/# and convert to track num & total tracks
   */
  private static final String[][] META_SUBSTITUTIONS = {
    { "WM/TrackNumber", MediaFormat.META_TRACK },
    { "WM/Track", MediaFormat.META_TRACK },
    { "WM/Year", MediaFormat.META_YEAR },
    { "WM/OriginalReleaseYear", MediaFormat.META_YEAR },
    { "OriginalReleaseYear", MediaFormat.META_YEAR },
    { "WM/AlbumTitle", MediaFormat.META_ALBUM },
    { "AlbumTitle", MediaFormat.META_ALBUM },
    { "Author", MediaFormat.META_ARTIST },
    { "WM/Genre", MediaFormat.META_GENRE },
    { "WM/GenreID", MediaFormat.META_GENRE_ID },
    { "WM/Language", MediaFormat.META_LANGUAGE },
    { "WM/AlbumArtist", MediaFormat.META_ALBUM_ARTIST },
    { "WM/Composer", MediaFormat.META_COMPOSER },
    { "date", MediaFormat.META_YEAR },
    { "tracknumber", MediaFormat.META_TRACK },
    { "Album Artist", MediaFormat.META_ALBUM_ARTIST },
    { "INAM", MediaFormat.META_TITLE },
    { "WM/SubTitle", MediaFormat.META_EPISODE_NAME },
    { "WM/SubTitleDescription", MediaFormat.META_DESCRIPTION },
    { "WM/ParentalRating", MediaFormat.META_PARENTAL_RATING },
  };

  public String toString()
  {
    StringBuffer sb = new StringBuffer(getFormatName() + " " + sage.Sage.durFormat(duration) + " " + bitrate/1000 + " kbps [");
    for (int i = 0; i < numStreams; i++)
      sb.append("#" + i + " " + streamFormats[i]);
    if (isDRMProtected())
      sb.append("DRM:" + drmProtection);
    if (hasMetadata())
      sb.append(metadata.toString());
    sb.append(']');
    return sb.toString();
  }
  public int getNumberOfStreams()
  {
    return numStreams;
  }

  public void setStreamFormats(BitstreamFormat[] theFormats)
  {
    streamFormats = theFormats;
    numStreams = theFormats.length;
  }

  public long getDuration()
  {
    return duration;
  }

  public void setDuration(long x)
  {
    duration = x;
  }

  public int getPacketSize()
  {
    return packetSize;
  }

  public void setPacketSize(int x)
  {
    packetSize = x;
  }

  public BitstreamFormat getStreamFormat(int streamIdx)
  {
    if (streamFormats != null && streamFormats.length > streamIdx)
      return streamFormats[streamIdx];
    else
      return null;
  }

  public int getBitrate()
  {
    return bitrate;
  }

  public void setBitrate(int x)
  {
    bitrate = x;
  }

  public void setDRMProtection(String s)
  {
    drmProtection = s != null ? s.intern() : s;
  }

  public String getDRMProtection()
  {
    return drmProtection;
  }

  public boolean isDRMProtected()
  {
    return drmProtection != null && drmProtection.length() > 0;
  }

  public boolean hasMetadata()
  {
    return metadata != null && metadata.size() > 0;
  }

  public void addMetadata(String name, String value)
  {
    addMetadata(name, value, true);
  }
  public void addMetadata(String name, String value, boolean replaceIfThere)
  {
    if (name == null || value == null) return;
    if (metadata == null)
      metadata = new java.util.Properties();
    for (int i = 0; i < META_SUBSTITUTIONS.length; i++)
      if (META_SUBSTITUTIONS[i][0].equalsIgnoreCase(name))
      {
        addMetadata(META_SUBSTITUTIONS[i][1], value, replaceIfThere);
        return;
      }
    // Fix any case sensitivity issues
    for (int i = 0; i < ALL_META_PROPS.length; i++)
      if (ALL_META_PROPS[i].equalsIgnoreCase(name) && !ALL_META_PROPS[i].equals(name))
      {
        addMetadata(ALL_META_PROPS[i], value, replaceIfThere);
        return;
      }
    if (!replaceIfThere && hasMetadata(name))
      return;
    if ("WM/Picture".equals(name) || "Picture".equals(name))
    {
      int commaIdx = value.indexOf(',');
      if (commaIdx != -1)
      {
        try
        {
          int offset = Integer.parseInt(value.substring(0, commaIdx));
          int size = Integer.parseInt(value.substring(commaIdx + 1));
          metadata.setProperty(META_THUMBNAIL_OFFSET, Integer.toString(offset));
          metadata.setProperty(META_THUMBNAIL_SIZE, Integer.toString(size));
          return;
        }
        catch (NumberFormatException e)
        {
          System.out.println("ERROR parsing Picture metadata " + name + "=" + value + " of:" + e);
        }
      }
    }
    else if ("Track".equals(name))
    {
      int slashIdx = value.indexOf('/');
      if (slashIdx != -1)
      {
        try
        {
          int curr = Integer.parseInt(value.substring(0, slashIdx));
          int total = Integer.parseInt(value.substring(slashIdx + 1));
          metadata.setProperty(META_TRACK, Integer.toString(curr).intern());
          metadata.setProperty(META_TOTAL_TRACKS, Integer.toString(total).intern());
          return;
        }
        catch (NumberFormatException e)
        {
          System.out.println("ERROR parsing Picture metadata " + name + "=" + value + " of:" + e);
        }
      }
    }
    else if ("GenreIDBase1".equals(name))
    {
      try
      {
        metadata.setProperty(MediaFormat.META_GENRE, ID3Parser.ID3V1_GENRES[Integer.parseInt(value) - 1]);
        return;
      }
      catch (Exception e)
      {
        System.out.println("ERROR parsing Picture metadata " + name + "=" + value + " of:" + e);
      }
    }
    else if ("chapterpoints".equalsIgnoreCase(name))
    {
      // This is a special one that's not really metadata, but format information
      java.util.StringTokenizer toker = new java.util.StringTokenizer(value, ",");
      chapterStarts = new long[toker.countTokens()];
      for (int i = 0; i < chapterStarts.length; i++)
      {
        try
        {
          chapterStarts[i] = Long.parseLong(toker.nextToken());
        }
        catch (NumberFormatException nfe)
        {
          System.out.println("ERROR parsing chapterpoints metadata \"" + value + "\" of:" + nfe);
        }
      }
      return;
    }
    metadata.setProperty(name.intern(), value);
  }

  public void addMetadata(java.util.Map newData)
  {
    if (metadata == null)
      metadata = new java.util.Properties();
    java.util.Iterator walker = newData.entrySet().iterator();
    while (walker.hasNext())
    {
      java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
      if (ent.getKey() != null && ent.getValue() != null)
        addMetadata(ent.getKey().toString(), ent.getValue().toString());
    }
  }

  public java.util.Properties getMetadata()
  {
    return metadata;
  }

  public String getMetadataProperty(String name)
  {
    return (metadata == null) ? null : metadata.getProperty(name);
  }

  public boolean hasMetadata(String name)
  {
    return (metadata != null) && metadata.containsKey(name);
  }

  public boolean compareMetadata(java.util.Properties otherMetadata)
  {
    boolean rval = true;
    boolean debugMetaChanges = sage.Sage.getBoolean("debug_metadata_changes",false);
    java.util.Properties myMetadata = metadata;

    //		if (debugMetaChanges) System.out.println("   *******************************   DEBUGGING METADATA CHANGES");
    // first, we try metadata.equals(), if that works then we're guaranteed equal
    // the problem with this method is sometimes revisions in FFmpeg rearrange the
    // order metadata is reported which causes false errors, so we do a deeper scan
    // to double check entries in both lists

    if (debugMetaChanges) {
      // make sure neither list is null, this allows us to "compare" null lists for debugging purposes
      if (otherMetadata == null)
        otherMetadata = new java.util.Properties();
      if (myMetadata == null)
        myMetadata = new java.util.Properties();
    } else {
      // avoid NPEs
      if (metadata == null) {
        if (otherMetadata == null)
          return true;
        return false;
      }
    }

    if (myMetadata.equals(otherMetadata))
      return true;

    if (!debugMetaChanges) {
      // missing metadata, avoid NPE
      if (myMetadata == null && otherMetadata != null)
        return false;

      // size mismatch indicates immediate failure
      // if debugging changes, we want to see the differences, so don't do this
      if (myMetadata.size() != otherMetadata.size())
        return false;
    }
    // pass if empty property lists (both empty is handled by metadata.equals above)
    //		if (myMetadata.size() == 0)
    //			return true;

    try {
      java.util.Iterator myWalker = myMetadata.entrySet().iterator();

      // here we need to create a copy of otherMetadata so we can remove items as they are matched
      // at the end, if any items are left over, we report them as no longer existent
      java.util.Properties otherCopy = (java.util.Properties) otherMetadata.clone(); // shallow copy is enough for this...

      while (myWalker.hasNext()) {
        java.util.Map.Entry myEntry = (java.util.Map.Entry) myWalker.next();
        String theKey = myEntry.getKey().toString();
        String theValue = myEntry.getValue().toString();
        String otherValue = otherCopy.getProperty(theKey);

        // case 1, new in mine
        if (otherValue == null) {
          if (!debugMetaChanges)
            return false;

          // + key=value
          System.out.println("+ "+theKey+"="+theValue);
          // New metadata is not a failure case
          //					rval = false;
        } else {
          // case 2, key exists, values mismatch
          if (!theValue.equalsIgnoreCase(otherValue)) {
            if (!debugMetaChanges)
              return false;

            System.out.println("- "+theKey+"="+otherValue);
            System.out.println("+ "+theKey+"="+theValue);
            rval = false;
          } // else values match

          otherCopy.remove(theKey);
        }
      }

      if (otherCopy.size() > 0) {
        if (!debugMetaChanges)
          return false;

        // entries that are not in the current metadata
        java.util.Iterator otherWalker =  otherCopy.entrySet().iterator();
        while (otherWalker.hasNext()) {
          java.util.Map.Entry otherEntry = (java.util.Map.Entry) otherWalker.next();
          System.out.println("- "+otherEntry.getKey().toString()+"="+otherEntry.getValue().toString());
        }
        rval = false;
      }
    } catch (Throwable t) {
      System.out.println("Exception while comparing metadata (non-fatal): "+t);
      t.printStackTrace();
      return false;
    }

    return rval;
  }

  public String getFullPropertyString()
  {
    return getFullPropertyString(true);
  }
  public String getFullPropertyString(boolean includeMetadata)
  {
    return getFullPropertyString(includeMetadata, null);
  }
  public String getFullPropertyString(boolean includeMetadata, String extraProps)
  {
    // We do name=property and delimit each pair with a ';' character. We also allow escaping the = or ; or \ character
    // with a backslash.
    // Each stream has it's properties within a  [...] so we can tell when a stream's properties start and end
    StringBuffer sb = new StringBuffer();
    sb.append("f=");
    sb.append(escapeString(getFormatName()));
    sb.append(';');
    if (duration > 0)
    {
      sb.append("dur=");
      sb.append(duration);
      sb.append(';');
    }
    if (bitrate > 0)
    {
      sb.append("br=");
      sb.append(bitrate);
      sb.append(';');
    }
    if (drmProtection != null && drmProtection.length() > 0)
    {
      sb.append("drm=");
      sb.append(escapeString(drmProtection));
      sb.append(';');
    }
    if (packetSize > 0)
    {
      sb.append("ps=");
      sb.append(packetSize);
      sb.append(';');
    }
    if (singleStreamFormat == AUDIO_ONLY)
    {
      sb.append("audioonly=1;");
    }
    if (singleStreamFormat == VIDEO_ONLY)
    {
      sb.append("videoonly=1;");
    }
    if (hasChapters())
    {
      sb.append("numchp=");
      sb.append(chapterStarts.length);
      sb.append(';');
      for (int i = 0; i < chapterStarts.length; i++)
      {
        sb.append("chp");
        sb.append(i);
        sb.append('=');
        sb.append(chapterStarts[i]);
        sb.append(';');
      }
    }
    if (metadata != null && includeMetadata)
    {
      java.util.Iterator walker = metadata.entrySet().iterator();
      while (walker.hasNext())
      {
        java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
        sb.append("M"); // We prefix all meta properties with an uppercase M
        sb.append(escapeString(ent.getKey().toString()));
        sb.append('=');
        sb.append(escapeString(ent.getValue().toString()));
        sb.append(';');
      }
    }
    if (extraProps != null)
      sb.append(extraProps);
    if (numStreams > 0)
    {
      // Don't save the number of streams because it could only lead to conflict with the actual number
      // of streams we have the data for
      //sb.append("ns=");
      //sb.append(numStreams);
      //sb.append(';');
      for (int i = 0; i < streamFormats.length; i++)
      {
        sb.append('[');
        sb.append(streamFormats[i].getFullPropertyString());
        sb.append(']');
      }
    }
    return sb.toString();
  }

  public static ContainerFormat buildFormatFromString(String str)
  {
    if (str == null || str.length() == 0) return null;
    // We read up until the first unescaped [. After that we have substream info, each stream is within an unescaped [...]
    ContainerFormat rv = new ContainerFormat();
    // Extract the information from the container format
    int currNameStart = 0;
    int currValueStart = -1;
    boolean inStreamInfo = false;
    java.util.ArrayList streams = null;
    for (int i = 0; i < str.length(); i++)
    {
      char c = str.charAt(i);
      if (c == '\\')
      {
        // Escaped character, so skip the next one
        i++;
        continue;
      }
      else if (c == '=' && !inStreamInfo)
      {
        // We found the name=value delimeter, set the value start position
        currValueStart = i + 1;
      }
      else if (c == ';' && !inStreamInfo && currValueStart != -1)
      {
        // We're at the end of the name value pair, get their values!
        String name = str.substring(currNameStart, currValueStart - 1);
        String value = str.substring(currValueStart, i);
        currNameStart = i + 1;
        currValueStart = -1;
        try
        {
          if ("f".equals(name))
            rv.setFormatName(unescapeString(value));
          else if ("dur".equals(name))
            rv.duration = Long.parseLong(value);
          else if ("br".equals(name))
            rv.bitrate = Integer.parseInt(value);
          else if ("drm".equals(name))
            rv.drmProtection = unescapeString(value).intern();
          else if ("ps".equals(name))
            rv.packetSize = Integer.parseInt(value);
          else if ("audioonly".equals(name))
            rv.singleStreamFormat = ("1".equals(value) ? AUDIO_ONLY : rv.singleStreamFormat);
          else if ("videoonly".equals(name))
            rv.singleStreamFormat = ("1".equals(value) ? VIDEO_ONLY : rv.singleStreamFormat);
          else if (name.startsWith("M"))
            rv.addMetadata(unescapeString(name.substring(1)), unescapeString(value));
          else if ("numchp".equals(name))
            rv.chapterStarts = new long[Integer.parseInt(value)];
          else if (name.startsWith("chp"))
            rv.chapterStarts[Integer.parseInt(name.substring(3))] = Long.parseLong(value);
        }
        catch (Exception e)
        {
          System.out.println("ERROR parsing container format info " + str + " of:" + e);
        }
      }
      else if (c == '[')
      {
        inStreamInfo = true;
        currNameStart = i + 1;
        currValueStart = -1;
      }
      else if (c == ']')
      {
        if (inStreamInfo)
        {
          if (streams == null)
            streams = new java.util.ArrayList();
          BitstreamFormat newFormat = BitstreamFormat.buildFormatFromProperty(str.substring(currNameStart, i));
          if (newFormat != null)
            streams.add(newFormat);
          inStreamInfo = false;
        }
      }
    }
    if (streams != null && !streams.isEmpty())
      rv.setStreamFormats((BitstreamFormat[]) streams.toArray(new BitstreamFormat[0]));
    return rv;
  }

  public String getPrettyDesc()
  {
    StringBuffer sb = new StringBuffer(getFormatName());
    if (isDRMProtected())
    {
      sb.append(" DRM:");
      sb.append(drmProtection);
    }
    if (streamFormats != null && streamFormats.length > 0)
    {
      // Do video first and then the rest
      sb.append('[');
      boolean printedInfo = false;
      for (int i = 0; i < streamFormats.length; i++)
      {
        if (streamFormats[i] instanceof VideoFormat)
        {
          if (printedInfo)
            sb.append(", ");
          sb.append(streamFormats[i].getPrettyDesc());
          printedInfo = true;
        }
      }
      for (int i = 0; i < streamFormats.length; i++)
      {
        if (!(streamFormats[i] instanceof VideoFormat))
        {
          if (printedInfo)
            sb.append(", ");
          sb.append(streamFormats[i].getPrettyDesc());
          printedInfo = true;
        }
      }
      sb.append(']');
    }
    return sb.toString();
  }

  public String getPrimaryVideoFormat()
  {
    String vf = "";
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof VideoFormat)
      {
        if (streamFormats[i].isPrimary())
          return streamFormats[i].getFormatName();
        else if (vf.length() == 0)
          vf = streamFormats[i].getFormatName();
      }
    }
    return vf;
  }

  public BitstreamFormat[] getStreamFormats()
  {
    return streamFormats;
  }

  public String getPreferredFileExtension()
  {
    if (MPEG2_TS.equalsIgnoreCase(formatName))
      return ".ts";
    else if (MP3.equalsIgnoreCase(formatName))
      return ".mp3";
    else if (AVI.equalsIgnoreCase(formatName))
      return ".avi";
    else if (ASF.equalsIgnoreCase(formatName))
      return ".asf";
    else if (QUICKTIME.equalsIgnoreCase(formatName))
      return ".mp4"; // since these are usually what we want instead of actually Quicktime
    else if (MPEG1.equalsIgnoreCase(formatName) || MPEG2_PS.equalsIgnoreCase(formatName) || "dvd".equalsIgnoreCase(formatName))
      return ".mpg";
    else if ("psp".equalsIgnoreCase(formatName))
      return ".M4V";
    else if (MATROSKA.equalsIgnoreCase(formatName))
      return ".mkv";
    else
      return "." + formatName.toLowerCase();
  }

  public VideoFormat getVideoFormat()
  {
    VideoFormat vf = null;
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof VideoFormat)
      {
        if (streamFormats[i].isPrimary())
          return (VideoFormat)streamFormats[i];
        else if (vf == null)
          vf = (VideoFormat)streamFormats[i];
      }
    }
    return vf;
  }

  public AudioFormat getAudioFormat()
  {
    AudioFormat af = null;
    int minAudioIndex = Integer.MAX_VALUE;
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof AudioFormat)
      {
        if (streamFormats[i].isPrimary())
          return (AudioFormat)streamFormats[i];
        else if (af == null || (((AudioFormat)streamFormats[i]).getOrderIndex() < minAudioIndex))
        {
          af = (AudioFormat)streamFormats[i];
          minAudioIndex = af.getOrderIndex();
        }
      }
    }
    return af;
  }

  public VideoFormat[] getVideoFormats()
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof VideoFormat)
        rv.add(streamFormats[i]);
    }
    return (VideoFormat[]) rv.toArray(new VideoFormat[0]);
  }

  public AudioFormat[] getAudioFormats()
  {
    return getAudioFormats(true);
  }
  public AudioFormat[] getAudioFormats(boolean sorted)
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof AudioFormat)
        rv.add(streamFormats[i]);
    }
    AudioFormat[] rva = (AudioFormat[]) rv.toArray(new AudioFormat[0]);
    if (sorted)
      java.util.Arrays.sort(rva, AudioFormat.AUDIO_FORMAT_SORTER);
    return rva;
  }

  public SubpictureFormat[] getSubpictureFormats()
  {
    return getSubpictureFormats(null);
  }
  public SubpictureFormat[] getSubpictureFormats(String formatMatch)
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof SubpictureFormat &&
          (formatMatch == null || formatMatch.length() == 0 || formatMatch.equals(streamFormats[i].getFormatName())))
        rv.add(streamFormats[i]);
    }
    SubpictureFormat[] rva = (SubpictureFormat[]) rv.toArray(new SubpictureFormat[0]);
    return rva;
  }

  public String getPrimaryAudioFormat()
  {
    AudioFormat af = getAudioFormat();
    return af == null ? "" : af.getFormatName();
  }

  // Returns a set of strings that are appropriate for the user to select from different audio streams
  public String[] getAudioStreamSelectionDescriptors()
  {
    return getAudioStreamSelectionDescriptors(true);
  }
  public String[] getAudioStreamSelectionDescriptors(boolean sorted)
  {
    AudioFormat[] afs = getAudioFormats(sorted);
    String[] rv = new String[afs.length];
    for (int i = 0; i < afs.length; i++)
    {
      rv[i] = "# " + (i + 1);
      String lang = afs[i].getLanguage();
      if (lang != null && lang.length() > 0)
        rv[i] += " " + lang;
      rv[i] += " " + afs[i].getFormatName() + (afs[i].getChannels() == 6 ? " 5.1" : (afs[i].getChannels() == 7 ? " 6.1" : ""));
    }
    return rv;
  }

  // Returns a set of strings that are appropriate for the user to select from different subpicture streams
  public String[] getSubpictureStreamSelectionDescriptors()
  {
    return getSubpictureStreamSelectionDescriptors(null);
  }
  public String[] getSubpictureStreamSelectionDescriptors(String formatMatch)
  {
    SubpictureFormat[] afs = getSubpictureFormats(formatMatch);
    String[] rv = new String[afs.length];
    for (int i = 0; i < afs.length; i++)
    {
      rv[i] = "# " + (i + 1);
      String lang = afs[i].getLanguage();
      if (lang != null && lang.length() > 0)
        rv[i] += " " + lang + (afs[i].getForced() ? " [" + Sage.rez("Forced") + "]" : "");
    }
    return rv;
  }

  public int getNumAudioStreams()
  {
    int rv = 0;
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof AudioFormat)
        rv++;
    }
    return rv;
  }

  public int getNumVideoStreams()
  {
    int rv = 0;
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof VideoFormat)
        rv++;
    }
    return rv;
  }

  public void clearMetadata()
  {
    metadata = null;
  }

  public void clearMetadataExceptFor(String[] sortedRetainedProperties)
  {
    if (metadata != null)
    {
      java.util.Iterator walker = metadata.keySet().iterator();
      while (walker.hasNext())
      {
        String nextKey = walker.next().toString();
        if (java.util.Arrays.binarySearch(sortedRetainedProperties, nextKey) < 0)
          walker.remove();
      }
      if (metadata.isEmpty())
        metadata = null;
    }
  }

  public int getNumSubpictureStreams()
  {
    int rv = 0;
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof SubpictureFormat)
        rv++;
    }
    return rv;
  }

  public void addStream(BitstreamFormat bf)
  {
    if (streamFormats == null)
    {
      setStreamFormats(new BitstreamFormat[] { bf });
    }
    else
    {
      BitstreamFormat[] news = new BitstreamFormat[numStreams + 1];
      System.arraycopy(streamFormats, 0, news, 0, streamFormats.length);
      news[news.length - 1] = bf;
      setStreamFormats(news);
    }
  }

  // Returns true if changes were made
  public boolean validateExternalSubtitles()
  {
    boolean rv = false;
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof SubpictureFormat)
      {
        String p = ((SubpictureFormat) streamFormats[i]).getPath();
        if (p != null && p.length() > 0)
        {
          if (!new java.io.File(p).isFile())
          {
            rv = true;
            BitstreamFormat[] news = new BitstreamFormat[numStreams - 1];
            System.arraycopy(streamFormats, 0, news, 0, i);
            System.arraycopy(streamFormats, i + 1, news, i, numStreams - i - 1);
            setStreamFormats(news);
            i--;
          }
        }
      }
    }
    return rv;
  }

  public boolean hasExternalSubtitles()
  {
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof SubpictureFormat)
      {
        String p = ((SubpictureFormat) streamFormats[i]).getPath();
        if (p != null && p.length() > 0)
          return true;
      }
    }
    return false;
  }

  public boolean areExternalSubsBitmaps()
  {
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof SubpictureFormat)
      {
        if (MediaFormat.VOBSUB.equals(streamFormats[i].getFormatName()))
          return true;
      }
    }
    return false;
  }

  public boolean formatHasSubtitlePath(String testPath)
  {
    for (int i = 0; streamFormats != null && i < streamFormats.length; i++)
    {
      if (streamFormats[i] instanceof SubpictureFormat)
      {
        String p = ((SubpictureFormat) streamFormats[i]).getPath();
        if (p != null && p.equals(testPath))
          return true;
      }
    }
    return false;
  }

  // This is for multiplexed streams that only have an audio substream; used for knowing a TV channel shouldn't wait for
  // video to know that its OK to start playback
  public boolean hasAudioOnlyStream()
  {
    return singleStreamFormat == AUDIO_ONLY;
  }
  public boolean hasVideoOnlyStream()
  {
    return singleStreamFormat == VIDEO_ONLY;
  }

  public long[] getChapterStarts()
  {
    return chapterStarts;
  }

  public boolean hasChapters() { return chapterStarts != null && chapterStarts.length > 0; }

  public void setChapters(long[] inChapters)
  {
    chapterStarts = inChapters;
  }

  private static final byte AUDIO_ONLY = 1;
  private static final byte VIDEO_ONLY = 2;
  protected int numStreams;
  protected BitstreamFormat[] streamFormats;
  protected long duration; // -1 if unknown or invalid
  protected int bitrate;
  protected String drmProtection;
  protected java.util.Properties metadata;
  protected int packetSize;
  protected byte singleStreamFormat;
  protected long[] chapterStarts;
}
