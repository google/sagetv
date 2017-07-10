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
package sage.media.sub;

import sage.Sage;
import sage.io.BufferedSageFile;
import sage.io.LocalSageFile;
import sage.io.RemoteSageFile;
import sage.io.SageDataFile;

import java.util.ArrayList;

/**
 *
 * @author Narflex
 */
public class VobSubSubtitleHandler extends SubtitleHandler
{
  /** Creates a new instance of VobSubSubtitleHandler */
  public VobSubSubtitleHandler(sage.VideoFrame vf, sage.media.format.ContainerFormat inFormat)
  {
    super(inFormat);
    this.vf = vf;
  }

  public String getMediaFormat()
  {
    return sage.media.format.MediaFormat.VOBSUB;
  }

  public void loadSubtitlesFromFiles(sage.MediaFile sourceFile)
  {
    // Only one file for this format
    sage.media.format.SubpictureFormat[] subs = mediaFormat.getSubpictureFormats(sage.media.format.MediaFormat.VOBSUB);
    if (subs.length == 0)
      return;
    java.io.File subFile = new java.io.File(subs[0].getPath());
    java.io.BufferedReader inStream = null;
    getLanguages();
    try
    {
      inStream = sage.IOUtils.openReaderDetectCharset(subFile, sage.Sage.BYTE_CHARSET, sourceFile.isLocalFile());
      String line = inStream.readLine();
      if (line != null && line.indexOf("VobSub index file") == -1)
      {
        if (sage.Sage.DBG) System.out.println("Invalid VobSub IDX file, bad comment on first line: " + line);
        return;
      }
      String orgSubPath = subs[0].getPath();
      bitmapFile = new java.io.File(orgSubPath.substring(0, orgSubPath.length() - 3) + "sub");
      if (!sourceFile.isLocalFile())
      {
        cleanupBitmapFile = true;
        sage.NetworkClient.getSN().requestMediaServerAccess(bitmapFile, true);
        frf = new SageDataFile(new BufferedSageFile(new RemoteSageFile(Sage.preferredServer, bitmapFile), 32768), Sage.I18N_CHARSET);
      }
      else
      {
        frf = new SageDataFile(new BufferedSageFile(new LocalSageFile(bitmapFile, true), 32768), Sage.I18N_CHARSET);
      }

      int idx;
      String currLang = null;
      byte alpha = (byte) 0xFF;
      int langIdx = -1;
      java.util.Vector allFileOffsets = new java.util.Vector();
      while (line != null)
      {
        if (line.startsWith("id:"))
        {
          idx = line.indexOf(',');
          if (idx != -1)
          {
            langIdx++;
            if (subLangs != null && langIdx < subLangs.length)
              currLang = subLangs[langIdx];
            else
              currLang = line.substring(3, idx).trim();
            subEntries = new ArrayList<SubtitleEntry>();
            subLangEntryMap.put(currLang, subEntries);
            if (SUB_DEBUG) System.out.println("VobSub IDX loading found language: " + currLang);
            idx = line.indexOf("index:");
          }
        }
        else if (line.startsWith("size:"))
        {
          if (line.indexOf("576") != -1)
            flags = sage.MiniPlayer.PUSHBUFFER_SUBPIC_PAL_FLAG;
          if (vf.getUIMgr().getRootPanel().getRenderEngine() instanceof sage.MiniClientSageRenderer &&
              ((sage.MiniClientSageRenderer) vf.getUIMgr().getRootPanel().getRenderEngine()).isMediaExtender() &&
              !((sage.MiniClientSageRenderer) vf.getUIMgr().getRootPanel().getRenderEngine()).isSupportedVideoCodec("VOBSUBHD") &&
              (line.indexOf("x720") != -1 || line.indexOf("1080") != -1 || line.indexOf("1920") != -1 || line.indexOf("1280") != -1))
          {
            if (sage.Sage.DBG) System.out.println("WARNING Detected high-definition VOBSUB subtitles in file which is playing on an extender; disable them since the hardware can't handle this! size=" + line);
            forciblyDisable = true;
            return;
          }

        }
        else if (line.startsWith("alpha:"))
        {
          idx = line.indexOf('%');
          if (idx != -1)
          {
            try
            {
              alpha = (byte)((Integer.parseInt(line.substring("alpha:".length() + 1, idx)) * 255 / 100) & 0xFF);
            }
            catch (Exception ex)
            {
              if (sage.Sage.DBG) System.out.println("Error parsing alpha entry in VobSub idx file of: " + ex);
            }
          }
        }
        else if (line.startsWith("palette:"))
        {
          parsePaletteInfo(line, alpha);
        }
        else if (line.startsWith("timestamp:") && currLang != null)
        {
          idx = line.indexOf(',');
          if (idx != -1)
          {
            try
            {
              long[] timeRange = parseTimeRange(line.substring("timestamp:".length() + 1, idx).trim());
              idx = line.indexOf("filepos:");
              long fileOffset = Long.parseLong(line.substring(idx + "filepos:".length()).trim(), 16);
              if (sage.Sage.DBG) System.out.println("Found new VobSub idx entry time=" + timeRange[0] + " offset=" + fileOffset);
              allFileOffsets.add(new Long(fileOffset));
              insertSubtitleEntry(new SubtitleEntry(fileOffset, 0, timeRange[0], timeRange[1]));
            }
            catch (Exception ee)
            {
              if (sage.Sage.DBG) System.out.println("Error parsing time range/offset for VobSub idx entry: " + line);
            }
          }
        }
        line = inStream.readLine();
      }

      // Now go through the whole map of entries and figure out their proper sizes
      Long[] offsetData = (Long[]) allFileOffsets.toArray(new Long[allFileOffsets.size()]);
      java.util.Arrays.sort(offsetData);
      for (java.util.List<SubtitleEntry> currSubSet : subLangEntryMap.values())
      {
        for (int i = 0; i < currSubSet.size(); i++)
        {
          SubtitleEntry currEntry = currSubSet.get(i);
          int offsetIdx = java.util.Arrays.binarySearch(offsetData, new Long(currEntry.offset));
          if (offsetIdx >= 0)
          {
            if (offsetIdx < offsetData.length - 1)
              currEntry.size = offsetData[offsetIdx + 1].longValue() - offsetData[offsetIdx].longValue();
            else
              currEntry.size = bitmapFile.length() - offsetData[offsetIdx].longValue();
          }
          else
          {
            if (sage.Sage.DBG) System.out.println("ERROR did not find VobSub offset in sorted array offset=" + currEntry.offset);
          }
        }
      }
    }
    catch (java.io.IOException e)
    {
      if (sage.Sage.DBG) System.out.println("ERROR loading subtitle file from " + subs[0] + " of " + e);
      if (sage.Sage.DBG) e.printStackTrace();
    }
    finally
    {
      if (subFile != null && !sourceFile.isLocalFile())
        subFile.delete();
      if (inStream != null)
      {
        try
        {
          inStream.close();
        }
        catch (Exception e){}
      }
    }
  }

  private void parsePaletteInfo(String pinfo, byte alpha)
  {
    java.util.StringTokenizer toker = new java.util.StringTokenizer(pinfo.substring("palette:".length()).trim(), ", ");
    if (toker.countTokens() == 16)
    {
      paletteData = new byte[64];
      int num = 0;
      StringBuffer info = (sage.Sage.DBG) ? new StringBuffer() : null;
      while (toker.hasMoreTokens())
      {
        String nextValue = toker.nextToken();
        paletteData[num++] = alpha;
        // Now do the colorspace conversion
        int r = Integer.parseInt(nextValue.substring(0, 2), 16);
        int g = Integer.parseInt(nextValue.substring(2, 4), 16);
        int b = Integer.parseInt(nextValue.substring(4), 16);
        int y = clipUint8(Math.round( 0.1494f  * r + 0.6061f * g + 0.2445f * b));
        int u = clipUint8(Math.round( 0.6066f  * r - 0.4322f * g - 0.1744f * b + 128));
        int v = clipUint8(Math.round(-0.08435f * r - 0.3422f * g + 0.4266f * b + 128));
        y = y * 219 / 255 + 16;
        paletteData[num++] = (byte)(y & 0xFF);
        paletteData[num++] = (byte)(u & 0xFF);
        paletteData[num++] = (byte)(v & 0xFF);
        if (sage.Sage.DBG)
        {
          info.append(Integer.toString(paletteData[num - 4] & 0xFF, 16));
          info.append(',');
          info.append(Integer.toString(paletteData[num - 3] & 0xFF, 16));
          info.append(',');
          info.append(Integer.toString(paletteData[num - 2] & 0xFF, 16));
          info.append(',');
          info.append(Integer.toString(paletteData[num - 1] & 0xFF, 16));
          info.append(' ');
        }
      }
      if (SUB_DEBUG)
      {
        System.out.println("Loaded VobSub palette of: " + info);
      }
    }
    else
    {
      if (sage.Sage.DBG) System.out.println("VobSub IDX has INVALID palette entry, it doesn't have 16 items: " + pinfo);
    }

  }

  public void cleanup()
  {
    if (frf != null)
    {
      try
      {
        frf.close();
      }
      catch (Exception e){}
      frf = null;
    }
    if (bitmapFile != null && cleanupBitmapFile)
    {
      sage.NetworkClient.getSN().requestMediaServerAccess(bitmapFile, false);
      bitmapFile = null;
    }
  }

  public boolean areTextBased()
  {
    return false;
  }

  // If storage isn't big enough; then that needs to be noticed by the return value being larger than the size of 'storage'
  // The array can then be re-allocated and this method called again to load the complete data
  public int getSubtitleBitmap(long currMediaTime, boolean willDisplay, byte[] storage, boolean stripHeaders)
  {
    if (forciblyDisable)
      return 0;

    // We don't process the data if its embedded
    if (!external)
      stripHeaders = false;
    else
      currMediaTime += delay;
    int rv = 0;

    subtitleLock.writeLock().lock();

    try
    {
      boolean foundValid = false;
      for (int i = getSubEntryIndex(currMediaTime); i < subEntries.size(); i++)
      {
        SubtitleEntry currEntry = subEntries.get(i);
        SubtitleEntry nextEntry = ((i < subEntries.size() - 1) ? subEntries.get(i + 1) : null);
        if (currEntry.start <= currMediaTime)
        {
          if ((currEntry.duration > 0 && currEntry.start + currEntry.duration > currMediaTime) ||
              (currEntry.duration == 0 && (nextEntry == null || nextEntry.start > currMediaTime)))
          {
            foundValid = true;
            if (willDisplay)
            {
              subEntryPos = i;
              currBlank = false;
            }
            try
            {
              if (stripHeaders)
              {
                frf.seek(currEntry.offset);
                while (frf.position() < currEntry.offset + currEntry.size)
                {
                  // Check for MPEG2 packs and PES packets and strip off the headers and only send back the payload
                  int nextHeader = frf.readInt();
                  if (nextHeader == 0x1BA)
                  {
                    frf.skip(9); // SCR + muxrate
                    int stuffLength = frf.readByte() & 0x7;
                    if (stuffLength > 0)
                      frf.skip(stuffLength);
                    continue;
                  }
                  else if (nextHeader == 0x1BD || nextHeader == 0x1BE)
                  {
                    int pesLength = frf.readUnsignedShort();
                    if (nextHeader == 0x1BE)
                    {
                      frf.skip(pesLength);
                      continue;
                    }
                    long packetEnd = frf.position() + pesLength;
                    // Skip the first flags
                    frf.skip(1);
                    // Check the PTS/DTS flags
                    int flags = frf.readUnsignedByte();
                    int headerLength = frf.readUnsignedByte();
                    long newPTS = 0;
                    // We don't actually need the PTS's for anything
                    /*if ((flags & 0xC0) == 0x80)
										{
											int x = frf.readUnsignedByte();
											newPTS = ((long)(x & 0x0E)) << 29;
											int shot = frf.readUnsignedShort() & 0xFFFE;
											newPTS += shot << 14;
											shot = frf.readUnsignedShort() & 0xFFFE;
											newPTS += shot >> 1;
											frf.skipBytes(headerLength - 5 + 1);
										}
										else if ((flags & 0xC0) == 0xC0)
										{
											int x = frf.readUnsignedByte();
											newPTS = ((long)(x & 0x0E)) << 29;
											int shot = frf.readUnsignedShort() & 0xFFFE;
											newPTS += shot << 14;
											shot = frf.readUnsignedShort() & 0xFFFE;
											newPTS += shot >> 1;
											x = frf.readUnsignedByte();
											long dts = ((long)(x & 0x0E)) << 29;
											shot = frf.readUnsignedShort() & 0xFFFE;
											dts += shot << 14;
											shot = frf.readUnsignedShort() & 0xFFFE;
											dts += shot >> 1;
											frf.skipBytes(headerLength - 10 + 1);
										}
										else*/
                    frf.skip(headerLength + 1);
                    if (rv == 0)
                    {
                      // Put the 45k PTS in the first 4 bytes
                      long pts45 = (currEntry.start - delay) * 45;
                      storage[0] = (byte)((pts45 >> 24) & 0xFF);
                      storage[1] = (byte)((pts45 >> 16) & 0xFF);
                      storage[2] = (byte)((pts45 >> 8) & 0xFF);
                      storage[3] = (byte)(pts45 & 0xFF);
                      rv = 4;
                    }
                    int currSize = (int)(packetEnd - frf.position());
                    int currRead = Math.min(currSize, storage.length - rv);
                    if (currRead > 0)
                      frf.readFully(storage, rv, currRead);
                    rv += currSize;
                  }
                  else
                  {
                    if (sage.Sage.DBG) System.out.println("WARNING VobSub parser did not find MPEG2 PES private stream, padding or pack header! found: 0x" +
                        Integer.toString(nextHeader, 16));
                    break;
                  }
                }
                if (rv > 0)
                {
                  return rv;
                }
              }
              // If we made it here then either we're not stripping headers or the stripping failed (and if it fails we send the whole block of data instead)
              // Put the 45k PTS in the first 4 bytes
              long pts45 = (currEntry.start - delay) * 45;
              storage[0] = (byte)((pts45 >> 24) & 0xFF);
              storage[1] = (byte)((pts45 >> 16) & 0xFF);
              storage[2] = (byte)((pts45 >> 8) & 0xFF);
              storage[3] = (byte)(pts45 & 0xFF);
              if (external)
              {
                frf.seek(currEntry.offset);
                frf.readFully(storage, 4, (int)Math.min(storage.length - 4, currEntry.size));
              }
              else
              {
                System.arraycopy(currEntry.bitmapdata, 0, storage, 4, (int)Math.min(storage.length - 4, currEntry.size));
                if (subEntries.size() > 20)
                {
                  // Don't let these fill up too much in memory
                  subEntries.remove(0);
                  i--;
                  subEntryPos--;
                }
              }
              return rv = (int)currEntry.size + 4;
            }
            catch (java.io.IOException e)
            {
              if (sage.Sage.DBG) System.out.println("ERROR reading VobSub data:" + e);
              return 0;
            }
          }
          else if (!foundValid && willDisplay)
          {
            subEntryPos = i; // We're on a blank; so jump ahead one in the TTU estimate
            currBlank = true;
          }
        }
        else
        {
          if (willDisplay && !foundValid)
          {
            subEntryPos = i; // We're on a blank; so jump ahead one in the TTU estimate
            currBlank = true;
          }
          break;
        }
      }
    }
    finally
    {
      subtitleLock.writeLock().unlock();
    }
    //System.out.println("Got Subtitle bitmap subEntryPos=" + subEntryPos);
    return rv;
  }

  public byte[] getPaletteData()
  {
    if (!didInitPalette)
      didInitPalette = true;
    return paletteData;
  }

  public boolean isPaletteInitialized()
  {
    return didInitPalette || forciblyDisable;
  }

  private static int clipUint8(int x)
  {
    if (x > 255)
      return 255;
    else if (x < 0)
      return 0;
    else
      return x;
  }

  public int getSubtitleBitmapFlags()
  {
    return flags;
  }

  protected boolean insertEntryForPostedInfo(long time, long dur, byte[] rawText)
  {
    if (rawText == null || rawText.length == 0 || forciblyDisable)
      return false;
    if (rawText.length > 8 && rawText[4] == 'e' && rawText[5] == 's' && rawText[6] == 'd' && rawText[7] == 's')
    {
      // This is the initialization info
      try
      {
        if (paletteData == null)
        {
          int offset = 0;
          // First 4 bytes are the tag size
          offset += 4;
          // Next 4 are esds (already verified)
          offset += 4;
          // Next 4 are ver+flags
          offset += 4;
          // Next should be 0x03
          offset++;
          // Variable length field
          while ((rawText[offset] & 0x80) != 0)
            offset++;
          // for the last byte of the length
          offset++;
          // ID, priority, 0x04 tag
          offset += 4;
          // Variable length field
          while ((rawText[offset] & 0x80) != 0)
            offset++;
          // for the last byte of the length
          offset++;
          if ((rawText[offset] & 0xFF) != 0xE0)
          {
            System.out.println("ERROR MP4 VobSub subtitles don't have the right ID!!!!");
            return false;
          }
          // Skip the object ID, stream type, buffer sizes and bitrates and the 0x05 tag
          offset += 14;
          // Variable length field
          while ((rawText[offset] & 0x80) != 0)
            offset++;
          // for the last byte of the length
          offset++;
          // NFLX 04/21/09 - We should just leave this at PAL. With the test files I have
          // so far they both look correct this way. NTSC can possibly end up with things scaled
          // off the screen (since its 480 instead of 576); and we can't really figure out the
          // size from the data because its possible it won't go over 480 even for PAL subpics
          // NOTE: We need to fix this so it uses the right sizing still!!!!
          flags = sage.MiniPlayer.PUSHBUFFER_SUBPIC_PAL_FLAG;
          // Now we're at the start of the palette data
          if (rawText.length >= 64 + offset)
          {
            paletteData = new byte[64];
            System.arraycopy(rawText, offset, paletteData, 0, 64);
          }
          else
          {
            System.out.println("ERROR MP4 VobSub init data was not long enough! length=" + rawText.length);
            return false;
          }
          // There's an extra 12 bytes a the end which I'm not sure what its for yet
          // NOTE: It's probably the first subtitle's data
          // Nothing to insert here
          if (SUB_DEBUG) System.out.println("Got the subpicture palette for embedded VobSub!");
          return false;
        }
      }
      catch (Exception e)
      {
        System.out.println("ERROR with VobSub init of:" + e);
        e.printStackTrace();
        return false;
      }
      return false; // skip the init info since we already processed it.
    }
    else if (paletteData != null && initPrefix != null && rawText.length >= initPrefixLength)
    {
      // Check if this is a resend of the init data and strip it off if so
      int i = 0;
      for (;i < initPrefix.length; i++)
        if (rawText[i] != initPrefix[i])
          break;
      if (i == initPrefix.length)
      {
        byte[] myData = new byte[rawText.length - initPrefixLength];
        System.arraycopy(rawText, initPrefixLength, myData, 0, myData.length);
        rawText = myData;
      }
    }
    else if (paletteData == null)
    {
      // See if this is the init for a Matroska DVD sub
      String configText = new String(rawText);
      int sidx = configText.indexOf("size:");
      int pidx = configText.indexOf("palette:");
      int aidx = configText.indexOf("alpha:");
      if (pidx != -1)
      {
        if (sidx == -1)
          flags = sage.MiniPlayer.PUSHBUFFER_SUBPIC_PAL_FLAG; // default to PAL if size isn't there since its bigger
        else
        {
          int endSize = configText.indexOf('\n', sidx);
          if (endSize == -1)
          {
            if (sage.Sage.DBG) System.out.println("ERROR parsing Matroska DVDSUB header, can't find newline terminator after size:");
            return false;
          }
          sidx = configText.indexOf("576", sidx);
          if (sidx != -1 && sidx < endSize)
            flags = sage.MiniPlayer.PUSHBUFFER_SUBPIC_PAL_FLAG;
          if (vf.getUIMgr().getRootPanel().getRenderEngine() instanceof sage.MiniClientSageRenderer &&
              !((sage.MiniClientSageRenderer) vf.getUIMgr().getRootPanel().getRenderEngine()).isSupportedVideoCodec("VOBSUBHD") &&
              ((sage.MiniClientSageRenderer) vf.getUIMgr().getRootPanel().getRenderEngine()).isMediaExtender())
          {
            int testIdx = configText.indexOf("x720", sidx);
            if (testIdx != -1 && testIdx < endSize)
              forciblyDisable = true;
            testIdx = configText.indexOf("1080", sidx);
            if (testIdx != -1 && testIdx < endSize)
              forciblyDisable = true;
            testIdx = configText.indexOf("1920", sidx);
            if (testIdx != -1 && testIdx < endSize)
              forciblyDisable = true;
            testIdx = configText.indexOf("1280", sidx);
            if (testIdx != -1 && testIdx < endSize)
              forciblyDisable = true;
            if (forciblyDisable)
            {
              if (sage.Sage.DBG) System.out.println("WARNING Detected high-definition VOBSUB subtitles in file which is playing on an extender; disable them since the hardware can't handle this! size=" + configText);
              return false;
            }
          }
        }
        byte alpha = (byte) 0xFF;
        if (aidx != -1)
        {
          int idx = configText.indexOf('%', aidx);
          if (idx != -1)
          {
            try
            {
              alpha = (byte)((Integer.parseInt(configText.substring(aidx + "alpha:".length() + 1, idx)) * 255 / 100) & 0xFF);
            }
            catch (Exception ex)
            {
              if (sage.Sage.DBG) System.out.println("Error parsing alpha entry in embedded VobSub of: " + ex);
            }
          }
        }
        int endPal = configText.indexOf('\n', pidx);
        if (endPal == -1)
        {
          if (sage.Sage.DBG) System.out.println("ERROR parsing Matroska DVDSUB header, can't find newline terminator after palette:");
          return false;
        }
        parsePaletteInfo(configText.substring(pidx, endPal), alpha);
        // Now find the end of the config data
        int endIdx = endPal;
        int cidx = configText.indexOf("custom", pidx);
        if (cidx != -1)
        {
          int endc = configText.indexOf('\n', cidx);
          if (endc == -1)
          {
            if (sage.Sage.DBG) System.out.println("ERROR couldn't find end of VobSob config block; ignoring data in this packet");
            return false;
          }
          endIdx = endc;
        }

        // We use this to tell when the init block is resent
        initPrefix = new byte[16];
        System.arraycopy(rawText, 0, initPrefix, 0, initPrefix.length);
        initPrefixLength = endIdx + 1;

        // Check if there's an extra null byte after the init block that we should include in the init size
        if (rawText.length > initPrefixLength + 4 && rawText[initPrefixLength] == 0)
        {
          // It's there, now check to make sure it's not supposed to be
          int size = (rawText[initPrefixLength + 1] & 0xFF); // upper 8 bits are zero
          int ptr = ((rawText[initPrefixLength + 2] & 0xFF) << 8) | (rawText[initPrefixLength + 3] & 0xFF);
          // It's invalid if the ptr is greater than the size
          if (ptr >= size)
          {
            initPrefixLength++;
          }
        }
        byte[] myData = new byte[rawText.length - initPrefixLength];
        System.arraycopy(rawText, initPrefixLength, myData, 0, myData.length);
        rawText = myData;
      }
      if (paletteData == null)
        return false; // not initialized, so don't log the entries since its probably bogus data
    }
    return insertSubtitleEntry(new SubtitleEntry(rawText, time, dur));
  }

  private boolean cleanupBitmapFile;
  private java.io.File bitmapFile;
  private SageDataFile frf;
  private boolean didInitPalette;
  private byte[] paletteData;
  private int flags;
  private byte[] initPrefix;
  private int initPrefixLength;
  private sage.VideoFrame vf;

  // This is to deal with a bug on the media extenders where they can't handle high-definition VOBSUB information
  private boolean forciblyDisable;
}
