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

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Playlist extends DBObject
{

  public static final byte AIRING_SEGMENT = 1;
  public static final byte PLAYLIST_SEGMENT = 2;
  public static final byte ALBUM_SEGMENT = 3;
  // temp media files are only allowed in the nowplaying playlist
  public static final byte TEMPMEDIAFILE_SEGMENT = 4;

  Playlist(int inID)
  {
    super(inID);
    wiz = Wizard.getInstance();
    name = "";
    segmentTypes = Pooler.EMPTY_BYTE_ARRAY;
    segments = new Vector<Object>();
    segmentTimes = new Vector<long[]>();
  }

  Playlist(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    wiz = Wizard.getInstance();
    name = in.readUTF();
    int numSegs = in.readInt();
    segmentTypes = new byte[numSegs];
    segments = new Vector<Object>();
    segmentTimes = new Vector<long[]>();
    for (int i = 0; i < numSegs; i++)
    {
      segmentTypes[i] = in.readByte();
      if (segmentTypes[i] == ALBUM_SEGMENT)
      {
        int titleID = readID(in, idMap);
        int artistID = readID(in, idMap);
        int genreID = 0;
        int yearID = 0;
        if (ver > 0x2B)
        {
          genreID = readID(in, idMap);
          yearID = readID(in, idMap);
        }
        AlbumData ad = new AlbumData(wiz.getTitleForID(titleID), wiz.getPersonForID(artistID),
            wiz.getCategoryForID(genreID), wiz.getYearForID(yearID));
        segments.add(ad);
        segmentTimes.add(new long[0]);
      }
      else
      {
        segments.add(new Integer(readID(in, idMap)));
        int numCuts = in.readInt();
        long[] currTimes = new long[numCuts];
        for (int j = 0; j < numCuts; j++)
          currTimes[j] = in.readLong();
        segmentTimes.add(currTimes);
      }
    }
    if (ver > 0x45)
    {
      buildPlaylistProps(in.readUTF());
    }
  }

  private void buildPlaylistProps(String str)
  {
    if (str != null && str.length() > 0)
    {
      if (playlistProps == null)
        playlistProps = new Properties();
      else
        playlistProps.clear();
      int currNameStart = 0;
      int currValueStart = -1;
      for (int i = 0; i < str.length(); i++)
      {
        char c = str.charAt(i);
        if (c == '\\')
        {
          // Escaped character, so skip the next one
          i++;
          continue;
        }
        else if (c == '=')
        {
          // We found the name=value delimeter, set the value start position
          currValueStart = i + 1;
        }
        else if (c == ';' && currValueStart != -1)
        {
          // We're at the end of the name value pair, get their values!
          String name = sage.media.format.ContainerFormat.unescapeString(str.substring(currNameStart, currValueStart - 1));
          String value = sage.media.format.ContainerFormat.unescapeString(str.substring(currValueStart, i));
          currNameStart = i + 1;
          currValueStart = -1;
          playlistProps.setProperty(name, value);
        }
      }
    }
    else if (playlistProps != null)
      playlistProps.clear();
  }

  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    boolean useLookupIdx = (flags & Wizard.WRITE_OPT_USE_ARRAY_INDICES) != 0;
    out.writeUTF(name);
    out.writeInt(segmentTypes.length);
    for (int i = 0; i < segmentTypes.length; i++)
    {
      out.writeByte(segmentTypes[i]);
      Object segElem = segments.get(i);
      if (segmentTypes[i] == ALBUM_SEGMENT)
      {
        AlbumData ad = (AlbumData) segElem;
        out.writeInt(ad.title != null ? (useLookupIdx ? ad.title.lookupIdx : ad.title.id) : 0);
        out.writeInt(ad.artist != null ? (useLookupIdx ? ad.artist.lookupIdx : ad.artist.id) : 0);
        out.writeInt(ad.genre != null ? (useLookupIdx ? ad.genre.lookupIdx : ad.genre.id) : 0);
        out.writeInt(ad.year != null ? (useLookupIdx ? ad.year.lookupIdx : ad.year.id) : 0);
        // there's no cut times for album segments
      }
      else
      {
        // playlist id or airing id
        out.writeInt(((Integer) segElem).intValue());
        long[] currTimes = segmentTimes.get(i);
        out.writeInt(currTimes.length);
        for (int j = 0; j < currTimes.length; j++)
          out.writeLong(currTimes[j]);
      }
    }
    if (playlistProps == null)
      out.writeUTF("");
    else
    {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<Object, Object> ent : playlistProps.entrySet())
      {
        sb.append(sage.media.format.MediaFormat.escapeString(ent.getKey().toString()));
        sb.append('=');
        sb.append(sage.media.format.MediaFormat.escapeString(ent.getValue().toString()));
        sb.append(';');
      }
      out.writeUTF(sb.toString());
    }
  }

  boolean validate()
  {
    boolean rv = true;
    for (int i = 0; i < segmentTypes.length; i++)
    {
      if (segmentTypes[i] == ALBUM_SEGMENT)
      {
        // We can't invalidate an album
        /*AlbumData a = (AlbumData) segments.get(i);
				if (a.getTitleStringer() == null || a.getArtistStringer() == null)
				{
					segmentTypes[i] = 0;
					rv = false;
				}*/
      }
      else if (segmentTypes[i] == AIRING_SEGMENT)
      {
        if (wiz.getAiringForID(((Integer) segments.get(i)).intValue()) == null)
        {
          segmentTypes[i] = 0;
          rv = false;
        }
      }
      else if (segmentTypes[i] == PLAYLIST_SEGMENT)
      {
        if (wiz.getPlaylistForID(((Integer) segments.get(i)).intValue()) == null)
        {
          segmentTypes[i] = 0;
          rv = false;
        }
      }
    }
    if (!rv)
    {
      List<Byte> fixedTypes = new ArrayList<Byte>();
      for (int i = 0; i < segmentTypes.length; i++)
      {
        if (segmentTypes[i] == 0)
        {
          segments.remove(fixedTypes.size());
          segmentTimes.remove(fixedTypes.size());
        }
        else
          fixedTypes.add(new Byte(segmentTypes[i]));
      }
      segmentTypes = new byte[fixedTypes.size()];
      for (int i = 0; i < fixedTypes.size(); i++)
        segmentTypes[i] = fixedTypes.get(i);
    }
    return true;
  }

  synchronized void update(DBObject fromMe)
  {
    Playlist p = (Playlist) fromMe;
    name = p.name;
    segmentTypes = p.segmentTypes.clone();
    segments = new Vector<Object>(p.segments);
    segmentTimes = new Vector<long[]>(p.segmentTimes);
    if (p.playlistProps != null)
      playlistProps = (Properties) p.playlistProps.clone();
    else
      playlistProps = null;
    super.update(fromMe);
  }

  // Circular playlists should be allowed, its an easy way to do looped playback, just
  // be careful it doesn't kill us on recursion anywhere!
  public synchronized String toString()
  {
    return safeToString(new HashSet<Playlist>());
  }

  private String safeToString(Set<Playlist> doneLists)
  {
    StringBuilder sb = new StringBuilder("Playlist[");
    sb.append(name);
    if (!doneLists.add(this))
    {
      sb.append(']');
      return sb.toString();
    }
    sb.append(' ');
    for (int i = 0; i < segmentTypes.length; i++)
    {
      if (segmentTypes[i] == ALBUM_SEGMENT || segmentTypes[i] == TEMPMEDIAFILE_SEGMENT)
        sb.append(segments.get(i));
      else if (segmentTypes[i] == AIRING_SEGMENT)
        sb.append(wiz.getAiringForID(((Integer) segments.get(i)).intValue()));
      else if (segmentTypes[i] == PLAYLIST_SEGMENT)
      {
        Playlist theList = wiz.getPlaylistForID(((Integer) segments.get(i)).intValue());
        if (theList != null)
          sb.append(theList.safeToString(doneLists));
        else
          sb.append("null");
      }
      sb.append(", ");
    }
    sb.append(']');
    return sb.toString();
  }

  public String getName() { return name; }

  public synchronized Object[] getSegments()
  {
    List<Object> rv = new ArrayList<Object>();
    for (int i = 0; i < segmentTypes.length; i++)
    {
      if (segmentTypes[i] == ALBUM_SEGMENT)
      {
        AlbumData allie = (AlbumData) segments.get(i);
        Album al = wiz.getCachedAlbum(allie.title, allie.artist);
        if (al == null)
        {
          // Create an album to fake this out
          rv.add(new Album(allie.title, allie.artist, allie.genre, allie.year));
        }
        else
          rv.add(al);
      }
      else if (segmentTypes[i] == AIRING_SEGMENT)
      {
        Airing newAir = wiz.getAiringForID(((Integer) segments.get(i)));
        if (newAir != null)
          rv.add(newAir);
      }
      else if (segmentTypes[i] == PLAYLIST_SEGMENT)
      {
        Playlist listy = wiz.getPlaylistForID(((Integer) segments.get(i)));
        if (listy != null)
          rv.add(listy);
      }
      else if (segmentTypes[i] == TEMPMEDIAFILE_SEGMENT)
        rv.add(segments.get(i));
    }
    return rv.toArray();
  }

  public synchronized MediaFile[] getMediaFiles()
  {
    return getMediaFiles(new HashSet<Playlist>());
  }

  synchronized MediaFile[] getMediaFiles(Set<Playlist> donePlaylists)
  {
    if (!donePlaylists.add(this))
      return new MediaFile[0];
    List<MediaFile> rv = new ArrayList<MediaFile>();
    for (int i = 0; i < segmentTypes.length; i++)
    {
      if (segmentTypes[i] == ALBUM_SEGMENT)
      {
        AlbumData allie = (AlbumData) segments.get(i);
        Album a = wiz.getCachedAlbum(allie.title, allie.artist);
        if (a != null)
        {
          Airing[] airs = a.getAirings();
          for (int j = 0; j < airs.length; j++)
          {
            MediaFile mf = wiz.getFileForAiring(airs[j]);
            if (mf != null)
              rv.add(mf);
          }
        }
      }
      else if (segmentTypes[i] == AIRING_SEGMENT)
      {
        MediaFile mf = wiz.getFileForAiring(wiz.getAiringForID(((Integer) segments.get(i)).intValue()));
        if (mf != null)
          rv.add(mf);
      }
      else if (segmentTypes[i] == PLAYLIST_SEGMENT)
      {
        Playlist listy = wiz.getPlaylistForID(((Integer) segments.get(i)).intValue());
        if (listy != null)
        {
          rv.addAll(Arrays.asList(listy.getMediaFiles(donePlaylists)));
        }
      }
      else if (segmentTypes[i] == TEMPMEDIAFILE_SEGMENT)
        rv.add((MediaFile) segments.get(i));
    }
    return rv.toArray(new MediaFile[0]);
  }

  public synchronized Object getSegment(int i)
  {
    if (segmentTypes.length == 0) return null;
    i = Math.max(0, Math.min(segmentTypes.length - 1, i));
    if (segmentTypes[i] == ALBUM_SEGMENT)
    {
      AlbumData allie = (AlbumData) segments.get(i);
      Album a = wiz.getCachedAlbum(allie.title, allie.artist);
      if (a == null)
      {
        // Create an album to fake this out
        return new Album(allie.title, allie.artist, allie.genre, allie.year);
      }
      else
        return a;
    }
    else if (segmentTypes[i] == AIRING_SEGMENT)
      return wiz.getAiringForID(((Integer) segments.get(i)).intValue());
    else if (segmentTypes[i] == PLAYLIST_SEGMENT)
      return wiz.getPlaylistForID(((Integer) segments.get(i)).intValue());
    else if (segmentTypes[i] == TEMPMEDIAFILE_SEGMENT)
      return segments.get(i);
    return null;
  }
  public synchronized int getSegmentType(int i)
  {
    if (segmentTypes.length == 0) return 0;
    i = Math.max(0, Math.min(segmentTypes.length - 1, i));
    return segmentTypes[i];
  }

  public int getNumSegments()
  {
    return segmentTypes.length;
  }

  public synchronized void setName(String s)
  {
    name = s;
    if (id > 0)
      wiz.logUpdate(this, Wizard.PLAYLIST_CODE);
  }

  private void addToPlaylist(byte b, Object o, int idx)
  {
    idx = Math.max(0, Math.min(segmentTypes.length, idx));
    byte[] newTypes = new byte[segmentTypes.length + 1];
    if (idx == segmentTypes.length)
      System.arraycopy(segmentTypes, 0, newTypes, 0, segmentTypes.length);
    else if (idx == 0)
      System.arraycopy(segmentTypes, 0, newTypes, 1, segmentTypes.length);
    else
    {
      System.arraycopy(segmentTypes, 0, newTypes, 0, idx);
      System.arraycopy(segmentTypes, idx, newTypes, idx + 1, segmentTypes.length - idx);
    }
    newTypes[idx] = b;
    segmentTypes = newTypes;
    segments.insertElementAt(o, idx);
    segmentTimes.insertElementAt(new long[0], idx);
    if (id > 0)
      wiz.logUpdate(this, Wizard.PLAYLIST_CODE);
  }

  public synchronized void addToPlaylist(Airing addMe)
  {
    addToPlaylist(AIRING_SEGMENT, new Integer(addMe.id), segmentTypes.length);
  }

  public synchronized void addToPlaylist(Album addMe)
  {
    addToPlaylist(ALBUM_SEGMENT, new AlbumData(addMe.getTitleStringer(),
        addMe.getArtistObj(), addMe.getGenreStringer(), addMe.getYearStringer()), segmentTypes.length);
  }

  public synchronized void addToPlaylist(Playlist addMe)
  {
    addToPlaylist(PLAYLIST_SEGMENT, new Integer(addMe.id), segmentTypes.length);
  }

  public synchronized void addToPlaylist(MediaFile addMe)
  {
    // ONLY allow temporary media files for the now playing playlist
    if (addMe.generalType == MediaFile.MEDIAFILE_LOCAL_PLAYBACK && id == 0)
      addToPlaylist(TEMPMEDIAFILE_SEGMENT, addMe, segmentTypes.length);
  }

  public synchronized void insertIntoPlaylist(Airing addMe, int index)
  {
    addToPlaylist(AIRING_SEGMENT, new Integer(addMe.id), index);
  }

  public synchronized void insertIntoPlaylist(Album addMe, int index)
  {
    addToPlaylist(ALBUM_SEGMENT, new AlbumData(addMe.getTitleStringer(),
        addMe.getArtistObj(), addMe.getGenreStringer(), addMe.getYearStringer()), index);
  }

  public synchronized void insertIntoPlaylist(Playlist addMe, int index)
  {
    addToPlaylist(PLAYLIST_SEGMENT, new Integer(addMe.id), index);
  }

  public synchronized void insertIntoPlaylist(MediaFile addMe, int index)
  {
    // ONLY allow temporary media files for the now playing playlist
    if (addMe.generalType == MediaFile.MEDIAFILE_LOCAL_PLAYBACK && id == 0)
      addToPlaylist(TEMPMEDIAFILE_SEGMENT, addMe, index);
  }

  public synchronized void removeFromPlaylist(int idx)
  {
    if (segmentTypes.length == 0) return;
    idx = Math.max(0, Math.min(segmentTypes.length, idx));
    byte[] newTypes = new byte[segmentTypes.length - 1];
    if (idx == segmentTypes.length - 1)
      System.arraycopy(segmentTypes, 0, newTypes, 0, segmentTypes.length - 1);
    else if (idx == 0)
      System.arraycopy(segmentTypes, 1, newTypes, 0, segmentTypes.length - 1);
    else
    {
      System.arraycopy(segmentTypes, 0, newTypes, 0, idx);
      System.arraycopy(segmentTypes, idx + 1, newTypes, idx, segmentTypes.length - idx - 1);
    }
    segmentTypes = newTypes;
    segments.remove(idx);
    segmentTimes.remove(idx);
    if (id > 0)
      wiz.logUpdate(this, Wizard.PLAYLIST_CODE);
  }
  public synchronized void removeFromPlaylist(Object o)
  {
    if (o instanceof DBObject)
      o = new Integer(((DBObject) o).id);
    int x = segments.indexOf(o);
    if (x != -1)
      removeFromPlaylist(x);
  }

  public synchronized void clear()
  {
    if (segmentTypes.length == 0) return;
    segmentTypes = Pooler.EMPTY_BYTE_ARRAY;
    segments.clear();
    segmentTimes.clear();
    if (id > 0)
      wiz.logUpdate(this, Wizard.PLAYLIST_CODE);
  }

  public synchronized void movePlaylistSegment(int idx, boolean moveUp)
  {
    idx = Math.max(0, Math.min(segmentTypes.length, idx));
    int newIdx = idx + (moveUp ? -1 : 1);
    newIdx = Math.max(0, Math.min(segmentTypes.length, newIdx));
    if (newIdx == idx) return;
    byte swapType = segmentTypes[newIdx];
    segmentTypes[newIdx] = segmentTypes[idx];
    segmentTypes[idx] = swapType;
    segments.insertElementAt(segments.remove(idx), newIdx);
    segmentTimes.insertElementAt(segmentTimes.remove(idx), newIdx);
    if (id > 0)
      wiz.logUpdate(this, Wizard.PLAYLIST_CODE);
  }

  public String getProperty(String name)
  {
    if (playlistProps == null)
      return "";
    String rv = playlistProps.getProperty(name);
    return (rv == null) ? "" : rv;
  }

  public synchronized void setProperty(String name, String value)
  {
    if (value == null && (playlistProps == null || !playlistProps.containsKey(name)))
      return;
    if (value != null && playlistProps != null && value.equals(playlistProps.getProperty(name)))
      return;
    if (value == null)
    {
      playlistProps.remove(name);
    }
    else
    {
      if (playlistProps == null)
        playlistProps = new Properties();
      playlistProps.setProperty(name, value);
    }
    wiz.logUpdate(this, Wizard.PLAYLIST_CODE);
  }

  public Properties getProperties()
  {
    if (playlistProps == null)
      return new Properties();
    return (Properties) playlistProps.clone();
  }

  // Returns null if no requirement, a String describing it otherwise
  public String doesRequirePCAccess(UIManager uiMgr)
  {
    if (!VideoFrame.getEnablePC()) return null;
    String[] restrictions = VideoFrame.getPCRestrictions();
    if (restrictions.length == 0) return null;
    Set<String> restrictionsSet = new HashSet<String>();
    doesRequirePCAccessRecursable(restrictions, null, restrictionsSet);
    if (!restrictionsSet.isEmpty())
    {
      StringBuilder sb = new StringBuilder();
      for (String str : restrictionsSet)
      {
        str = Channel.convertPotentialStationIDToName(str);
        if (sb.length() == 0)
          sb.append(str);
        else
        {
          sb.append(", ");
          sb.append(str);
        }
      }
      return sb.toString();
    }
    else
      return null;
  }

  private void doesRequirePCAccessRecursable(String[] pcRestrict, Set<Playlist> playlistSet, Set<String> restrictionsSet)
  {
    Playlist p;
    Airing a;
    for (int i = 0; i < segmentTypes.length; i++)
    {
      if (segmentTypes[i] == ALBUM_SEGMENT)
      {
        // these can't have pc restrictions'
        continue;
      }
      else if (segmentTypes[i] == AIRING_SEGMENT)
      {
        if ((a = wiz.getAiringForID(((Integer) segments.get(i)).intValue())) != null && !a.isMusic())
        {
          String[] airDanger = a.getRatingRestrictables();
          for (int j = 0; j < airDanger.length; j++)
          {
            if (Arrays.binarySearch(pcRestrict, airDanger[j]) >= 0)
            {
              restrictionsSet.add(airDanger[j]);
            }
          }
        }
      }
      else if (segmentTypes[i] == PLAYLIST_SEGMENT)
      {
        if ((p = wiz.getPlaylistForID(((Integer) segments.get(i)).intValue())) != null)
        {
          if (playlistSet != null && !playlistSet.add(p))
            continue;
          p.doesRequirePCAccessRecursable(pcRestrict,
              playlistSet == null ? (playlistSet = new HashSet<Playlist>()) : playlistSet,
              restrictionsSet);
        }
      }
      else if (segmentTypes[i] == TEMPMEDIAFILE_SEGMENT)
      {
        // these can't have pc restrictions
        continue;
      }
    }
  }

  public boolean isMusicPlaylist()
  {
    return isMusicRecursable(null);
  }
  private boolean isMusicRecursable(Set<Playlist> set)
  {
    Playlist p;
    Airing a;
    for (int i = 0; i < segmentTypes.length; i++)
    {
      if (segmentTypes[i] == ALBUM_SEGMENT)
      {
        continue;
      }
      else if (segmentTypes[i] == AIRING_SEGMENT)
      {
        if ((a = wiz.getAiringForID(((Integer) segments.get(i)).intValue())) != null)
        {
          if (!a.isMusic())
            return false;
        }
      }
      else if (segmentTypes[i] == PLAYLIST_SEGMENT)
      {
        if ((p = wiz.getPlaylistForID(((Integer) segments.get(i)).intValue())) != null)
        {
          if (set != null && !set.add(p))
            continue;
          if (!p.isMusicRecursable(set == null ? new HashSet<Playlist>() : set))
            return false;
        }
      }
      else if (segmentTypes[i] == TEMPMEDIAFILE_SEGMENT)
      {
        if (!((MediaFile) segments.get(i)).isMusic())
          return false;
      }
    }
    return true;
  }

  // This will create a new playlist from the given playlist file unless a playlist already exists with that name.
  public static Playlist importPlaylist(File theFile, String prefixName)
  {
    // First generate the name for the playlist
    String listName = theFile.getName();
    int lastDot = listName.lastIndexOf('.');
    if (lastDot != -1)
      listName = listName.substring(0, lastDot);
    if (prefixName != null && prefixName.length() > 0)
      listName = prefixName + listName;

    boolean reimportPlaylists = Sage.getBoolean("fully_reimport_playlists_every_scan", false);
    Playlist existingPlaylist = null;
    Playlist[] allLists = Wizard.getInstance().getPlaylists();
    for (int i = 0; i < allLists.length; i++)
    {
      if (allLists[i].name.equalsIgnoreCase(listName))
      {
        if (reimportPlaylists)
          existingPlaylist = allLists[i];
        else
          return allLists[i];
      }
    }
    if (Sage.DBG) System.out.println("Creating playlist object for playlist file: " + theFile);

    List<String> missingSegments = new ArrayList<String>();

    // Now add all of the elements of the playlist to the playlist.
    // If any of them are not there, do not import the playlist. Most likely those files
    // will be imported later in the scan, and then we can get the playlist on the next time around
    MediaFile[] mf = Wizard.getInstance().getFiles();
    List<Airing> playlistItems = new ArrayList<Airing>();
    if (theFile.getName().toLowerCase().endsWith(".m3u"))
    {
      // M3U playlist
      BufferedReader inStream = null;
      try
      {
        // The default is supposed to be Latin-1; but many people use UTF8 in there so we should try to detect that
        inStream = IOUtils.openReaderDetectCharset(theFile, Sage.I18N_CHARSET);
        String line = inStream.readLine();
        while (line != null)
        {
          line = line.trim();
          if (line.length() > 0 && !line.startsWith("#") && !line.startsWith("http://") &&
              !line.startsWith("mms://") && !line.startsWith("rtsp://") && !line.startsWith("rtp://"))
          {
            // # indicates it's a comment
            File f = new File(line);
            if (!f.isAbsolute())
            {
              // NOTE: Workaround for bug in Java where new File("C:\\foo\\", "\\foo.txt") resolves to C:\foo\foo.txt
              if (Sage.WINDOWS_OS && line.startsWith("\\") && line.length() > 1 && line.charAt(1) != '\\')
              {
                File parentRoot = theFile.getParentFile();
                while (parentRoot.getParentFile() != null)
                  parentRoot = parentRoot.getParentFile();
                f = new File(parentRoot, line);
              }
              else
              {
                // Change it to be relative to the M3u file
                f = new File(theFile.getParentFile(), line);
              }
              f = f.getCanonicalFile();
            }
            // Find the MediaFile for this playlist element
            boolean foundFile = false;
            MediaFile matchMF = Wizard.getInstance().getFileForFilePath(f);
            if (matchMF != null)
            {
              playlistItems.add(matchMF.getContentAiring());
              foundFile = true;
            }
            if (!foundFile)
            {
              // In case there's extra stuff before the comment marker
              if (line.indexOf('#') == -1)
              {
                if (Sage.DBG) System.out.println("Missing element in playlist, ignoring that element - playlist: " + theFile + " element: " + line + " resolvedPath=" + f);
                missingSegments.add(f.getAbsolutePath());
                //return null;
              }
            }
            else
            {
              if (Sage.DBG) System.out.println("Found file to add to playlist: " + line);
            }
          }
          line = inStream.readLine();
        }
      }
      catch (IOException e)
      {
        System.out.println("Error parsing playlist file " + theFile + " of " + e.toString());
        return null;
      }
      finally
      {
        if (inStream != null)
        {
          try
          {
            inStream.close();
          }
          catch (Exception e){}
        }
        inStream = null;
      }
    }
    else if (theFile.getName().toLowerCase().endsWith(".asx") || theFile.getName().toLowerCase().endsWith(".wax") ||
        theFile.getName().toLowerCase().endsWith(".wvx"))
    {
      // Windows Media playlist
      // Read the contents of the whole playlist file
      String fileStr = IOUtils.getFileAsString(theFile);

      // The files in these playlists have a structure we're looking for like this:
      /*
       * <Entry>
       *  <Ref href="file:///C:/mp3s/getdown.asf" />
       * </Entry>
       *
       * So we need to regex parse it to find all of the Entry elements, and then find the first
       * Ref element inside it and get the href attribute from it
       */
      Pattern pat = Pattern.compile(
          "\\<\\s*ENTRY\\s*\\>.*?\\<\\s*REF.*?HREF\\s*\\=\\s*\\\"\\s*(.*?)\\s*\\\".*?\\/\\>.*?\\<\\/\\s*ENTRY\\s*\\>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

      Matcher match = pat.matcher(fileStr);
      while (match.find())
      {
        String currURL = match.group(1);
        if (Sage.DBG) System.out.println("Found URL to add to playlist: " + currURL);
        if (currURL.startsWith("http://") || currURL.startsWith("mms://") || currURL.startsWith("rtsp://") || currURL.startsWith("rtp://"))
          continue;
        try
        {
          File f;
          if (currURL.indexOf(":") != -1)
          {
            URI uri = new URI(currURL);
            f = new File(uri);
          }
          else
          {
            f = new File(currURL);
            try
            {
              if (!f.isAbsolute())
                f = new File(theFile.getParentFile(), currURL);
              f = f.getCanonicalFile();
            }catch (IOException e)
            {
              if (Sage.DBG) System.out.println("Error getting file path to:" + currURL);
              return null;
            }
          }
          // Find the MediaFile for this playlist element
          boolean foundFile = false;
          MediaFile matchMF = Wizard.getInstance().getFileForFilePath(f);
          if (matchMF != null)
          {
            playlistItems.add(matchMF.getContentAiring());
            foundFile = true;
          }
          if (!foundFile)
          {
            if (Sage.DBG) System.out.println("Missing element in playlist, ignoring that element - playlist: " + theFile + " element: " + currURL + " resolvedPath=" + f);
            missingSegments.add(f.getAbsolutePath());
            //return null;
          }
          else
          {
            if (Sage.DBG) System.out.println("Found file to add to playlist: " + currURL);
          }
        }
        catch (Exception e)
        {
          if (Sage.DBG) System.out.println("Ignoring playlist: " + theFile + " due to bad URL: " + currURL);
          return null;
        }
      }
    }
    else if (theFile.getName().toLowerCase().endsWith(".wpl"))
    {
      // Windows Media playlist
      // Read the contents of the whole playlist file
      String fileStr = IOUtils.getFileAsString(theFile);

      // The files in these playlists have a structure we're looking for like this:
      /*
       *  <media src="../foobar/test.wma" />
       *
       * So we need to regex parse it to find all of the src attributes on media elements
       */
      Pattern pat = Pattern.compile(
          "<\\s*media.*?src\\s*\\=\\s*\\\"\\s*(.*?)\\s*\\\".*?\\/\\>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

      Matcher match = pat.matcher(fileStr);
      while (match.find())
      {
        String currPath = match.group(1);
        // Fix the HTML entity reference stuff
        currPath = currPath.replaceAll("\\&apos\\;", "'");
        currPath = currPath.replaceAll("\\&quot\\;", "\"");
        currPath = currPath.replaceAll("\\&lt\\;", "<");
        currPath = currPath.replaceAll("\\&gt\\;", ">");
        currPath = currPath.replaceAll("\\&amp\\;", "&");
        // Also fix any UTF-8 issues
        File f;
        try
        {
          currPath = new String(currPath.getBytes(), "UTF-8");
          f = new File(currPath);
          if (!f.isAbsolute())
            f = new File(theFile.getParentFile(), currPath);
          f = f.getCanonicalFile();
        }catch (IOException e)
        {
          if (Sage.DBG) System.out.println("Error getting file path to:" + currPath);
          return null;
        }
        // Find the MediaFile for this playlist element
        boolean foundFile = false;
        MediaFile matchMF = Wizard.getInstance().getFileForFilePath(f);
        if (matchMF != null)
        {
          playlistItems.add(matchMF.getContentAiring());
          foundFile = true;
        }
        if (!foundFile)
        {
          if (Sage.DBG) System.out.println("Missing element in playlist, ignoring that element - playlist: " + theFile + " element: " + currPath + " resolvedPath=" + f);
          missingSegments.add(f.getAbsolutePath());
          //return null;
        }
        else
        {
          if (Sage.DBG) System.out.println("Found file to add to playlist: " + currPath);
        }
      }
    }
    else
    {
      System.out.println("Invalid playlist format: " + theFile);
      return null;
    }
    if (playlistItems.size() == 0)
    {
      if (Sage.DBG) System.out.println("Ignoring playlist due to zero size: " + theFile);
      SeekerSelector.getInstance().addIgnoreFile(theFile);
      return null;
    }
    Playlist rv;
    if (reimportPlaylists && existingPlaylist != null)
    {
      rv = existingPlaylist;
      rv.clear();
    }
    else
      rv = Wizard.getInstance().addPlaylist(listName);
    for (int i = 0; i < playlistItems.size(); i++)
    {
      Object obj = playlistItems.get(i);
      if (obj instanceof Airing)
        rv.addToPlaylist((Airing) obj);
      else if (obj instanceof Album)
        rv.addToPlaylist((Album) obj);
      else if (obj instanceof Playlist)
        rv.addToPlaylist((Playlist) obj);
    }
    for (int i = 0; i < missingSegments.size(); i++)
    {
      sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createPlaylistMissingSegmentMsg(theFile.getAbsolutePath(), missingSegments.get(i).toString()));
    }
    return rv;
  }

  // This checks to make sure all of the Airing objects inside of the Playlist that should correspond
  // to MediaFiles actually do. If they don't, then an attempt is made to find the MediaFile that they should match
  // and re-associate it. This can easily happen by doing a re-import of your music library.
  public boolean verifyPlaylist()
  {
    return verifyPlaylist(new HashSet<Playlist>());
  }
  private boolean verifyPlaylist(Set<Playlist> donePlaylists)
  {
    if (!donePlaylists.add(this))
      return true;
    boolean status = false;
    for (int i = 0; i < segmentTypes.length; i++)
    {
      if (segmentTypes[i] == ALBUM_SEGMENT)
      {
        // We don't need to check albums since they're referenced by name and not ID
        status = true;
      }
      else if (segmentTypes[i] == TEMPMEDIAFILE_SEGMENT)
      {
        status = true;
      }
      else if (segmentTypes[i] == AIRING_SEGMENT)
      {
        Airing aid = wiz.getAiringForID(((Integer) segments.get(i)).intValue());
        MediaFile mf = wiz.getFileForAiring(aid);
        if (mf == null && (aid.isMusic() || (!aid.isMusic() && aid.getEndTime() < Sage.time())))
        {
          Show testShow = aid.getShow();
          // OK, we've got an airing segment that doesn't match an Airing object anymore. First find all of
          // the Show objects that have matching data for this Airing. (just check title & episode name, if multiple
          // ones match on that then go into more detail and even use the Airing times if required, be sure anything
          // checked against also has a MediaFile object itself)
          DBObject[] rawShows = wiz.getRawAccess(Wizard.SHOW_CODE, (byte)0);
          Airing bestAirMatch = null;
          for (int j = 0; j < rawShows.length; j++)
          {
            Show s = (Show) rawShows[j];
            if (s != null && testShow.title == s.title && testShow.getEpisodeName().equals(s.getEpisodeName()))
            {
              Airing[] currAirs = wiz.getAirings(s, 0);
              if (currAirs != null && currAirs.length > 0)
              {
                for (int k = 0; k < currAirs.length; k++)
                {
                  if (wiz.getFileForAiring(currAirs[k]) != null)
                  {
                    if (bestAirMatch == null)
                      bestAirMatch = currAirs[k];
                    else
                    {
                      // First check on airing start time since file modification times aren't easily changed without retagging and
                      // have almost no chance of false positives
                      if (bestAirMatch.getStartTime() != aid.getStartTime() && currAirs[k].getStartTime() == aid.getStartTime())
                      {
                        bestAirMatch = currAirs[k];
                      }
                      else if (bestAirMatch.getStartTime() != aid.getStartTime())
                      {
                        // Next check is for a matching track number
                        if (bestAirMatch.partsB != aid.partsB && currAirs[k].partsB == aid.partsB)
                        {
                          bestAirMatch = currAirs[k];
                        }
                        // Otherwise, just keep what we've got.
                      }
                      // Otherwise, just keep what we've got.
                    }
                  }
                }
              }
            }
          }
          if (bestAirMatch != null)
          {
            if (Sage.DBG) System.out.println("Playlist is swapping out old invalid Airing for a new one, old=" + aid + " new=" + bestAirMatch);
            segments.setElementAt(new Integer(bestAirMatch.id), i);
            if (id > 0)
              wiz.logUpdate(this, Wizard.PLAYLIST_CODE);
            status = true;
          }
        }
        else
          status = true;
      }
      else if (segmentTypes[i] == PLAYLIST_SEGMENT)
      {
        Playlist listy = wiz.getPlaylistForID(((Integer) segments.get(i)).intValue());
        if (listy != null)
        {
          status |= listy.verifyPlaylist(donePlaylists);
        }
      }
    }
    return status;
  }

  public boolean isSingleItemPlaylist()
  {
    return segmentTypes.length == 1 && segmentTypes[0] == AIRING_SEGMENT;
  }

  private class AlbumData
  {
    public AlbumData(Stringer t, Person ar, Stringer g, Stringer y)
    {
      title = t;
      artist = ar;
      genre = g;
      year = y;
    }

    Stringer title;
    Person artist;
    Stringer genre;
    Stringer year;

    public String toString()
    {
      return Sage.rez("Song_By_Artist", new Object[] { (title == null ? "" : title.name),
          (artist == null ? "" : artist.name) });
    }
  }

  String name;
  byte[] segmentTypes;
  // Consists of airingIDs, playlistIDs or Albums
  Vector<Object> segments;
  // each element is a long[] with even size, pairs representing start/stop times, but this
  // won't work well until we have more accurate seeking in our demux
  Vector<long[]> segmentTimes;
  Properties playlistProps;

  private Wizard wiz;
}
