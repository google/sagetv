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
// Initial plan was to use dsm-cc but it's not clear how to pack into mpeg2 program stream
// and it was more complex to implement instead we will use padding stream with
// META, data, crc32
// 00 00 01 BE LENHI LENLO 'M' 'E' 'T' 'A' data crc32
package sage.media.format;

public class MpegMetadata
{
  public static boolean addMediaFileMetadata(sage.MediaFile mf)
  {
    int type;
    ContainerFormat format = mf.getFileFormat();
    if (format != null)
    {
      if (MediaFormat.MPEG2_TS.equals(format.getFormatName()))
      {
        if (format.getPacketSize() == 192)
          type = 2;
        else
          type = 1;
      }
      else if (MediaFormat.MPEG2_PS.equals(format.getFormatName()))
        type = 0;
      else
        return false;
    }
    else
      return false;
    // First create the meta string which is:
    // Name=Value;Name=Value;
    // Any special characters can be escaped using a backslash; and backslashes must also be escaped.
    // ContainerFormat has methods for handling this automatically
    if (mf == null) return false;
    StringBuffer rv = new StringBuffer();
    sage.Airing air = mf.getContentAiring();
    if (air == null) return false;
    sage.Show s = air.getShow();
    if (s == null) return false;
    rv.append(MediaFormat.META_AIRING_DURATION);
    rv.append('=');
    rv.append(Long.toString(air.getDuration()));
    rv.append(';');
    rv.append(MediaFormat.META_AIRING_TIME);
    rv.append('=');
    rv.append(Long.toString(air.getStartTime()));
    rv.append(';');

    if (air.getTotalParts() > 1)
    {
      rv.append(MediaFormat.META_PART_NUMBER);
      rv.append('=');
      rv.append(Integer.toString(air.getPartNum()));
      rv.append(';');

      rv.append(MediaFormat.META_TOTAL_PARTS);
      rv.append('=');
      rv.append(Integer.toString(air.getTotalParts()));
      rv.append(';');
    }

    if (air.isHDTV()) rv.append(MediaFormat.META_HDTV).append("=true;");
    if (air.isCC()) rv.append(MediaFormat.META_CC).append("=true;");
    if (air.isStereo()) rv.append(MediaFormat.META_STEREO).append("=true;");
    if (air.isSubtitled()) rv.append(MediaFormat.META_SUBTITLED).append("=true;");
    if (air.isPremiere()) rv.append(MediaFormat.META_PREMIERE).append("=true;");
    if (air.isSeasonPremiere()) rv.append(MediaFormat.META_SEASON_PREMIERE).append("=true;");
    if (air.isSeriesPremiere()) rv.append(MediaFormat.META_SERIES_PREMIERE).append("=true;");
    if (air.isChannelPremiere()) rv.append(MediaFormat.META_CHANNEL_PREMIERE).append("=true;");
    if (air.isSeasonFinale()) rv.append(MediaFormat.META_SEASON_FINALE).append("=true;");
    if (air.isSeriesFinale()) rv.append(MediaFormat.META_SERIES_FINALE).append("=true;");
    if (air.isSAP()) rv.append(MediaFormat.META_SAP).append("=true;");
    if (air.is3D()) rv.append(MediaFormat.META_3D).append("=true;");
    if (air.isDD51()) rv.append(MediaFormat.META_DD51).append("=true;");
    if (air.isDolby()) rv.append(MediaFormat.META_DOLBY).append("=true;");
    if (air.isLetterbox()) rv.append(MediaFormat.META_LETTERBOX).append("=true;");
    if (air.isLive()) rv.append(MediaFormat.META_LIVE).append("=true;");
    if (air.isNew()) rv.append(MediaFormat.META_NEW).append("=true;");
    if (air.isWidescreen()) rv.append(MediaFormat.META_WIDESCREEN).append("=true;");
    if (air.isSurround()) rv.append(MediaFormat.META_SURROUND).append("=true;");
    if (air.isDubbed()) rv.append(MediaFormat.META_DUBBED).append("=true;");
    if (air.isTaped()) rv.append(MediaFormat.META_TAPED).append("=true;");

    if (air.getParentalRating().length() > 0)
    {
      rv.append(MediaFormat.META_PARENTAL_RATING);
      rv.append('=');
      rv.append(ContainerFormat.escapeString(air.getParentalRating()));
      rv.append(';');
    }

    if (s.getDuration() > 0)
    {
      rv.append(MediaFormat.META_RUNNING_TIME);
      rv.append('=');
      rv.append(Long.toString(s.getDuration()));
      rv.append(';');
    }

    rv.append(MediaFormat.META_TITLE);
    rv.append('=');
    rv.append(ContainerFormat.escapeString(s.getTitle()));
    rv.append(';');

    String tmp = s.getEpisodeName();
    if (tmp.length() > 0)
    {
      rv.append(MediaFormat.META_EPISODE_NAME);
      rv.append('=');
      rv.append(ContainerFormat.escapeString(tmp));
      rv.append(';');
    }

    tmp = s.getDesc();
    if (tmp.length() > 0)
    {
      rv.append(MediaFormat.META_DESCRIPTION);
      rv.append('=');
      rv.append(ContainerFormat.escapeString(tmp));
      rv.append(';');
    }

    String[] showCats = s.getCategories();
    if (showCats.length > 0)
    {
      rv.append(MediaFormat.META_GENRE);
      rv.append('=');
      for (int i = 0; i < showCats.length; i++)
      {
        if (i > 0)
        {
          // Don't use the forward slash delimiter because some category names use it internally
          rv.append('\\');
          rv.append(';');
        }
        rv.append(ContainerFormat.escapeString(showCats[i]));
      }
      rv.append(';');
    }

    tmp = s.getRated();
    if (tmp.length() > 0)
    {
      rv.append(MediaFormat.META_RATED);
      rv.append('=');
      rv.append(ContainerFormat.escapeString(tmp));
      rv.append(';');
    }

    String[] ers = s.getExpandedRatings();
    if (ers != null && ers.length > 0)
    {
      rv.append(MediaFormat.META_EXTENDED_RATINGS);
      rv.append('=');
      for (int i = 0; i < ers.length; i++)
      {
        rv.append(ContainerFormat.escapeString(ers[i]));
        if (i < ers.length - 1)
          rv.append(',');
      }
      rv.append(';');
    }

    tmp = s.getYear();
    if (tmp.length() > 0)
    {
      rv.append(MediaFormat.META_YEAR);
      rv.append('=');
      rv.append(ContainerFormat.escapeString(tmp));
      rv.append(';');
    }

    tmp = s.getLanguage();
    if (tmp.length() > 0)
    {
      rv.append(MediaFormat.META_LANGUAGE);
      rv.append('=');
      rv.append(ContainerFormat.escapeString(tmp));
      rv.append(';');
    }

    if (s.getOriginalAirDate() > 0)
    {
      rv.append(MediaFormat.META_ORIGINAL_AIR_DATE);
      rv.append('=');
      rv.append(Long.toString(s.getOriginalAirDate()));
      rv.append(';');
    }

    if (s.getSeasonNumber() > 0)
    {
      rv.append(MediaFormat.META_SEASON_NUMBER);
      rv.append('=');
      rv.append(Integer.toString(s.getSeasonNumber()));
      rv.append(';');
    }
    if (s.getEpisodeNumber() > 0)
    {
      rv.append(MediaFormat.META_EPISODE_NUMBER);
      rv.append('=');
      rv.append(Integer.toString(s.getEpisodeNumber()));
      rv.append(';');
    }
    if (s.getAltEpisodeNumber() > 0)
    {
      rv.append(MediaFormat.META_ALT_EPISODE_NUMBER);
      rv.append('=');
      rv.append(Integer.toString(s.getAltEpisodeNumber()));
      rv.append(';');
    }
    if (s.isForcedUnique())
    {
      rv.append(MediaFormat.META_FORCED_UNIQUE);
      rv.append("=true;");
    }
    if (s.getSeriesID() != 0)
    {
      rv.append(MediaFormat.META_SERIES_ID);
      rv.append('=');
      rv.append(Integer.toString(s.getSeriesID()));
      rv.append(';');
    }
    if (s.getShowcardID() != 0)
    {
      rv.append(MediaFormat.META_SHOWCARD_ID);
      rv.append('=');
      rv.append(Integer.toString(s.getShowcardID()));
      rv.append(';');
    }
    String imageStr = s.getImageMetaStorageString();
    if (imageStr != null)
    {
      rv.append(MediaFormat.META_CORE_IMAGERY2);
      rv.append('=');
      rv.append(imageStr);
      rv.append(';');
    }

    rv.append(MediaFormat.META_EXTERNAL_ID);
    rv.append('=');
    rv.append(s.getExternalID());
    rv.append(';');

    String[] bonuses = s.getBonuses();
    if (bonuses != null && bonuses.length > 0)
    {
      rv.append(MediaFormat.META_MISC);
      rv.append('=');
      for (int i = 0; i < bonuses.length; i++)
      {
        rv.append(ContainerFormat.escapeString(bonuses[i]));
        if (i < bonuses.length - 1)
        {
          rv.append('\\');
          rv.append(';');
        }
      }
      rv.append(';');
    }

    for (int i = 1; i <= sage.Show.MAX_ROLE_NUM; i++)
    {
      String[] peeps = s.getPeopleList(i);
      if (peeps != null && peeps.length > 0)
      {
        rv.append(ContainerFormat.escapeString(sage.Show.ROLE_NAMES[i]));
        rv.append('=');
        for (int j = 0; j < peeps.length; j++)
        {
          rv.append(ContainerFormat.escapeString(peeps[j]));
          if (j < peeps.length - 1)
          {
            rv.append('\\');
            rv.append(';');
          }
        }
        rv.append(';');
      }
    }
    try
    {
      addFileMetadata(mf.getFile(0), type, rv.toString(), mf.getRecordEnd());
    }
    catch (java.io.IOException e)
    {
      if (sage.Sage.DBG) System.out.println("ERROR Failed writing metadata into mpeg file " + mf.getFile(0) + " of " + e);
      return false;
    }
    return true;
  }

  public static boolean doesFileHaveMetadata(java.io.File f, ContainerFormat format)
  {
    int type = 0;
    if (format != null)
    {
      if (MediaFormat.MPEG2_TS.equals(format.getFormatName()))
      {
        if (format.getPacketSize() == 192)
          type = 2;
        else
          type = 1;
      }
    }
    try
    {
      String str = getFileMetadata(f, type);
      return str != null && str.length() > 0;
    }
    catch (java.io.EOFException eofe)
    {} // these can happen if no metadata is found, so suppress them
    catch (java.io.IOException e)
    {
      if (sage.Sage.DBG) System.out.println("ERROR with MPEG metadata extraction from " + f + " of:" + e);
    }
    return false;

  }

  public static java.util.Map getFileMetadataAsMap(java.io.File f, ContainerFormat format)
  {
    int type = 0;
    if (format != null)
    {
      if (MediaFormat.MPEG2_TS.equals(format.getFormatName()))
      {
        if (format.getPacketSize() == 192)
          type = 2;
        else
          type = 1;
      }
    }
    java.util.Map rv = null;
    try
    {
      String str = getFileMetadata(f, type);
      if (str != null && str.length() > 0)
      {
        rv = new java.util.HashMap();
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
            String name = ContainerFormat.unescapeString(str.substring(currNameStart, currValueStart - 1));
            String value = ContainerFormat.unescapeString(str.substring(currValueStart, i));
            currNameStart = i + 1;
            currValueStart = -1;
            rv.put(name, value);
          }
        }
      }
    }
    catch (java.io.EOFException eofe)
    {} // these can happen if no metadata is found, so suppress them
    catch (java.io.IOException e)
    {
      if (sage.Sage.DBG) System.out.println("ERROR with MPEG metadata extraction from " + f + " of:" + e);
    }
    return rv;
  }

  // 0: Mpeg2-PS
  // 1: Mpeg2-TS
  // 2: Mpeg2-TS with 4 bytes extra header (.m2ts)

  public static void addFileMetadata(java.io.File f, int type, String metadataStr, long targetFileTime) throws java.io.IOException
  {
    int i, j, packetcount=0;
    int pos=0;
    // PRESERVE TIMESTAMP ON THE FILE!!!
    long fileTime = f.lastModified();
    if (targetFileTime != 0 && Math.abs(targetFileTime - fileTime) > 30000) {
      // There's an issue on Windows where the file closing is done asynchronously sometimes and may not have occurred by the time
      // we get here so the file modification time may not have been updated yet. So ensure we don't go setting the modification time
      // to the creation time if we received a suggestion that is more than 30 seconds off from the modification time.
      fileTime = targetFileTime;
    }
    sage.io.SageDataFile ra = new sage.io.SageDataFile(new sage.io.BufferedSageFile(new sage.io.LocalSageFile(f, false)), sage.Sage.BYTE_CHARSET);
    try
    {
      // TODO: Verify which value that uses...
      java.util.zip.CRC32 crc = new java.util.zip.CRC32();

      byte[] header = new byte[10];
      byte[] metadata = metadataStr.getBytes(sage.Sage.I18N_CHARSET);
      byte[] checksum = new byte[4];
      if(metadata.length>64000) throw new java.io.IOException("message too long");
      header[0]=0x00; header[1]=0x00; header[2]=0x01; header[3]=(byte) 0xBE;
      header[4]=(byte) (((metadata.length+8)>>8)&0xFF);
      header[5]=(byte) (((metadata.length+8))&0xFF);
      header[6]=0x4D; header[7]=0x45; header[8]=0x54; header[9]=0x41;

      crc.update(metadata);
      checksum[0]=(byte) ((crc.getValue()>>24)&0xFF);
      checksum[1]=(byte) ((crc.getValue()>>16)&0xFF);
      checksum[2]=(byte) ((crc.getValue()>>8)&0xFF);
      checksum[3]=(byte) ((crc.getValue()>>0)&0xFF);

      ra.seek(ra.length());
      switch(type)
      {
        case 0:
          // Check for a program end code and then move that after the padding we're inserting
          ra.seek(ra.length() - 4);
          int b1 = ra.read();
          int b2 = ra.read();
          int b3 = ra.read();
          int b4 = ra.read();
          boolean fixEndCode = b1 == 0 && b2 == 0 && b3 == 1 && b4 == 0xB9;
          if (fixEndCode)
            ra.seek(ra.length() - 4);
          ra.write(header);
          ra.write(metadata);
          ra.write(checksum);
          if (fixEndCode)
          {
            ra.write(0);ra.write(0);ra.write(1);ra.write(0xB9);
          }
          break;
        case 1:
        case 2:
          for(i=0;i<16;i++) // Add some padding in case we didn't align right
          {
            if(type==2)
            {
              ra.writeByte(0x0); ra.writeByte(0x0); ra.writeByte(0x0); ra.writeByte(0x0);
            }
            ra.writeByte(0x47);
            ra.writeByte(0x1F);
            ra.writeByte(0xFF);
            ra.writeByte(0x10|(packetcount&0xF));
            packetcount+=1;
            for(j=0;j<184;j++)
            {
              ra.writeByte(0xFF);
            }
          }
          while(pos<(metadata.length+10+4))
          {
            if(type==2)
            {
              ra.writeByte(0x0); ra.writeByte(0x0); ra.writeByte(0x0); ra.writeByte(0x0);
            }
            ra.writeByte(0x47);
            ra.writeByte(0x1F|( (pos==0) ? 0x40 : 0x00));
            ra.writeByte(0xFF);
            ra.writeByte(0x10|(packetcount&0xF));
            packetcount+=1;
            for(j=0;j<184;j++)
            {
              if(pos<10)
              {
                ra.writeByte(header[pos]);
              }
              else if(pos>=10 && (pos-10)<metadata.length)
              {
                ra.writeByte(metadata[pos-10]);
              }
              else if(pos>=(10+metadata.length) && (pos-10-metadata.length)<4)
              {
                ra.writeByte(checksum[pos-10-metadata.length]);
              }
              else
              {
                ra.writeByte(0xFF);
              }
              pos+=1;
            }
          }
          break;
      }
    }
    finally
    {
      if (ra != null)
        ra.close();
      f.setLastModified(fileTime);
    }
  }

  private static boolean verifyMeta(byte []data, int len)
  {
    if(len<8)
      return false;
    // Verify header
    if(data[0]!=0x4D ||
        data[1]!=0x45 ||
        data[2]!=0x54 ||
        data[3]!=0x41)
      return false;
    // verify CRC
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(data,4,len-8);

    if((data[len-4]&0xFF)!=((crc.getValue()>>24)&0xFF) ||
        (data[len-3]&0xFF)!=((crc.getValue()>>16)&0xFF) ||
        (data[len-2]&0xFF)!=((crc.getValue()>>8)&0xFF) ||
        (data[len-1]&0xFF)!=((crc.getValue()>>0)&0xFF))
      return false;
    return true;
  }

  // We assume it is in the last 128K of the file
  public static String getFileMetadata(java.io.File f, int type) throws java.io.IOException
  {
    String metadataStr = null;
    sage.io.SageFileSource ra = new sage.io.BufferedSageFile(new sage.io.LocalSageFile(f, true));
    try
    {
      // Get the file length now in case its growing
      long orgFileLength = ra.length();
      if(orgFileLength>128*1024)
      {
        ra.seek(orgFileLength-128*1024);
      }
      else
      {
        ra.seek(0);
      }
      if(type==0)
      {
        int cur=0xFFFFFFFF;
        int b=0;
        while((b=ra.read())!=-1) // detect EOF
        {
          if (ra.position() >= orgFileLength)
            return metadataStr;
          cur<<=8;
          cur|=b;
          if((cur&0xFFFFFF00)==0x00000100)
          {
            // Could be optimized by reading all types and skipping bytes...
            if(b==0xBE)
            {
              int lenhi,lenlo,len;
              lenhi=ra.read();
              lenlo=ra.read();
              if(lenhi==-1 || lenlo==-1) //EOF
              {
                return metadataStr;
              }
              len=(lenhi<<8)|lenlo;
              if((ra.length()-ra.position())>=len)
              {
                byte []data=new byte[len];
                ra.readFully(data);
                if(verifyMeta(data, len))
                {
                  return new String(data, 4, len-8, sage.Sage.I18N_CHARSET);
                }
                else
                {
                  cur=0xFFFFFFFF;
                  continue;
                }
              }
              else
              {
                return metadataStr;
              }
            }
          }
        }
      }
      else
      {
        int cur=0xFFFFFFFF;
        byte[] tsbuf = new byte[188];
        boolean insync=false;
        byte [] msgbuf = new byte[65536+188];
        boolean foundmeta=false;
        int metalen=0, metapos=0;
        // 1 sync to TS packets
        // 2 find packet on pid 0x1FFF which has start flag
        // 3 look inside for our magic start
        // 4 get 0x1FFF packets until length (we assume no lost or dup)
        int b=0;
        while((b=ra.read())!=-1) // detect EOF
        {
          if (ra.position() >= orgFileLength)
            return metadataStr;
          if(b==0x47) // Sync byte
          {
            if(insync==false)
            {
              // Verify next packet is where it should be
              long pos=ra.position();
              ra.seek(ra.position()+((type==2) ? 191 : 187));
              if(ra.read()==0x47)
              {
                insync=true;
                ra.seek(pos);
              }
              else
              {
                ra.seek(pos);
                continue;
              }
            }
            ra.readFully(tsbuf,1, 187);
            // verify if it is the right pid
            if((tsbuf[1]&0x1F)==0x1F &&
                (tsbuf[2]&0xFF)==0xFF)
            {
              if(!foundmeta)
              {
                if((tsbuf[1]&0x40)==0x40 &&
                    (tsbuf[3]&0xF0)==0x10 &&
                    (tsbuf[4]&0xFF)==0x00 &&
                    (tsbuf[5]&0xFF)==0x00 &&
                    (tsbuf[6]&0xFF)==0x01 &&
                    (tsbuf[7]&0xFF)==0xBE)
                {
                  foundmeta=true;
                  metalen=((tsbuf[8]&0xFF)<<8)|
                      tsbuf[9]&0xFF;
                  metapos=188-10;
                  System.arraycopy(tsbuf, 10, msgbuf, 0, 188-10);
                }
              }
              else
              {
                if(metapos<metalen)
                {
                  System.arraycopy(tsbuf, 4, msgbuf, metapos, 184);
                  metapos+=184;
                }
              }
              // append to current metadata
              if(foundmeta)
              {
                if(metapos>=metalen)
                {
                  if(verifyMeta(msgbuf, metalen))
                  {
                    return new String(msgbuf, 4, metalen-8, sage.Sage.I18N_CHARSET);
                  }
                  else
                  {
                    cur=0xFFFFFFFF;
                    continue;
                  }
                }
              }
            }

            if(type==2) ra.skip(4);
          }
          else
          {
            insync=false;
          }
        }
        return metadataStr;
      }
      return null;
    }
    finally
    {
      if (ra != null)
        ra.close();
    }
  }


  public static void main(String[] args)
  {
    if(args.length<3)
    {
      System.out.println("Usage MpegMetadata action file.mpg mode [metadata]");
      System.out.println("action 0 insert, action 1 get");
      return;
    }
    java.io.File f = new java.io.File(args[1]);
    if(new Integer(args[0]).intValue()==0)
    {
      if(args.length<4)
      {
        System.out.println("missing metadata");
        return;
      }
      try
      {
        addFileMetadata(f, new Integer(args[2]).intValue(), args[3], 0);
      }
      catch(Exception e)
      {
        System.out.println(e);
      }
    }
    else
    {
      try
      {
        System.out.println("metadata: "+
            getFileMetadata(f, new Integer(args[2]).intValue()));
      }
      catch(Exception e)
      {
        System.out.println(e);
      }
    }
  }
}