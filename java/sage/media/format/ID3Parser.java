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
public class ID3Parser
{
  public static final String[] ID3V1_GENRES = { "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk",
    "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock",
    "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno",
    "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental",
    "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise", "AlternRock", "Bass", "Soul", "Punk",
    "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave",
    "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult",
    "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave",
    "Psychadelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka",
    "Retro", "Musical", "Rock & Roll", "Hard Rock", "Folk", "Folk-Rock", "National Folk", "Swing",
    "Fast Fusion", "Bebob", "Latin", "Revival", "Celtic", "Bluegrass", "Avantgarde", "Gothic Rock",
    "Progressive Rock", "Psychedelic Rock", "Symphonic Rock", "Slow Rock", "Big Band", "Chorus",
    "Easy Listening", "Acoustic", "Humour", "Speech", "Chanson", "Opera", "Chamber Music", "Sonata",
    "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam", "Club", "Tango", "Samba",
    "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet", "Punk Rock",
    "Drum Solo", "A capella", "Euro-House", "Dance Hall" };

  private ID3Parser()
  {
  }

  // This will extract any additional ID3V1/V2 metadata from the file and put it in the returned Map
  public static java.util.Map extractID3MetaData(java.io.File f)
  {
    try
    {
      if (f.length() > 128)
      {
        // First look for the ID3V2 tags
        java.util.HashMap rv = parseID3V2Tag(f);
        if (rv != null && !rv.isEmpty())
          return rv;
        rv = parseID3V1Tag(f);
        if (rv != null)
          return rv;
      }
      /*String parsePlugins = Sage.get("mediafile_metadata_parser_plugins", "");
			if (Sage.WINDOWS_OS)
				parsePlugins += ";sage.WMATagParser";
			java.util.StringTokenizer toker = new java.util.StringTokenizer(parsePlugins, ",;");
			while (toker.hasMoreTokens())
			{
				try
				{
					MediaFileMetadataParser parsie = (MediaFileMetadataParser) Class.forName(toker.nextToken()).newInstance();
					metaAir = (Airing)parsie.extractMetadata(theFile, namePrefix);
					if (metaAir != null)
					{
						infoAiringID = metaAir.getID();
						myAiring = metaAir;
						return metaAir;
					}
				}
				catch (Exception e1)
				{
					if (Sage.DBG) System.out.println("Error instantiating metadata parser plugin of:" + e1);
				}
			}
			return metaAir;*/
      return null;
    }
    catch (Exception e)
    {
      if (sage.Sage.DBG) System.out.println("Error parsing metadata in file " + f + " of:" + e);
      return null;
    }
  }

  private static long getMpegAudioBitrate(byte[] data, int offset)
  {
    // First synchronize on the syncword 0xFF F?
    while ((offset + 1 < data.length) && (((data[offset] & 0xFF) != 0xFF) || ((data[offset+1]&0xF0) != 0xF0)))
      offset++;
    if (((data[offset] & 0xFF) != 0xFF) || ((data[offset+1]&0xF0) != 0xF0) || (data.length - offset < 3))
    {
      //if (sage.Sage.DBG) System.out.println("Couldn't find MPEG audio syncword");
      return 0;
    }
    int level = ((data[offset+1]&0x08) == 0x08) ? 1 : 2;
    int layer = (data[offset+1]&0x06) >> 1;
    if (layer == 3)
      layer = 1;
    else if (layer != 2)
      layer = 3;
    int bitrateIndex = (data[offset+2]&0xF0) >> 4;
    // First index is the input
    // Second index is MPEG level and layer
    // (MPEG-1, layer 1; MPEG-1, layer 2; MPEG-1, layer3;
    //  MPEG-2, layer 1; MPEG-2, layer 2; MPEG-2, layer3)
    int [][]convert = {
        {   0,   0,   0,   0,   0,   0 },
        {  32,  32,  32,  32,  32,   8 },
        {  64,  48,  40,  64,  48,  16 },
        {  96,  56,  48,  96,  56,  24 },
        { 128,  64,  56, 128,  64,  32 },
        { 160,  80,  64, 160,  80,  64 },
        { 192,  96,  80, 192,  96,  80 },
        { 224, 112,  96, 224, 112,  56 },
        { 256, 128, 112, 256, 128,  64 },
        { 288, 160, 128, 288, 160, 128 },
        { 320, 192, 160, 320, 192, 160 },
        { 352, 224, 192, 352, 224, 112 },
        { 384, 256, 224, 384, 256, 128 },
        { 416, 320, 256, 416, 320, 256 },
        { 448, 384, 320, 448, 384, 320 },
        {   0,   0,   0,   0,   0,   0 }
    };

    int index2 = (level - 1) * 3 + layer - 1;

    if (bitrateIndex < convert.length && bitrateIndex >= 0 && index2 >= 0 && index2 < convert[bitrateIndex].length)
      return convert[bitrateIndex][index2];
    else
      return 0;
  }

  private static String cleanString(String s)
  {
    StringBuffer rv = new StringBuffer();
    for (int i = 0; i < s.length(); i++)
    {
      if (s.charAt(i) != 0)
        rv.append(s.charAt(i));
      else if (rv.length() != 0)
        break;
    }
    return rv.toString().trim();
  }

  public static java.util.HashMap parseID3V1Tag(java.io.File theFile) throws java.io.IOException
  {
    // Don't use FastRandomFile here because it allocates a big buffer to read which we don't need
    java.io.RandomAccessFile randy = new java.io.RandomAccessFile(theFile, "r");
    try
    {
      byte[] buf = new byte[128];
      randy.seek(randy.length() - 128);
      randy.readFully(buf);
      String tag = new String(buf, 0, 3).trim();
      if (!"TAG".equals(tag))
        return null;
      String textEncoding = sage.Sage.get("id3_default_text_encoding", "ISO-8859-1");
      String title = cleanString(new String(buf, 3, 30, textEncoding));
      if (title.length() == 0) return null;
      String artist = cleanString(new String(buf, 33, 30, textEncoding));
      String album = cleanString(new String(buf, 63, 30, textEncoding));
      //if (album.length() == 0) album = sage.Sage.rez("Music_Single");
      String year = cleanString(new String(buf, 93, 4, textEncoding));
      String comment = cleanString(new String(buf, 97, 28, textEncoding));
      int track = (buf[125] & 0xFF);
      int genre = (buf[127] & 0xFF);
      String category = "";
      if (genre >= 0 && genre < ID3V1_GENRES.length)
        category = ID3V1_GENRES[genre];
      if (track == 0 && comment.startsWith("Track "))
      {
        try
        {
          track = Integer.parseInt(comment.substring(6));
        }catch (Exception e2){}
      }
      if (sage.Sage.DBG) System.out.println("ID3V1tag=" + tag + " title=" + title + " artist=" + artist + " album=" + album + " year=" + year +
          " comment=" + comment + " track=" + track + " genre=" + category);
      randy.seek(0);
      randy.readFully(buf);
      // Try the updated duration technique
      /*			long theDur = sage.MP3Utils.getMp3Duration(theFile);
			if (theDur == 0)
			{
				long mpegAudioBitrate = getMpegAudioBitrate(buf, 0);
				if (mpegAudioBitrate > 0)
				{
					theDur = 8*(randy.length() - 128)/mpegAudioBitrate;
					if (sage.Sage.DBG) System.out.println("Mpegaudiobitrate=" + mpegAudioBitrate + " theDur=" + theDur);
				}
			}*/
      java.util.HashMap rv = new java.util.HashMap();
      rv.put(MediaFormat.META_TITLE, title);
      rv.put(MediaFormat.META_ALBUM, album);
      rv.put(MediaFormat.META_YEAR, year);
      rv.put(MediaFormat.META_COMMENT, comment);
      rv.put(MediaFormat.META_ARTIST, artist);
      rv.put(MediaFormat.META_GENRE, category);
      if (track != 0)
        rv.put(MediaFormat.META_TRACK, Integer.toString(track));
      //			if (theDur > 0)
      //				rv.put(MediaFormat.META_DURATION, Long.toString(theDur));
      return rv;
    }
    finally
    {
      randy.close();
    }
    /*if (theDur == 0)
			theDur = VideoFrame.getFileDuration(theFile.toString());
		if (theDur == 0)
			theDur = 1;*/
    /*if (!isMusicFile())
		{
			title = namePrefix + title;
			String temp = title;
			title = album;
			album = temp;
		}*/
    //		if (artist.length() > 0)
    //			myAiring = Wizard.getInstance().addAiring(Wizard.getInstance().addShow(album, null, title,
    //				"", 0, category, null, new String[] { artist }, new byte[] { (mediaType == MEDIATYPE_AUDIO) ? Show.ARTIST_ROLE : Show.ACTOR_ROLE }, null, null,
    //				year, null, null, "MP3" + Integer.toString(id), "English", 0), 0/*1000000 + id*/,
    //				theFile.lastModified() - theDur, theDur, (byte)track, (byte)0, (byte)0);
    //		else
    //			myAiring = Wizard.getInstance().addAiring(Wizard.getInstance().addShow(album, null, title,
    //				"", 0, category, null, new String[0], new byte[0], null, null,
    //				year, null, null, "MP3" + Integer.toString(id), "English", 0), 0/*1000000 + id*/,
    //				theFile.lastModified() - theDur, theDur, (byte)track, (byte)0, (byte)0);
    //		infoAiringID = myAiring.id;
    //		return myAiring;
  }

  private static int getSyncSafeInt(byte[] buf, int offset, boolean unsync)
  {
    if (unsync)
    {
      if (buf[offset] < 0 || buf[offset+1] < 0 || buf[offset+2] < 0 || buf[offset+3] < 0) return -1; // unsynched size bytes
      return (((int)buf[offset]) << 21) + (((int)buf[offset+1]) << 14) + (((int)buf[offset+2]) << 7) + buf[offset+3];
    }
    else
    {
      return ((buf[offset] & 0xFF) << 24) + ((buf[offset+1] & 0xFF) << 16) + ((buf[offset+2] & 0xFF) << 8) + (buf[offset+3] & 0xFF);
    }
  }

  public static java.util.HashMap parseID3V2Tag(java.io.File theFile) throws java.io.IOException
  {
    if (theFile.length() < 128) return null;
    // Don't use FastRandomFile here because it allocates a big buffer to read which we don't need
    java.io.RandomAccessFile randy = new java.io.RandomAccessFile(theFile, "r");
    try
    {
      byte[] buf = new byte[128];
      randy.readFully(buf);
      String tag = new String(buf, 0, 3).trim();
      if (!"ID3".equals(tag))
        return null;
      //if (Sage.DBG) System.out.println("ID3V2." + buf[3] + "." + buf[4] + " tag detected");
      short version = buf[3];
      if ((buf[3] & 0xFF) == 0xFF) return null; // incompatible version
      boolean unsync = (buf[5] & 0x80) == 0x80;
      boolean extHdr = (buf[5] & 0x40) == 0x40;
      boolean experimental = (buf[5] & 0x20) == 0x20;
      boolean footerPresent = (buf[5] & 0x10) == 0x10;
      if ((buf[5] & 0x0F) != 0) return null; // unknown flags
      int tagSize = getSyncSafeInt(buf, 6, true);
      if (tagSize < 0) return null;
      int fullHeaderSize = tagSize + 10 + (footerPresent ? 10 : 0);
      buf = new byte[fullHeaderSize];
      randy.seek(0);
      randy.readFully(buf);
      //if (Sage.DBG) System.out.println("unsync=" + unsync + " extHdr=" + extHdr + " footer=" + footerPresent + " size=" + tagSize);
      int offset = 10;
      if (extHdr)
      {
        int extHdrSize = getSyncSafeInt(buf, offset, version >= 4 /*V2.4 added unsync here*/);
        if (extHdrSize < 0) return null;
        int numFlagBytes = buf[14];
        offset += 5;
        //if (Sage.DBG) System.out.println("extHdrSize=" + extHdrSize + " numFlagBytes=" + numFlagBytes);
        if (numFlagBytes != 1) return null;
        boolean tagUpdate = (buf[offset] & 0x40) == 0x40;
        boolean crcPresent = (buf[offset] & 0x20) == 0x20;
        boolean tagRestrictions = (buf[offset] & 0x10) == 0x10;
        if (tagUpdate)
        {
          int extTagLength = buf[offset] + 1;
          offset += 1 + extTagLength;
        }
        if (crcPresent)
        {
          int extTagLength = buf[offset] + 1;
          offset += 1 + extTagLength;
        }
        if (tagRestrictions)
        {
          int extTagLength = buf[offset] + 1;
          offset += 1 + extTagLength;
        }
        //if (Sage.DBG) System.out.println("tagUpdate=" + tagUpdate + " crc=" + crcPresent + " tagRes=" + tagRestrictions);
      }
      java.util.HashMap rv = new java.util.HashMap();
      long duration = 0;
      frame_processing:
        while (offset < tagSize) // +10 for the initial header and -10 for the next frame header
        {
          String frameCode = new String(buf, offset, (version <= 2) ? 3 : 4);
          for (int i = 0; i < 3; i++)
          {
            char c = frameCode.charAt(i);
            if ((c < '0' || c > '9') && (c < 'A' || c > 'Z'))
              break frame_processing;
          }
          int frameSize;
          if (version <= 2)
          {
            frameSize = ((buf[offset + 3] & 0xFF) << 16) | ((buf[offset + 4] & 0xFF) << 8) | (buf[offset + 5] & 0xFF);
          }
          else
            frameSize = getSyncSafeInt(buf, offset + 4, unsync || version >= 4); // V2.4 made all frame sizes synched ints
          //if (Sage.DBG) System.out.println("FrameCode=" + frameCode + " frameSize=" + frameSize);
          if (frameSize == -1) break;
          if (version <= 2)
            offset += 6;
          else
          {
            int frameFlag1 = buf[offset+8]&0xFF;
            int frameFlag2 = buf[offset+9]&0xFF;
            if ((frameFlag2 & 0x4F) != 0)
            {
              //if (Sage.DBG) System.out.println("Unknown frame format flags");
              offset += frameSize;
              break;
            }
            boolean groupIdentity = (frameFlag2 & 0x40) == 0x40;
            boolean compressed = (frameFlag2 & 0x08) == 0x08;
            boolean encryption = (frameFlag2 & 0x04) == 0x04;
            boolean unsynchronization = (frameFlag2 & 0x02) == 0x02;
            boolean dataLengthIndicator = (frameFlag2 & 0x01) == 0x01;
            offset += 10;
          }

          String textEncoding;
          boolean doubleByteEncoding = false;
          if (buf[offset] == 3)
            textEncoding = "UTF-8";
          else if (buf[offset] == 2)
          {
            doubleByteEncoding = true;
            // We don't need to specify big endian since the byte order mark (BOM) will be there
            textEncoding = "UTF-16";
          }
          else if (buf[offset] == 1)
          {
            doubleByteEncoding = true;
            textEncoding = "UTF-16";
          }
          else
            textEncoding = sage.Sage.get("id3_default_text_encoding", "ISO-8859-1");
          // Skip one for the character encoding byte
          if (frameCode.equals("TLEN"))
          {
            String currStr = cleanString(new String(buf, offset + 1, frameSize - 1, textEncoding));
            //if (Sage.DBG) System.out.println("TLEN(length)=" + currStr);
            try
            {
              duration = Long.parseLong(currStr);
              rv.put(MediaFormat.META_DURATION, Long.toString(duration));
            }
            catch(Exception e){}
          }
          else if (frameCode.equals("TIT2") || frameCode.equals("TT2"))
          {
            rv.put(MediaFormat.META_TITLE, cleanString(new String(buf, offset + 1, frameSize - 1, textEncoding)));
            //if (Sage.DBG) System.out.println("TIT2(title)=" + title);
          }
          else if (frameCode.equals("TCOM") || frameCode.equals("TCM"))
          {
            rv.put(MediaFormat.META_COMPOSER, cleanString(new String(buf, offset + 1, frameSize - 1, textEncoding)));
          }
          else if (frameCode.equals("TRCK") || frameCode.equals("TRK"))
          {
            String currStr = cleanString(new String(buf, offset + 1, frameSize - 1, textEncoding));
            //if (Sage.DBG) System.out.println("TRCK(track#)=" + currStr);
            rv.put(MediaFormat.META_TRACK, currStr);
          }
          else if (frameCode.equals("TPE1") || frameCode.equals("TP1"))
          {
            rv.put(MediaFormat.META_ARTIST, cleanString(new String(buf, offset + 1, frameSize - 1, textEncoding)));
            //if (Sage.DBG) System.out.println("TPE1(artist)=" + artist);
          }
          else if (frameCode.equals("TPE2") || frameCode.equals("TP2"))
          {
            rv.put(MediaFormat.META_ALBUM_ARTIST, cleanString(new String(buf, offset + 1, frameSize - 1, textEncoding)));
          }
          else if (frameCode.equals("TCON") || frameCode.equals("TCO"))
          {
            String category = cleanString(new String(buf, offset + 1, frameSize - 1, textEncoding));
            //if (Sage.DBG) System.out.println("TCON(content type)=" + category);
            // Check for a number in parethesis for the category. If this is there with no other text,
            // then we use that to look up the genre in the ID3V1 array. Otherwise if it's there with text
            // we just strip off the number and the parenthesis
            int paren1 = category.indexOf('(');
            if (paren1 != -1)
            {
              int paren2 = category.indexOf(')');
              if (paren2 > paren1)
              {
                String numText = category.substring(paren1 + 1, paren2);
                try
                {
                  int catNum = Integer.parseInt(numText);
                  if (sage.Sage.DBG) System.out.println("Converting numeric category of " + catNum);
                  String newCategory = (category.substring(0, paren1) + category.substring(paren2 + 1)).trim();
                  if (newCategory.length() == 0 && catNum >= 0 && catNum < ID3V1_GENRES.length)
                  {
                    category = ID3V1_GENRES[catNum];
                  }
                  else
                    category = newCategory;
                }
                catch (NumberFormatException e)
                {
                  if (sage.Sage.DBG) System.out.println("Error extracing numeric category:" + e);
                }
              }
            }
            else
            {
              // Check for a straight numeric category
              try
              {
                int catNum = Integer.parseInt(category);
                if (catNum >= 0 && catNum < ID3V1_GENRES.length)
                  category = ID3V1_GENRES[catNum];
              }
              catch (NumberFormatException e)
              {
                // This can happen normally if the category is non-numeric, which is allowed
              }
            }
            rv.put(MediaFormat.META_GENRE, category);
          }
          else if (frameCode.equals("TALB") || frameCode.equals("TAL"))
          {
            rv.put(MediaFormat.META_ALBUM, cleanString(new String(buf, offset + 1, frameSize - 1, textEncoding)));
            //if (Sage.DBG) System.out.println("TALB(album)=" + album);
          }
          else if (frameCode.equals("TYER") || frameCode.equals("TORY") || frameCode.equals("TYE") || frameCode.equals("TOR") ||
              frameCode.equals("TDAT") || frameCode.equals("TDRC") || frameCode.equals("TIME") || frameCode.equals("TRDA"))
          {
            String yearStr = cleanString(new String(buf, offset + 1, frameSize - 1, textEncoding));
            if (yearStr != null && (yearStr.length() == 4 || (yearStr.length() > 4 && yearStr.charAt(4) == '-')))
            {
              if (yearStr.length() > 4)
                yearStr = yearStr.substring(0, 4);
              rv.put(MediaFormat.META_YEAR, yearStr);
            }
            //if (Sage.DBG) System.out.println(frameCode + "(year)=" + year);
          }
          else if (frameCode.equals("APIC") || frameCode.equals("PIC"))
          {
            String mimeType;
            if (version <= 2)
              mimeType = "XX";
            else
              mimeType = cleanString(new String(buf, offset + 1, 128, textEncoding));
            byte pictureType = buf[offset + 2 + mimeType.length()];
            String pictureDesc;
            if (buf[offset + 3 + mimeType.length()] == 0)
              pictureDesc = "";
            else
              pictureDesc = cleanString(new String(buf, offset + 3 + mimeType.length(), 65, textEncoding));
            if (pictureDesc.length() > 0)
              rv.put(MediaFormat.META_THUMBNAIL_DESC, pictureDesc);
            int pictureOffset = offset + 3 + mimeType.length();
            if (doubleByteEncoding)
              pictureOffset += pictureDesc.length()*2 + 2;
            else
              pictureOffset += pictureDesc.length() + 1;
            int pictureLength = frameSize - (pictureOffset - offset);
            //thumbnailFile = theFile;
            //thumbnailOffset = pictureOffset;
            //thumbnailSize = pictureLength;
            // 10 is just an arbitrary, number. Basically something that's not just empty.
            if (pictureLength > 10)
            {
              rv.put(MediaFormat.META_THUMBNAIL_SIZE, Integer.toString(pictureLength));
              rv.put(MediaFormat.META_THUMBNAIL_OFFSET, Integer.toString(pictureOffset));
            }
            //if (Sage.DBG) System.out.println("Picture unicode="+unicodeText + " mime=" + mimeType + " picType=" + pictureType +
            //	" desc=" + pictureDesc + " offset=" + pictureOffset + " length=" + pictureLength);
          }
          offset += frameSize;
        }

      // Calculate the actual duration
      /*			randy.readFully(buf);
			duration = sage.MP3Utils.getMp3Duration(theFile);
			if (duration == 0)
			{
				long mpegAudioBitrate = getMpegAudioBitrate(buf, 0);
				if (mpegAudioBitrate > 0)
				{
					duration = 8*(randy.length() - fullHeaderSize)/mpegAudioBitrate;
					if (sage.Sage.DBG) System.out.println("Mpegaudiobitrate=" + mpegAudioBitrate + " duration=" + duration);
				}
			}
			if (duration != 0)
				rv.put(MediaFormat.META_DURATION, Long.toString(duration));*/
      /*if (duration == 0)
				duration = VideoFrame.getFileDuration(theFile.toString());
			if (duration == 0)
				duration = 1;
			if (title != null)
			{
				if (album == null)
					album = Sage.rez("Music_Single");
				if (artist != null && artist.length() == 0)
					artist = null;
				if (mediaType != MEDIATYPE_AUDIO)
				{
					title = namePrefix + title;
					String temp = album;
					album = title;
					title = temp;
				}
				if (artist != null)
					myAiring = Wizard.getInstance().addAiring(Wizard.getInstance().addShow(album, null, title,
						"", 0, category, null, new String[] { artist }, new byte[] { (mediaType == MEDIATYPE_AUDIO) ? Show.ARTIST_ROLE : Show.ACTOR_ROLE }, null, null,
       *///						year, null, null, "MP3" + Integer.toString(id), "English", 0), 0/*1000000 + id*/,
      //						theFile.lastModified() - duration, duration, (byte)track, (byte)0, (byte)0);
      //				else
      //					myAiring = Wizard.getInstance().addAiring(Wizard.getInstance().addShow(album, null, title,
      //						"", 0, category, null, new String[0] , new byte[0], null, null,
      //						year, null, null, "MP3" + Integer.toString(id), "English", 0), 0/*1000000 + id*/,
      //						theFile.lastModified() - duration, duration, (byte)track, (byte)0, (byte)0);
      //				infoAiringID = myAiring.id;
      //				return myAiring;
      //			}
      //			return null;
      return rv;
    }
    finally
    {
      randy.close();
    }
  }

}
