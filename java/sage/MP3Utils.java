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

import java.io.*;

public class MP3Utils {

  // Constants for MP3 calculations/array indexes

  // MPEG version
  public static final int MPEG2_LSF = 0;
  public static final int MPEG25_LSF = 2;
  public static final int MPEG1 = 1;

  // Stereo options
  public static final int STEREO = 0;
  public static final int JOINT_STEREO = 1;
  public static final int DUAL_CHANNEL = 2;
  public static final int SINGLE_CHANNEL = 3;

  // Sampling freq
  public static final int FOURTYFOUR_POINT_ONE = 0;
  public static final int FOURTYEIGHT = 1;
  public static final int THIRTYTWO = 2;


  // bitrates [h_version][h_layer - 1][h_bitrate_index]
  public static final int bitrates[][][] = {
    {
      { 0 /* free format */, 32000, 48000, 56000, 64000, 80000,
        96000, 112000, 128000, 144000, 160000, 176000,
        192000, 224000, 256000, 0
      },{ 0 /* free format */, 8000, 16000, 24000, 32000, 40000,
        48000, 56000, 64000, 80000, 96000, 112000, 128000,
        144000, 160000, 0
      },{ 0 /* free format */, 8000, 16000, 24000, 32000, 40000,
        48000, 56000, 64000, 80000, 96000, 112000, 128000,
        144000, 160000, 0
      }
    },{
      { 0 /* free format */, 32000, 64000, 96000, 128000,
        160000, 192000, 224000, 256000, 288000, 320000,
        352000, 384000, 416000, 448000, 0
      },{ 0 /* free format */, 32000, 48000, 56000, 64000, 80000,
        96000, 112000, 128000, 160000, 192000, 224000,
        256000, 320000, 384000, 0
      },{ 0 /* free format */, 32000, 40000, 48000, 56000, 64000,
        80000, 96000, 112000, 128000, 160000, 192000,
        224000, 256000, 320000, 0
      }
    },{// SZD: MPEG2.5
      { 0 /* free format */, 32000, 48000, 56000, 64000, 80000,
        96000, 112000, 128000, 144000, 160000, 176000,
        192000, 224000, 256000, 0
      },{ 0 /* free format */, 8000, 16000, 24000, 32000, 40000,
        48000, 56000, 64000, 80000, 96000, 112000, 128000,
        144000, 160000, 0
      },{ 0 /* free format */, 8000, 16000, 24000, 32000, 40000,
        48000, 56000, 64000, 80000, 96000, 112000, 128000,
        144000, 160000, 0
      } },
  };

  // frequencies[h_version][h_sample_frequency]
  public static final int[][] frequencies = {
    { 22050, 24000, 16000, 1 },
    { 44100, 48000, 32000, 1 },
    { 11025, 12000, 8000, 1 } };
  // number of samples per frame[layer]
  public static final long h_samples_per_frame_array[] = {
    -1, 384, 1152,	1152 };

  // get duration from mp3
  public static long getMp3Duration(File file) {
    if (file.getName().endsWith(".mp3")) {
      FileInputStream fileinputstream = null;
      try {
        fileinputstream = new FileInputStream(file);
        byte bytes[] = new byte[4096];
        int index = 0;
        fileinputstream.read(bytes);

        // Skip ID3 tag if present
        String tag = new String(bytes, 0, 3).trim();
        if ("ID3".equals(tag))
        {
          int hdrlen = ((bytes[6]&0x7F)<<21) | ((bytes[7]&0x7F)<<14) |
              ((bytes[8]&0x7F)<<7) | (bytes[9]&0x7F);
          hdrlen += 10; // for the 10 initial bytes
          if (Sage.DBG) System.out.println("got ID3 header len="+hdrlen);
          while (hdrlen > 0)
          {
            int currAmt = Math.min(bytes.length, hdrlen);
            hdrlen -= currAmt;
            index += currAmt;
            if (index >= bytes.length) {
              // There may be a larger header in here, like album art
              if (fileinputstream.read(bytes) <= 0)
                return 0;
              index = 0;
            }
          }
        }

        // scan through file to get syncword
        int headerstring = ((bytes[index] << 16) & 0x00FF0000)
            | ((bytes[index+1] << 8) & 0x0000FF00)
            | ((bytes[index+2] << 0) & 0x000000FF);
        boolean isSync = false;
        index += 2;
        do {
          headerstring <<= 8;
          index++;
          if (index >= bytes.length) {
            // There may be a larger header in here, like album art
            if (fileinputstream.read(bytes) <= 0)
              return 0;
            if (Sage.DBG) System.out.println("Couldn't find MPEG audio syncword...reading more");
            index = 0;
            //return 0L;
          }

          headerstring |= (bytes[index] & 0x000000FF);
          isSync = ((headerstring & 0xFFE00000) == 0xFFE00000);
          // MPEG 2.5
          // filter out invalid sample rate
          if (isSync)
            isSync = (((headerstring >>> 10) & 3) != 3);
          // filter out invalid layer
          if (isSync)
            isSync = (((headerstring >>> 17) & 3) != 0);
          // filter out invalid version
          if (isSync)
            isSync = (((headerstring >>> 19) & 3) != 1);
          // filter out invalid bitrate
          if (isSync)
            isSync = (((headerstring >>> 12) & 0xF) != 0) && (((headerstring >>> 12) & 0xF) != 15);
        } while (!isSync);

        // Got a header... lets have a look at it...
        int h_version = ((headerstring >>> 19) & 1);
        if (((headerstring >>> 20) & 1) == 0) // SZD: MPEG2.5 detection
          if (h_version == MPEG2_LSF)
            h_version = MPEG25_LSF;
          else {
            if (Sage.DBG) System.out.println("invalid MPEG header (version)");
            return 0L;
          }
        int h_sample_frequency;
        if ((h_sample_frequency = ((headerstring >>> 10) & 3)) == 3) {
          if (Sage.DBG) System.out.println("invalid MPEG header (sample freq)");
          return 0L;
        }
        int h_layer = 4 - (headerstring >>> 17) & 3;
        int h_bitrate_index = (headerstring >>> 12) & 0xF;
        int h_padding_bit = (headerstring >>> 9) & 1;
        int h_mode = ((headerstring >>> 6) & 3);
        int h_mode_extension = (headerstring >>> 4) & 3;
        int h_bitrate = bitrates[h_version][h_layer - 1][h_bitrate_index];

        // look at frame to see if it is a XING VBR frame

        int offset = index;
        // Compute "Xing" offset depending on MPEG version and channels.
        if (h_version == MPEG1) {
          if (h_mode == SINGLE_CHANNEL)
            offset = 21 + index - 3;
          else
            offset = 36 + index - 3;
        } else {
          if (h_mode == SINGLE_CHANNEL)
            offset = 23 + index - 3;
          else
            offset = 21 + index - 3;
        }


        String info = "Info";
        String xing = "Xing";
        byte tmp[] = new byte[4];
        String tmpstr="";
        if ( offset+tmp.length<bytes.length) {
          System.arraycopy(bytes, offset, tmp, 0, tmp.length);
          tmpstr=new String(tmp);
        }
        boolean h_vbr=false;
        // Is "Xing" VBR header or Info CBR header ?
        if (xing.equals(tmpstr) || info.equals(tmpstr)) {
          // yes
          if (xing.equals(tmpstr) )
            h_vbr=true;

          int h_num_frames = -1;

          int length = 4;
          if (offset + 8 < bytes.length){
            // Read flags.
            byte flags[] = new byte[4];
            System.arraycopy(bytes, offset + 4, flags, 0, flags.length);
            length += flags.length;

            // Read number of frames (if available).
            if ((flags[3] & (byte) (1 << 0)) != 0) {
              System.arraycopy(bytes, offset + length, tmp, 0,
                  tmp.length);
              h_num_frames = (tmp[0] << 24) & 0xFF000000
                  | (tmp[1] << 16) & 0x00FF0000 | (tmp[2] << 8)
                  & 0x0000FF00 | tmp[3] & 0x000000FF;
              length += 4;
            }
          }
          // so do we have the actual number of frames?
          if (h_num_frames > 0) {
            // get duration from number of frames/sample rate/etc

            long ms_per_frame = h_samples_per_frame_array[h_layer]
                * 1000
                / frequencies[h_version][h_sample_frequency];
            if ((h_version == MPEG2_LSF)
                || (h_version == MPEG25_LSF))
              ms_per_frame /= 2;

            long duration = ms_per_frame * h_num_frames;
            if (Sage.DBG) System.out.println("Mpegaudio frames=" + h_num_frames
                + " xing theDur2=" + duration);
            return (duration);
          } else {
            // no num frames -- fall back to file size...
            if (Sage.DBG) System.out.println("no num frames in  MPEG Xing/Info header, using file size");
          }
        }
        // if we have got to here, either there was no Xing/Info header
        // or the header did not contain the number of frames
        // -- get duration from bitrate/size,
        // having removed whatever prefiz we have found

        // Original calculation from Sage code...
        long duration = (8L * (file.length() - offset))
            / (h_bitrate / 1000);
        if (Sage.DBG) System.out.println("Mpegaudiobitrate=" + h_bitrate
            + " cbr approx theDur2=" + duration);
        return duration;

      } catch (Exception e) {
        if (Sage.DBG) System.out.println("Failed when reading MPEG header "
            + e.toString());
      } finally {
        if (fileinputstream != null) {
          try {
            fileinputstream.close();
          } catch (Exception exception2) {
          }
          fileinputstream = null;
        }
      }
    }
    return 0L;
  }

  // Original code decompiled from Sage.a, slightly modified to return values
  public static long getOrigMp3Duration(File file) {
    if (file.getName().endsWith(".mp3")) {
      long l1 = 0L;
      FileInputStream fileinputstream = null;
      try {
        fileinputstream = new FileInputStream(file);
        byte abyte0[] = new byte[4096];
        fileinputstream.read(abyte0);
        long l3 = getMp3Bitrate(abyte0, 0);
        if (l3 > 0L) {
          l1 = (8L * (file.length() - 128L)) / l3;
          if (Sage.DBG) System.out.println("Mpegaudiobitrate=" + l3 + " theDur2="
              + l1);
        }
      } catch (Exception exception) {
      } finally {
        if (fileinputstream != null) {
          try {
            fileinputstream.close();
          } catch (Exception exception2) {
          }
          fileinputstream = null;
        }
      }
      return l1;
    }
    return 0L;
  }

  // decompiled from Sage.a to get an mp3 bitrate from a header...
  private static long getMp3Bitrate(byte abyte0[], int k) {
    // look for an MPEG audio frame
    for (; k + 1 < abyte0.length
        && ((abyte0[k] & 0xff) != 255 || (abyte0[k + 1] & 0xf0) != 240); k++)
      ;
    if ((abyte0[k] & 0xff) != 255 || (abyte0[k + 1] & 0xf0) != 240
        || abyte0.length - k < 3) {

      if (Sage.DBG) System.out.println("Couldn't find MPEG audio syncword");
      return 0L;
    }

    byte byte0 = ((byte) ((abyte0[k + 1] & 8) != 8 ? 2 : 1));
    int j1 = (abyte0[k + 1] & 6) >> 1;
    if (j1 == 3)
      j1 = 1;
    else if (j1 != 2)
      j1 = 3;
    int k1 = (abyte0[k + 2] & 0xf0) >> 4;
                  int ai[][] = { { 0, 0, 0, 0, 0, 0 }, { 32, 32, 32, 32, 32, 8 },
                      { 64, 48, 40, 64, 48, 16 }, { 96, 56, 48, 96, 56, 24 },
                      { 128, 64, 56, 128, 64, 32 }, { 160, 80, 64, 160, 80, 64 },
                      { 192, 96, 80, 192, 96, 80 }, { 224, 112, 96, 224, 112, 56 },
                      { 256, 128, 112, 256, 128, 64 },
                      { 288, 160, 128, 288, 160, 128 },
                      { 320, 192, 160, 320, 192, 160 },
                      { 352, 224, 192, 352, 224, 112 },
                      { 384, 256, 224, 384, 256, 128 },
                      { 416, 320, 256, 416, 320, 256 },
                      { 448, 384, 320, 448, 384, 320 }, { 0, 0, 0, 0, 0, 0 } };
                  int l1 = ((byte0 - 1) * 3 + j1) - 1;
                  if (k1 < ai.length && k1 >= 0 && l1 >= 0 && l1 < ai[k1].length)
                    return (long) ai[k1][l1];
                  else
                    return 0L;
  }

}